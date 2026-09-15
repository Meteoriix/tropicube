package fr.tropicube.fallenkingdoms.event;

import fr.tropicube.fallenkingdoms.game.GameState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** Published after a session phase changes on the Paper thread. */
public final class FallenKingdomsPhaseChangeEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID sessionId; private final GameState previous; private final GameState current;
    public FallenKingdomsPhaseChangeEvent(UUID sessionId, GameState previous, GameState current) {
        this.sessionId = sessionId; this.previous = previous; this.current = current;
    }
    public UUID sessionId() { return sessionId; } public GameState previous() { return previous; }
    public GameState current() { return current; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
