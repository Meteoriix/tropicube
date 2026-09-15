package fr.tropicube.fallenkingdoms.event;

import fr.tropicube.fallenkingdoms.game.KingdomId;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.UUID;

/** Cancellable heart damage request before validated damage is applied. */
public final class KingdomHeartDamageEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final UUID sessionId; private final KingdomId kingdom; private final Player attacker;
    private double damage; private boolean cancelled;
    public KingdomHeartDamageEvent(UUID sessionId, KingdomId kingdom, Player attacker, double damage) {
        this.sessionId = sessionId; this.kingdom = kingdom; this.attacker = attacker; setDamage(damage);
    }
    public UUID sessionId() { return sessionId; } public KingdomId kingdom() { return kingdom; }
    public Player attacker() { return attacker; } public double damage() { return damage; }
    public void setDamage(double damage) { if (damage < 0 || !Double.isFinite(damage)) throw new IllegalArgumentException("Dégâts invalides"); this.damage = damage; }
    @Override public boolean isCancelled() { return cancelled; } @Override public void setCancelled(boolean value) { cancelled = value; }
    @Override public HandlerList getHandlers() { return HANDLERS; } public static HandlerList getHandlerList() { return HANDLERS; }
}
