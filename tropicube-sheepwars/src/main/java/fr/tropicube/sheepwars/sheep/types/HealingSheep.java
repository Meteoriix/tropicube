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

/** Support sheep that heals allies in its area. */
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
        var balance = plugin.getGameplayBalance();
        int duration = balance.ticks("sheep.healing.duration-seconds");
        int period = balance.integer("sheep.healing.pulse-period-ticks");
        double radius = balance.decimal("sheep.healing.radius");

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= duration || sheep.isDead() || !sheep.isValid()) {
                    sheep.remove();
                    cancel();
                    return;
                }

                if (ticks % period == 0) {
                    for (Entity entity : sheep.getNearbyEntities(radius, radius, radius)) {
                        if (entity instanceof Player target) {
                            healIfTeammate(target, throwerTeam);
                        }
                    }

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
        target.setHealth(Math.min(target.getHealth()
                + plugin.getGameplayBalance().decimal("sheep.healing.pulse-heal"), maxHealth));
    }
}
