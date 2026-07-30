package de.txmmy.novaeconomy;

import de.txmmy.novaeconomy.command.MoneyCommand;
import de.txmmy.novaeconomy.command.OrderCommand;
import de.txmmy.novaeconomy.database.DataAccessException;
import de.txmmy.novaeconomy.database.Database;
import de.txmmy.novaeconomy.economy.EconomyService;
import de.txmmy.novaeconomy.gui.OrderGuiListener;
import de.txmmy.novaeconomy.gui.OrderGuiService;
import de.txmmy.novaeconomy.listener.PlayerAccountListener;
import de.txmmy.novaeconomy.order.OrderService;
import de.txmmy.novaeconomy.util.MessageService;
import de.txmmy.novaeconomy.util.Money;
import de.txmmy.novaeconomy.util.MoneyFormatter;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;

public final class NovaEconomyPlugin extends JavaPlugin {
    private Database database;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveBundledResource("messages.yml");

        long startingBalance;
        try {
            startingBalance = Money.parseMinor(getConfig().getString("economy.starting-balance", "1000.00"));
        } catch (RuntimeException exception) {
            getLogger().severe("Ungültiger Wert economy.starting-balance in config.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (startingBalance < 0) {
            getLogger().severe("economy.starting-balance darf nicht negativ sein.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String databaseFile = getConfig().getString("storage.sqlite-file", "novaeconomy.db");
        int busyTimeout = Math.max(0, getConfig().getInt("storage.busy-timeout-ms", 5000));
        database = new Database(this, databaseFile, busyTimeout);
        try {
            database.open();
        } catch (DataAccessException exception) {
            getLogger().severe("NovaEconomy konnte nicht gestartet werden: " + exception.getMessage());
            exception.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        MessageService messages = new MessageService(this);
        MoneyFormatter moneyFormatter = new MoneyFormatter(
                getConfig().getString("economy.locale", "de-DE"),
                getConfig().getString("economy.currency-symbol", "€")
        );
        EconomyService economyService = new EconomyService(database, startingBalance);
        OrderService orderService = new OrderService(
                database,
                economyService,
                Math.max(1, getConfig().getInt("orders.max-active-orders-per-player", 25))
        );
        OrderGuiService guiService = new OrderGuiService(
                orderService,
                messages,
                moneyFormatter,
                getConfig().getInt("orders.gui-page-size", 45),
                Math.max(1, getConfig().getInt("orders.normal-click-delivery-limit", 64))
        );

        OrderCommand orderCommand = new OrderCommand(
                this,
                orderService,
                guiService,
                messages,
                moneyFormatter,
                Math.max(1, getConfig().getInt("orders.max-amount-per-order", 10_000_000))
        );
        MoneyCommand moneyCommand = new MoneyCommand(this, economyService, messages, moneyFormatter);

        registerCommand("order", orderCommand);
        registerCommand("money", moneyCommand);
        getServer().getPluginManager().registerEvents(
                new OrderGuiListener(this, orderService, guiService, messages, moneyFormatter), this);
        getServer().getPluginManager().registerEvents(
                new PlayerAccountListener(this, economyService), this);

        getLogger().info("NovaEconomy v" + getPluginMeta().getVersion()
                + " aktiviert. Eigenständiges Economy-System, SQLite: " + databaseFile);
    }

    @Override
    public void onDisable() {
        if (database != null) {
            database.close();
        }
    }

    private void registerCommand(String name, org.bukkit.command.TabExecutor executor) {
        PluginCommand command = Objects.requireNonNull(getCommand(name),
                "Befehl fehlt in plugin.yml: " + name);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void saveBundledResource(String resourceName) {
        File target = new File(getDataFolder(), resourceName);
        if (!target.exists()) {
            saveResource(resourceName, false);
        }
    }
}
