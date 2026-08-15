package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Sheep that attracts neighboring entities towards its point of impact. */
public class GravitySheep extends AbstractSheep {

    public GravitySheep() {
        super(SheepType.GRAVITY);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location center = sheep.getLocation().clone();
        var balance = plugin.getGameplayBalance();
        int pullTicks = balance.ticks("sheep.gravity.pull-seconds");

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                center.getWorld().spawnParticle(Particle.PORTAL, center, 25, 4, 2, 4);

                if (ticks <= pullTicks) {
                    // Only attracts enemies to the center
                    for (Player target : center.getNearbyPlayers(balance.decimal("sheep.gravity.pull-radius"))) {
                        if (!isEnemy(thrower, target)) continue;
                        Vector pull = center.toVector()
                                .subtract(target.getLocation().toVector())
                                .normalize()
                                .multiply(balance.decimal("sheep.gravity.pull-speed"));
                        target.setVelocity(pull);
                    }
                } else if (ticks == pullTicks + 1) {
        // Launch enemies into the air
                    center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.4F);
                    for (Player target : center.getNearbyPlayers(balance.decimal("sheep.gravity.launch-radius"))) {
                        if (!isEnemy(thrower, target)) continue;
                        target.setVelocity(new Vector(0, balance.decimal("sheep.gravity.launch-speed"), 0));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }
}
