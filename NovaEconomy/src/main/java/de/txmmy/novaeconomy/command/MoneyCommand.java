package de.txmmy.novaeconomy.command;

import de.txmmy.novaeconomy.database.DataAccessException;
import de.txmmy.novaeconomy.economy.EconomyService;
import de.txmmy.novaeconomy.economy.TransferResult;
import de.txmmy.novaeconomy.util.MessageService;
import de.txmmy.novaeconomy.util.Money;
import de.txmmy.novaeconomy.util.MoneyFormatter;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MoneyCommand implements TabExecutor {
    private final JavaPlugin plugin;
    private final EconomyService economyService;
    private final MessageService messages;
    private final MoneyFormatter moneyFormatter;

    public MoneyCommand(JavaPlugin plugin, EconomyService economyService,
                        MessageService messages, MoneyFormatter moneyFormatter) {
        this.plugin = plugin;
        this.economyService = economyService;
        this.messages = messages;
        this.moneyFormatter = moneyFormatter;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        try {
            if (args.length == 0 || args[0].equalsIgnoreCase("balance")) {
                return handleBalance(sender, args);
            }
            if (args[0].equalsIgnoreCase("pay")) {
                return handlePay(sender, args);
            }
            if (args[0].equalsIgnoreCase("admin")) {
                return handleAdmin(sender, args);
            }
            messages.send(sender, "money.usage");
            return true;
        } catch (DataAccessException exception) {
            plugin.getLogger().severe("Datenbankfehler in /money: " + exception.getMessage());
            messages.send(sender, "database-error");
            return true;
        } catch (ArithmeticException exception) {
            messages.send(sender, "invalid-money", Map.of("value", String.join(" ", args)));
            return true;
        }
    }

    private boolean handleBalance(CommandSender sender, String[] args) {
        if (!sender.hasPermission("novaeconomy.money.balance")) {
            messages.send(sender, "no-permission");
            return true;
        }
        OfflinePlayer target;
        if (args.length <= 1) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "money.usage");
                return true;
            }
            target = player;
        } else if (args.length == 2) {
            if (!sender.hasPermission("novaeconomy.money.balance.others")) {
                messages.send(sender, "no-permission");
                return true;
            }
            target = resolvePlayer(args[1]);
            if (target == null) {
                messages.send(sender, "money.player-not-found", Map.of("player", args[1]));
                return true;
            }
        } else {
            messages.send(sender, "money.usage");
            return true;
        }

        String name = safeName(target);
        long balance = economyService.getBalance(target.getUniqueId(), name);
        messages.send(sender, "money.balance", Map.of(
                "player", name,
                "balance", moneyFormatter.format(balance)
        ));
        return true;
    }

    private boolean handlePay(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }
        if (!sender.hasPermission("novaeconomy.money.pay")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length != 3) {
            messages.send(sender, "money.usage");
            return true;
        }
        OfflinePlayer target = resolvePlayer(args[1]);
        if (target == null) {
            messages.send(sender, "money.player-not-found", Map.of("player", args[1]));
            return true;
        }
        long amount;
        try {
            amount = Money.parseMinor(args[2]);
        } catch (NumberFormatException | ArithmeticException exception) {
            messages.send(sender, "invalid-money", Map.of("value", args[2]));
            return true;
        }
        if (amount <= 0) {
            messages.send(sender, "invalid-money", Map.of("value", args[2]));
            return true;
        }

        String targetName = safeName(target);
        TransferResult result = economyService.transfer(
                player.getUniqueId(), player.getName(), target.getUniqueId(), targetName, amount);
        switch (result.status()) {
            case SUCCESS -> {
                messages.send(player, "money.pay-success", Map.of(
                        "player", targetName,
                        "amount", moneyFormatter.format(amount)
                ));
                Player onlineTarget = target.getPlayer();
                if (onlineTarget != null) {
                    messages.send(onlineTarget, "money.pay-received", Map.of(
                            "player", player.getName(),
                            "amount", moneyFormatter.format(amount)
                    ));
                }
            }
            case INSUFFICIENT_FUNDS -> messages.send(player, "money.insufficient-funds");
            case SAME_ACCOUNT -> messages.send(player, "money.pay-self");
            case INVALID_AMOUNT -> messages.send(player, "invalid-money", Map.of("value", args[2]));
        }
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("novaeconomy.money.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        if (args.length != 4) {
            messages.send(sender, "money.usage");
            return true;
        }
        String operation = args[1].toLowerCase(Locale.ROOT);
        OfflinePlayer target = resolvePlayer(args[2]);
        if (target == null) {
            messages.send(sender, "money.player-not-found", Map.of("player", args[2]));
            return true;
        }
        long amount;
        try {
            amount = Money.parseMinor(args[3]);
        } catch (NumberFormatException | ArithmeticException exception) {
            messages.send(sender, "invalid-money", Map.of("value", args[3]));
            return true;
        }
        if (amount < 0) {
            messages.send(sender, "invalid-money", Map.of("value", args[3]));
            return true;
        }

        String targetName = safeName(target);
        long newBalance;
        try {
            newBalance = switch (operation) {
                case "set" -> economyService.setBalance(target.getUniqueId(), targetName, amount, "ADMIN_SET");
                case "add" -> economyService.addBalance(target.getUniqueId(), targetName, amount, "ADMIN_ADD");
                case "take" -> economyService.addBalance(target.getUniqueId(), targetName, -amount, "ADMIN_TAKE");
                default -> {
                    messages.send(sender, "money.usage");
                    yield Long.MIN_VALUE;
                }
            };
        } catch (IllegalArgumentException exception) {
            messages.send(sender, "money.insufficient-funds");
            return true;
        }
        if (newBalance == Long.MIN_VALUE) {
            return true;
        }
        messages.send(sender, "money.admin-success", Map.of(
                "player", targetName,
                "balance", moneyFormatter.format(newBalance)
        ));
        return true;
    }

    private OfflinePlayer resolvePlayer(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        return Bukkit.getOfflinePlayerIfCached(name);
    }

    private String safeName(OfflinePlayer player) {
        return player.getName() == null ? player.getUniqueId().toString() : player.getName();
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return List.of("balance", "pay", "admin").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
            return List.of("set", "add", "take").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
