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
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.Material;

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
        Player attacker = attacker(event);
        if (attacker == null) return;
        if (!session.allowsPvp(attacker, victim)) event.setCancelled(true);
        else session.applyCombatProfile(event,attacker,victim);
    }
    @EventHandler public void swapHands(PlayerSwapHandItemsEvent event){if(session.legacyCombat())event.setCancelled(true);}
    @EventHandler public void shield(PlayerInteractEvent event){if(session.legacyCombat()&&event.getItem()!=null&&event.getItem().getType()==Material.SHIELD)event.setCancelled(true);}
    @EventHandler public void splash(PotionSplashEvent event){if(!(event.getPotion().getShooter() instanceof Player shooter))return;for(var target:event.getAffectedEntities())if(target instanceof Player player&&session.sameKingdom(shooter,player))event.setIntensity(target,0);}
    @EventHandler public void lingering(AreaEffectCloudApplyEvent event){AreaEffectCloud cloud=event.getEntity();if(!(cloud.getSource() instanceof Player shooter))return;event.getAffectedEntities().removeIf(entity->entity instanceof Player player&&session.sameKingdom(shooter,player));}
    private static Player attacker(EntityDamageByEntityEvent event){
        if(event.getDamager() instanceof Player player)return player;
        if(event.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof Player player)return player;
        if(event.getDamager() instanceof TNTPrimed tnt&&tnt.getSource() instanceof Player player)return player;
        return null;
    }
}
