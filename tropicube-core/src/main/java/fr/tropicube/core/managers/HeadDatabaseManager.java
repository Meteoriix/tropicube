package fr.tropicube.core.managers;

import me.arcaniax.hdb.api.DatabaseLoadEvent;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Suit le chargement de HeadDatabase et expose son API aux plugins Paper Tropicube.
 * L'API reste {@code null} tant que {@link DatabaseLoadEvent} n'a pas été reçu ;
 * les appelants doivent donc toujours prévoir une icône de repli.
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
