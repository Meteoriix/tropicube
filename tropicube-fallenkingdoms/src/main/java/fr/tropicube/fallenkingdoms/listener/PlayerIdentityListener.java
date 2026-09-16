package fr.tropicube.fallenkingdoms.listener;

import fr.tropicube.core.identity.PlayerDisplayIdentityChangedEvent;
import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Refreshes the Fallen Kingdoms HUD after Core has applied a visible identity. */
public final class PlayerIdentityListener implements Listener {
    private final TropicubeFallenKingdoms plugin;

    public PlayerIdentityListener(TropicubeFallenKingdoms plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onIdentityChanged(PlayerDisplayIdentityChangedEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> plugin.refreshPlayerIdentity(event.playerId()), 2L);
    }
}
