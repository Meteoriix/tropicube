package fr.tropicube.fallenkingdoms.listener;

import fr.tropicube.fallenkingdoms.game.GameSession;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;

/** Thin Paper listener delegating game rules to the session. */
public final class GameListener implements Listener {
    private final GameSession session;
    public GameListener(GameSession session) { this.session = session; }
    @EventHandler public void heartDamage(EntityDamageByEntityEvent event) { session.damageHeart(event); }
    @EventHandler public void death(PlayerDeathEvent event) { session.playerDied(event.getPlayer(), event.getPlayer().getKiller()); }
    @EventHandler public void join(PlayerJoinEvent event) { session.join(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) { session.quit(event.getPlayer()); }
    @EventHandler public void respawn(PlayerRespawnEvent event) { session.respawn(event.getPlayer()); }
    @EventHandler public void playerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = event.getDamager() instanceof Player direct ? direct
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker == null) return;
        if (!session.allowsPvp(attacker, victim)) event.setCancelled(true);
    }
}
