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

/** Mouton de soutien qui renforce temporairement les alliés proches. */
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

        Set<UUID> buffed = new HashSet<>();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 100 || sheep.isDead() || !sheep.isValid()) {
                    buffed.forEach(plugin.getSheepManager()::removeStrengthBuff);
                    sheep.remove();
                    cancel();
                    return;
                }

                if (ticks % 20 == 0) {
                    grantBuff(thrower, throwerTeam, buffed);
                    for (Entity entity : sheep.getNearbyEntities(5, 5, 5)) {
                        if (entity instanceof Player target) {
                            grantBuff(target, throwerTeam, buffed);
                        }
                    }
                    sheep.getWorld().spawnParticle(Particle.SWEEP_ATTACK, sheep.getLocation().add(0, 1, 0), 6, 0.5, 0.5, 0.5, 0);
                    sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.5F);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return false;
    }

    private void grantBuff(Player target, GameTeam team, Set<UUID> buffed) {
        GamePlayer gp = plugin.getGameManager().getPlayer(target);
        if (gp == null || gp.getTeam() != team || !gp.isAlive()) return;
        if (buffed.add(target.getUniqueId())) {
            plugin.getSheepManager().addStrengthBuff(target.getUniqueId());
        }
    }
}
