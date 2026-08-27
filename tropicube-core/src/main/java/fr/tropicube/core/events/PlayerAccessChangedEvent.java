package fr.tropicube.core.events;

import fr.tropicube.docker.model.PlayerAccessProfile;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Main-thread notification emitted after the derived Bukkit permissions were rebuilt. */
public final class PlayerAccessChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final PlayerAccessProfile profile;

    public PlayerAccessChangedEvent(Player player, PlayerAccessProfile profile) {
        this.player = player;
        this.profile = profile;
    }

    public Player player() { return player; }
    public PlayerAccessProfile profile() { return profile; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
