package fr.tropicube.fallenkingdoms.event;

import fr.tropicube.fallenkingdoms.game.GameResult;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Published after the immutable terminal result has been fixed. */
public final class FallenKingdomsGameEndEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList();private final GameResult result;
    public FallenKingdomsGameEndEvent(GameResult result){this.result=result;}public GameResult result(){return result;}
    @Override public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
