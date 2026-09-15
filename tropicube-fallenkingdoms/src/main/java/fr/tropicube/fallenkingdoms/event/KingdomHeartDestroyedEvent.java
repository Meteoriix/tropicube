package fr.tropicube.fallenkingdoms.event;

import fr.tropicube.fallenkingdoms.game.KingdomId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** Published exactly once when a heart is destroyed. */
public final class KingdomHeartDestroyedEvent extends Event {
    public enum Cause { PLAYER, FORCED_SUDDEN_DEATH }
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID sessionId; private final KingdomId kingdom; private final Cause cause;
    public KingdomHeartDestroyedEvent(UUID sessionId, KingdomId kingdom, Cause cause) { this.sessionId=sessionId;this.kingdom=kingdom;this.cause=cause; }
    public UUID sessionId(){return sessionId;} public KingdomId kingdom(){return kingdom;} public Cause cause(){return cause;}
    @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;}
}
