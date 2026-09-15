package fr.tropicube.fallenkingdoms.event;

import fr.tropicube.fallenkingdoms.game.KingdomId;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** Published once when a kingdom loses its final survivor. */
public final class KingdomEliminatedEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();private final UUID sessionId;private final KingdomId kingdom;
    public KingdomEliminatedEvent(UUID sessionId,KingdomId kingdom){this.sessionId=sessionId;this.kingdom=kingdom;}
    public UUID sessionId(){return sessionId;}public KingdomId kingdom(){return kingdom;}
    @Override public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
