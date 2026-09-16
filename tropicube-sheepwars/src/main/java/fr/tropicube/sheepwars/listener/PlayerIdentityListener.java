package fr.tropicube.sheepwars.listener;

import fr.tropicube.core.identity.PlayerDisplayIdentityChangedEvent;
import fr.tropicube.sheepwars.TropicubeSheepwars;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Rebuilds game-owned name rendering after Core has changed a player profile. */
public final class PlayerIdentityListener implements Listener {
    private final TropicubeSheepwars plugin;

    public PlayerIdentityListener(TropicubeSheepwars plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onIdentityChanged(PlayerDisplayIdentityChangedEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.getScoreboardManager().refreshIdentity(event.playerId()), 2L);
    }
}
