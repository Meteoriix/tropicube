package fr.tropicube.core.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerRecipeDiscoverEvent;

/** Hides Paper notifications that the network replaces with its own interface. */
public class SuppressNotificationsListener implements Listener {

    @EventHandler
    public void onAdvancement(PlayerAdvancementDoneEvent e) {
        e.message(null);
    }

    @EventHandler
    public void onRecipe(PlayerRecipeDiscoverEvent e) {
        e.setCancelled(true);
    }
}
