package de.txmmy.novaeconomy.gui;

import de.txmmy.novaeconomy.order.OrderRecord;
import de.txmmy.novaeconomy.order.OrderService;
import de.txmmy.novaeconomy.util.InventoryUtil;
import de.txmmy.novaeconomy.util.MessageService;
import de.txmmy.novaeconomy.util.Money;
import de.txmmy.novaeconomy.util.MoneyFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OrderGuiService {
    public static final int PREVIOUS_SLOT = 45;
    public static final int INFO_SLOT = 49;
    public static final int NEXT_SLOT = 53;

    private final OrderService orderService;
    private final MessageService messages;
    private final MoneyFormatter moneyFormatter;
    private final int pageSize;
    private final int clickLimit;

    public OrderGuiService(OrderService orderService, MessageService messages,
                           MoneyFormatter moneyFormatter, int pageSize, int clickLimit) {
        this.orderService = orderService;
        this.messages = messages;
        this.moneyFormatter = moneyFormatter;
        this.pageSize = Math.min(45, Math.max(1, pageSize));
        this.clickLimit = clickLimit;
    }

    public void open(Player player, Material filter, int requestedPage) {
        List<OrderRecord> fulfillable = orderService.findActiveOrders(filter).stream()
                .filter(order -> !order.ownerUuid().equals(player.getUniqueId()))
                .filter(order -> InventoryUtil.count(player.getInventory(), order.material()) > 0)
                .toList();

        int totalPages = Math.max(1, (int) Math.ceil(fulfillable.size() / (double) pageSize));
        int page = Math.max(0, Math.min(requestedPage, totalPages - 1));
        OrderBrowserHolder holder = new OrderBrowserHolder(filter, page, totalPages);
        Component title = messages.component("gui.title", Map.of(), false);
        Inventory inventory = Bukkit.createInventory(holder, 54, title);
        holder.setInventory(inventory);

        int from = page * pageSize;
        int to = Math.min(fulfillable.size(), from + pageSize);
        for (int listIndex = from; listIndex < to; listIndex++) {
            int slot = listIndex - from;
            OrderRecord order = fulfillable.get(listIndex);
            holder.putOrder(slot, order);
            inventory.setItem(slot, createOrderItem(player, order));
        }

        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, navigationItem(Material.ARROW, "gui.previous", Map.of()));
        }
        if (page + 1 < totalPages) {
            inventory.setItem(NEXT_SLOT, navigationItem(Material.ARROW, "gui.next", Map.of()));
        }
        Map<String, String> infoPlaceholders = new HashMap<>();
        infoPlaceholders.put("page", Integer.toString(page + 1));
        infoPlaceholders.put("pages", Integer.toString(totalPages));
        infoPlaceholders.put("filter", filter == null ? "Alle" : prettyMaterial(filter));
        infoPlaceholders.put("count", Integer.toString(fulfillable.size()));
        inventory.setItem(INFO_SLOT, navigationItem(Material.PAPER, "gui.info", infoPlaceholders));

        player.openInventory(inventory);
        if (fulfillable.isEmpty()) {
            messages.send(player, "order.search-empty");
        }
    }

    private ItemStack createOrderItem(Player player, OrderRecord order) {
        int available = InventoryUtil.count(player.getInventory(), order.material());
        int deliverable = Math.min(available, order.remainingAmount());
        long possiblePayout = Money.multiplyExact(order.unitPriceMinor(), deliverable);
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("id", Long.toString(order.id()));
        placeholders.put("material", prettyMaterial(order.material()));
        placeholders.put("owner", order.ownerName());
        placeholders.put("remaining", Integer.toString(order.remainingAmount()));
        placeholders.put("requested", Integer.toString(order.requestedAmount()));
        placeholders.put("unit_price", moneyFormatter.format(order.unitPriceMinor()));
        placeholders.put("possible_payout", moneyFormatter.format(possiblePayout));
        placeholders.put("click_limit", Integer.toString(clickLimit));

        ItemStack item = new ItemStack(order.material(), Math.max(1, Math.min(order.material().getMaxStackSize(), order.remainingAmount())));
        ItemMeta meta = item.getItemMeta();
        meta.customName(messages.component("gui.order-name", placeholders, false));
        meta.lore(messages.componentList("gui.order-lore", placeholders));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack navigationItem(Material material, String messagePath, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.customName(messages.component(messagePath, placeholders, false));
        if (messagePath.equals("gui.info")) {
            meta.lore(messages.componentList("gui.info-lore", placeholders));
        }
        item.setItemMeta(meta);
        return item;
    }

    public int clickLimit() {
        return clickLimit;
    }

    private String prettyMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
