package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

/** Sheep that applies a poison effect in its area of ​​impact. */
public class PoisonSheep extends AbstractSheep {

    public PoisonSheep() {
        super(SheepType.POISON);
    }

    @Override
    public boolean hasCountdown() { return false; }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location loc = sheep.getLocation();
        var balance = plugin.getGameplayBalance();
        int cloudDurationTicks = balance.ticks("sheep.poison.duration-seconds");
        double cloudRadius = balance.decimal("sheep.poison.radius");
        int damagePeriod = balance.integer("sheep.poison.damage-period-ticks");

        // Spawn area effect cloud (lingering poison zone)
        AreaEffectCloud cloud = loc.getWorld().spawn(loc, AreaEffectCloud.class, c -> {
            c.setDuration(cloudDurationTicks);
            c.setRadius((float) cloudRadius);
            c.setRadiusPerTick(-0.01f); // shrinks slowly
            c.setRadiusOnUse(0f);
            c.setReapplicationDelay(20);
            c.setSource(thrower);
            c.setColor(org.bukkit.Color.fromRGB(0, 180, 0));
        });

        // Periodically deals damage to visible enemies in the cloud.
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (ticks >= cloudDurationTicks || !cloud.isValid()) {
                    cancel();
                    return;
                }
                if (ticks % damagePeriod == 0) {
                    loc.getWorld().spawnParticle(Particle.ENTITY_EFFECT, loc.clone().add(0, 0.5, 0),
                            20, cloudRadius * 0.4, 0.5, cloudRadius * 0.4, 0.01, Color.fromARGB(85, 0, 255, 120));
                    // Applies damage to enemies in the area.
                    for (Player target : loc.getNearbyPlayers(cloudRadius)) {
                        if (isEnemy(thrower, target)) {
                            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 30,
                                    balance.integer("sheep.poison.poison-amplifier")));
                            damageEnemy(thrower, target, balance.decimal("sheep.poison.direct-damage")
                                    * sheepDamageMultiplier(thrower));
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }
}
