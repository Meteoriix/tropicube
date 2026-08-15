package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.game.GameTeam;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Support sheep that temporarily strengthens nearby allies. */
public class StrengthSheep extends AbstractSheep {

    public StrengthSheep() {
        super(SheepType.STRENGTH);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        sheep.setVelocity(new Vector(0, 0, 0));

        GamePlayer throwerGp = plugin.getGameManager().getPlayer(thrower);
        if (throwerGp == null) return true;
        GameTeam throwerTeam = throwerGp.getTeam();
        var balance = plugin.getGameplayBalance();
        int duration = balance.ticks("sheep.strength.duration-seconds");
        int refreshPeriod = balance.integer("sheep.strength.refresh-period-ticks");
        double radius = balance.decimal("sheep.strength.radius");

        Set<UUID> buffed = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration || sheep.isDead() || !sheep.isValid()) {
                    buffed.forEach(plugin.getSheepManager()::removeStrengthBuff);
                    sheep.remove();
                    cancel();
                    return;
                }

                if (ticks % refreshPeriod == 0) {
                    Set<UUID> playersInAura = new HashSet<>();
                    for (Entity entity : sheep.getNearbyEntities(radius, radius, radius)) {
                        if (entity instanceof Player target) {
                            grantBuff(target, throwerTeam, buffed, playersInAura);
                        }
                    }
                    Set<UUID> playersWhoLeft = new HashSet<>(buffed);
                    playersWhoLeft.removeAll(playersInAura);
                    playersWhoLeft.forEach(plugin.getSheepManager()::removeStrengthBuff);
                    buffed.removeAll(playersWhoLeft);
                    sheep.getWorld().spawnParticle(Particle.SWEEP_ATTACK, sheep.getLocation().add(0, 1, 0), 6, 0.5, 0.5, 0.5, 0);
                    sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.5F);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return false;
    }

    private void grantBuff(Player target, GameTeam team, Set<UUID> buffed, Set<UUID> playersInAura) {
        GamePlayer gp = plugin.getGameManager().getPlayer(target);
        if (gp == null || gp.getTeam() != team || !gp.isAlive()) return;
        playersInAura.add(target.getUniqueId());
        if (buffed.add(target.getUniqueId())) {
            plugin.getSheepManager().addStrengthBuff(target.getUniqueId());
        }
    }
}
