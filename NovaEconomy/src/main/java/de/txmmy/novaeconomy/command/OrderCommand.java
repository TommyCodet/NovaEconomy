package de.txmmy.novaeconomy.command;

import de.txmmy.novaeconomy.database.DataAccessException;
import de.txmmy.novaeconomy.gui.OrderGuiService;
import de.txmmy.novaeconomy.order.CancelOrderResult;
import de.txmmy.novaeconomy.order.CollectResult;
import de.txmmy.novaeconomy.order.CollectedItems;
import de.txmmy.novaeconomy.order.CreateOrderResult;
import de.txmmy.novaeconomy.order.OrderService;
import de.txmmy.novaeconomy.util.InventoryUtil;
import de.txmmy.novaeconomy.util.MaterialParser;
import de.txmmy.novaeconomy.util.MessageService;
import de.txmmy.novaeconomy.util.Money;
import de.txmmy.novaeconomy.util.MoneyFormatter;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class OrderCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final OrderService orderService;
    private final OrderGuiService guiService;
    private final MessageService messages;
    private final MoneyFormatter moneyFormatter;
    private final int maxOrderAmount;

    public OrderCommand(JavaPlugin plugin, OrderService orderService, OrderGuiService guiService,
                        MessageService messages, MoneyFormatter moneyFormatter, int maxOrderAmount) {
        this.plugin = plugin;
        this.orderService = orderService;
        this.guiService = guiService;
        this.messages = messages;
        this.moneyFormatter = moneyFormatter;
        this.maxOrderAmount = maxOrderAmount;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (args.length == 0) {
            messages.send(player, "order.usage");
            return true;
        }

        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "search", "suche" -> handleSearch(player, args);
                case "collect", "abholen" -> handleCollect(player, args);
                case "cancel", "abbrechen" -> handleCancel(player, args);
                default -> handleCreate(player, args);
            };
        } catch (DataAccessException exception) {
            plugin.getLogger().severe("Datenbankfehler in /order: " + exception.getMessage());
            messages.send(player, "database-error");
            return true;
        } catch (ArithmeticException exception) {
            messages.send(player, "invalid-money", Map.of("value", String.join(" ", args)));
            return true;
        }
    }

    private boolean handleCreate(Player player, String[] args) {
        if (!player.hasPermission("novaeconomy.order.create")) {
            messages.send(player, "no-permission");
            return true;
        }
        if (args.length != 3) {
            messages.send(player, "order.usage");
            return true;
        }

        Material material = MaterialParser.parseBlock(args[0]).orElse(null);
        if (material == null) {
            messages.send(player, "invalid-material", Map.of("value", args[0]));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException exception) {
            messages.send(player, "invalid-number", Map.of("value", args[1]));
            return true;
        }
        if (amount < 1 || amount > maxOrderAmount) {
            messages.send(player, "order.invalid-amount", Map.of("max", Integer.toString(maxOrderAmount)));
            return true;
        }

        long unitPriceMinor;
        try {
            unitPriceMinor = Money.parseMinor(args[2]);
        } catch (NumberFormatException | ArithmeticException exception) {
            messages.send(player, "invalid-money", Map.of("value", args[2]));
            return true;
        }
        if (unitPriceMinor <= 0) {
            messages.send(player, "order.invalid-price");
            return true;
        }

        CreateOrderResult result = orderService.createOrder(
                player.getUniqueId(), player.getName(), material, amount, unitPriceMinor);
        switch (result.status()) {
            case SUCCESS -> {
                Map<String, String> placeholders = new HashMap<>();
                placeholders.put("id", Long.toString(result.orderId()));
                placeholders.put("amount", Integer.toString(amount));
                placeholders.put("material", material.name().toLowerCase(Locale.ROOT));
                placeholders.put("unit_price", moneyFormatter.format(unitPriceMinor));
                placeholders.put("total", moneyFormatter.format(result.totalMinor()));
                messages.send(player, "order.created", placeholders);
            }
            case INSUFFICIENT_FUNDS -> messages.send(player, "order.insufficient-funds", Map.of(
                    "required", moneyFormatter.format(result.totalMinor()),
                    "balance", moneyFormatter.format(result.balanceMinor())
            ));
            case ACTIVE_LIMIT_REACHED -> messages.send(player, "order.limit-reached");
        }
        return true;
    }

    private boolean handleSearch(Player player, String[] args) {
        if (!player.hasPermission("novaeconomy.order.search")) {
            messages.send(player, "no-permission");
            return true;
        }
        if (args.length > 2) {
            messages.send(player, "order.usage");
            return true;
        }
        Material filter = null;
        if (args.length == 2) {
            filter = MaterialParser.parseBlock(args[1]).orElse(null);
            if (filter == null) {
                messages.send(player, "invalid-material", Map.of("value", args[1]));
                return true;
            }
        }
        guiService.open(player, filter, 0);
        return true;
    }

    private boolean handleCollect(Player player, String[] args) {
        if (!player.hasPermission("novaeconomy.order.collect")) {
            messages.send(player, "no-permission");
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "order.usage");
            return true;
        }
        CollectResult result = orderService.collect(player.getUniqueId());
        if (result.items().isEmpty()) {
            messages.send(player, "order.nothing-to-collect");
            return true;
        }
        for (CollectedItems collected : result.items()) {
            InventoryUtil.giveOrDrop(player, collected.material(), collected.amount());
        }
        messages.send(player, "order.collected", Map.of(
                "items", Integer.toString(result.totalAmount()),
                "orders", Integer.toString(result.orderCount())
        ));
        return true;
    }

    private boolean handleCancel(Player player, String[] args) {
        if (!player.hasPermission("novaeconomy.order.cancel")) {
            messages.send(player, "no-permission");
            return true;
        }
        if (args.length != 2) {
            messages.send(player, "order.usage");
            return true;
        }
        long orderId;
        try {
            orderId = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            messages.send(player, "invalid-number", Map.of("value", args[1]));
            return true;
        }
        CancelOrderResult result = orderService.cancel(orderId, player.getUniqueId(), player.getName());
        switch (result.status()) {
            case SUCCESS -> messages.send(player, "order.cancelled", Map.of(
                    "id", Long.toString(orderId),
                    "refund", moneyFormatter.format(result.refundMinor())
            ));
            case NOT_FOUND -> messages.send(player, "order.not-found", Map.of("id", Long.toString(orderId)));
            case NOT_OWNER -> messages.send(player, "order.not-owner");
            case NOT_ACTIVE -> messages.send(player, "order.not-active");
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> suggestions = new ArrayList<>(List.of("search", "collect", "cancel"));
            for (Material material : Material.values()) {
                if (material.isBlock() && material.isItem() && !material.isAir()) {
                    suggestions.add(material.name().toLowerCase(Locale.ROOT));
                }
            }
            return suggestions.stream().filter(value -> value.startsWith(prefix)).limit(100).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("search")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return java.util.Arrays.stream(Material.values())
                    .filter(Material::isBlock)
                    .filter(Material::isItem)
                    .filter(material -> !material.isAir())
                    .map(material -> material.name().toLowerCase(Locale.ROOT))
                    .filter(value -> value.startsWith(prefix))
                    .limit(100)
                    .toList();
        }
        return List.of();
    }
}
