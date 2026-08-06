package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.game.GameTeam;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Objects;

/** Mouton de soutien qui soigne les alliés dans sa zone. */
public class HealingSheep extends AbstractSheep {

    public HealingSheep() {
        super(SheepType.HEALING);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        sheep.setVelocity(new Vector(0, 0, 0));

        GamePlayer throwerGp = plugin.getGameManager().getPlayer(thrower);
        if (throwerGp == null) return true;
        GameTeam throwerTeam = throwerGp.getTeam();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= 150 || sheep.isDead() || !sheep.isValid()) {
                    sheep.remove();
                    cancel();
                    return;
                }

                if (ticks % 20 == 0) {
                    for (Entity entity : sheep.getNearbyEntities(5, 5, 5)) {
                        if (entity instanceof Player target) {
                            healIfTeammate(target, throwerTeam);
                        }
                    }
                    healIfTeammate(thrower, throwerTeam);

                    sheep.getWorld().spawnParticle(Particle.HEART, sheep.getLocation().add(0, 1, 0), 6, 0.5, 0.5, 0.5, 0);
                    sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6F, 1.5F);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        return false;
    }

    private void healIfTeammate(Player target, GameTeam team) {
        GamePlayer gp = plugin.getGameManager().getPlayer(target);
        if (gp == null || gp.getTeam() != team || !gp.isAlive()) return;
        double maxHealth = Objects.requireNonNull(target.getAttribute(Attribute.MAX_HEALTH)).getValue();
        target.setHealth(Math.min(target.getHealth() + 2.0, maxHealth));
    }
}
