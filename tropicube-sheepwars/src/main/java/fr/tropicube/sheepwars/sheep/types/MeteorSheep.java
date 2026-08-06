package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/** Mouton qui déclenche une frappe météorique sur la zone visée. */
public class MeteorSheep extends AbstractSheep {

    public MeteorSheep() {
        super(SheepType.METEOR);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location impact = sheep.getLocation();
        float impactPower = explosionPower(thrower, 2.0F);
        sheep.getWorld().createExplosion(impact, impactPower, false, true, thrower);
        applyExplosionDamage(thrower, impact, impactPower);

        // Fait pleuvoir 5 boules de feu autour de l'impact
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 5) {
                    cancel();
                    return;
                }
                double offsetX = ThreadLocalRandom.current().nextDouble(-6, 6);
                double offsetZ = ThreadLocalRandom.current().nextDouble(-6, 6);
                Location spawn = impact.clone().add(offsetX, 25, offsetZ);

                Fireball fireball = (Fireball) impact.getWorld().spawnEntity(spawn,
                        org.bukkit.entity.EntityType.FIREBALL);
                fireball.setShooter(thrower);
                fireball.setYield(explosionPower(thrower, 2.5F));
                fireball.setDirection(new org.bukkit.util.Vector(0, -1, 0));
                count++;
            }
        }.runTaskTimer(plugin, 0L, 5L);

        return true;
    }
}
