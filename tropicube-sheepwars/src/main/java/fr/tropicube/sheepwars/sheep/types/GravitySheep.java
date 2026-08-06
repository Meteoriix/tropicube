package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/** Mouton qui attire les entités voisines vers son point d'impact. */
public class GravitySheep extends AbstractSheep {

    public GravitySheep() {
        super(SheepType.GRAVITY);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location center = sheep.getLocation().clone();

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                center.getWorld().spawnParticle(Particle.PORTAL, center, 25, 4, 2, 4);

                if (ticks <= 20) {
                    // Attire uniquement les ennemis vers le centre
                    for (Player target : center.getNearbyPlayers(10)) {
                        if (!isEnemy(thrower, target)) continue;
                        Vector pull = center.toVector()
                                .subtract(target.getLocation().toVector())
                                .normalize()
                                .multiply(0.7);
                        target.setVelocity(pull);
                    }
                } else if (ticks == 21) {
                    // Projette les ennemis en l'air
                    center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.5F, 0.4F);
                    for (Player target : center.getNearbyPlayers(7)) {
                        if (!isEnemy(thrower, target)) continue;
                        target.setVelocity(new Vector(0, 1.8, 0));
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }
}
