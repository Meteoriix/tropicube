package fr.tropicube.core.identity;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;
import java.util.UUID;

/**
 * Signals that Core has finished applying a player's visible identity on the
 * Paper thread. Consumers may rebuild tablists and scoreboard teams without
 * racing the Redis request that initiated the change.
 */
public final class PlayerDisplayIdentityChangedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final Change change;

    public PlayerDisplayIdentityChangedEvent(UUID playerId, Change change) {
        this.playerId = Objects.requireNonNull(playerId, "playerId");
        this.change = Objects.requireNonNull(change, "change");
    }

    public UUID playerId() {
        return playerId;
    }

    public Change change() {
        return change;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /** Describes the identity that is visible after the event. */
    public enum Change {
        NICK_APPLIED,
        NICK_REMOVED
    }
}
