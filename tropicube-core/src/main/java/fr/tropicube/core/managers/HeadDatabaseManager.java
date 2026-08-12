package fr.tropicube.core.managers;

import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Tracks HeadDatabase loading and exposes its API to Tropicube Paper plugins.
 * The API remains {@code null} until {@link DatabaseLoadEvent} has been received;
 * callers must therefore always provide a fallback icon.
 */
public class HeadDatabaseManager implements Listener {

    private HeadDatabaseAPI headDatabaseAPI;

    @EventHandler
    public void onDatabaseLoad(DatabaseLoadEvent event) {
        headDatabaseAPI = new HeadDatabaseAPI();
    }

    public HeadDatabaseAPI getHeadDatabaseAPI() {
        return headDatabaseAPI;
    }
}
