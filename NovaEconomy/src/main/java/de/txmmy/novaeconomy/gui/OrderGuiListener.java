package de.txmmy.novaeconomy.gui;

import de.txmmy.novaeconomy.database.DataAccessException;
import de.txmmy.novaeconomy.order.FulfillmentResult;
import de.txmmy.novaeconomy.order.OrderRecord;
import de.txmmy.novaeconomy.order.OrderService;
import de.txmmy.novaeconomy.util.InventoryUtil;
import de.txmmy.novaeconomy.util.MessageService;
import de.txmmy.novaeconomy.util.MoneyFormatter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public final class OrderGuiListener implements Listener {
    private final JavaPlugin plugin;
    private final OrderService orderService;
    private final OrderGuiService guiService;
    private final MessageService messages;
    private final MoneyFormatter moneyFormatter;

    public OrderGuiListener(JavaPlugin plugin, OrderService orderService, OrderGuiService guiService,
                            MessageService messages, MoneyFormatter moneyFormatter) {
        this.plugin = plugin;
        this.orderService = orderService;
        this.guiService = guiService;
        this.messages = messages;
        this.moneyFormatter = moneyFormatter;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof OrderBrowserHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        if (rawSlot == OrderGuiService.PREVIOUS_SLOT && holder.page() > 0) {
            reopenNextTick(player, holder.filter(), holder.page() - 1);
            return;
        }
        if (rawSlot == OrderGuiService.NEXT_SLOT && holder.page() + 1 < holder.totalPages()) {
            reopenNextTick(player, holder.filter(), holder.page() + 1);
            return;
        }

        OrderRecord order = holder.orderAt(rawSlot);
        if (order == null) {
            return;
        }
        int available = InventoryUtil.count(player.getInventory(), order.material());
        int requested;
        if (event.isShiftClick()) {
            requested = Math.min(available, order.remainingAmount());
        } else if (event.isRightClick()) {
            requested = Math.min(1, available);
        } else {
            requested = Math.min(Math.min(guiService.clickLimit(), available), order.remainingAmount());
        }
        if (requested <= 0) {
            reopenNextTick(player, holder.filter(), holder.page());
            return;
        }

        int removed = InventoryUtil.remove(player.getInventory(), order.material(), requested);
        if (removed <= 0) {
            reopenNextTick(player, holder.filter(), holder.page());
            return;
        }

        try {
            FulfillmentResult result = orderService.fulfill(order.id(), player.getUniqueId(), player.getName(), removed);
            if (result.status() != FulfillmentResult.Status.SUCCESS) {
                InventoryUtil.giveOrDrop(player, order.material(), removed);
                switch (result.status()) {
                    case OWN_ORDER -> messages.send(player, "order.own-order");
                    case NOT_ACTIVE, NOTHING_NEEDED -> messages.send(player, "order.not-active");
                    case NOT_FOUND -> messages.send(player, "order.not-found", Map.of("id", Long.toString(order.id())));
                    default -> messages.send(player, "order.delivery-failed");
                }
            } else {
                if (result.acceptedAmount() < removed) {
                    InventoryUtil.giveOrDrop(player, order.material(), removed - result.acceptedAmount());
                }
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("amount", Integer.toString(result.acceptedAmount()));
                placeholders.put("material", order.material().name().toLowerCase());
                placeholders.put("payout", moneyFormatter.format(result.payoutMinor()));
                placeholders.put("id", Long.toString(result.orderId()));
                messages.send(player, "order.delivery-success", placeholders);
            }
        } catch (DataAccessException | ArithmeticException exception) {
            InventoryUtil.giveOrDrop(player, order.material(), removed);
            plugin.getLogger().severe("Lieferung für Auftrag #" + order.id() + " fehlgeschlagen: " + exception.getMessage());
            messages.send(player, "database-error");
        }
        reopenNextTick(player, holder.filter(), holder.page());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof OrderBrowserHolder) {
            event.setCancelled(true);
        }
    }

    private void reopenNextTick(Player player, org.bukkit.Material filter, int page) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                guiService.open(player, filter, page);
            }
        });
    }
}
