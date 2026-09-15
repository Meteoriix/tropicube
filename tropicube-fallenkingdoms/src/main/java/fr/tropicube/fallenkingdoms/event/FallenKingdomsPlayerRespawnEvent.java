package fr.tropicube.fallenkingdoms.event;

import fr.tropicube.fallenkingdoms.game.KingdomId;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** Published after respawn eligibility is validated and before teleportation. */
public final class FallenKingdomsPlayerRespawnEvent extends Event {
    private static final HandlerList HANDLERS=new HandlerList(); private final UUID sessionId;private final Player player;private final KingdomId kingdom;
    public FallenKingdomsPlayerRespawnEvent(UUID sessionId,Player player,KingdomId kingdom){this.sessionId=sessionId;this.player=player;this.kingdom=kingdom;}
    public UUID sessionId(){return sessionId;}public Player player(){return player;}public KingdomId kingdom(){return kingdom;}
    @Override public HandlerList getHandlers(){return HANDLERS;}public static HandlerList getHandlerList(){return HANDLERS;}
}
