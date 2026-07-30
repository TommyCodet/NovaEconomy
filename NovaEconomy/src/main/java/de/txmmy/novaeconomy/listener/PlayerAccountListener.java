package de.txmmy.novaeconomy.listener;

import de.txmmy.novaeconomy.database.DataAccessException;
import de.txmmy.novaeconomy.economy.EconomyService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerAccountListener implements Listener {
    private final JavaPlugin plugin;
    private final EconomyService economyService;

    public PlayerAccountListener(JavaPlugin plugin, EconomyService economyService) {
        this.plugin = plugin;
        this.economyService = economyService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        try {
            economyService.ensureAccount(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        } catch (DataAccessException exception) {
            plugin.getLogger().severe("Konto für " + event.getPlayer().getName()
                    + " konnte nicht initialisiert werden: " + exception.getMessage());
        }
    }
}
