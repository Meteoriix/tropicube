package fr.tropicube.fallenkingdoms.listener;

import fr.tropicube.fallenkingdoms.game.CactusSession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Thin Paper listener delegating game rules to the session. */
public final class GameListener implements Listener {
    private final CactusSession session;
    public GameListener(CactusSession session) { this.session = session; }
    @EventHandler public void heartDamage(EntityDamageByEntityEvent event) { session.damageHeart(event); }
    @EventHandler public void death(PlayerDeathEvent event) { session.playerDied(event.getPlayer()); }
}
