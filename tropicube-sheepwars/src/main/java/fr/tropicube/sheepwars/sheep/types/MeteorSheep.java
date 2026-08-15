package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.concurrent.ThreadLocalRandom;

/** Sheep that triggers a meteor strike on the targeted area. */
public class MeteorSheep extends AbstractSheep {

    public MeteorSheep() {
        super(SheepType.METEOR);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location impact = sheep.getLocation();
        var balance = plugin.getGameplayBalance();
        createSheepExplosion(thrower, impact,
                (float) balance.decimal("sheep.meteor.impact-block-power"), false, true);
        applyExplosionDamage(thrower, impact, balance.decimal("sheep.meteor.impact-radius"),
                balance.decimal("sheep.meteor.impact-damage"));

        // Rain four controlled fireballs around the impact.
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= balance.integer("sheep.meteor.projectile-count")) {
                    cancel();
                    return;
                }
                double spread = balance.decimal("sheep.meteor.projectile-spread");
                double offsetX = ThreadLocalRandom.current().nextDouble(-spread, spread);
                double offsetZ = ThreadLocalRandom.current().nextDouble(-spread, spread);
                Location spawn = impact.clone().add(offsetX, 25, offsetZ);

                Fireball fireball = (Fireball) impact.getWorld().spawnEntity(spawn,
                        org.bukkit.entity.EntityType.FIREBALL);
                fireball.setShooter(thrower);
                fireball.setYield(0F);
                fireball.setIsIncendiary(false);
                fireball.setDirection(new org.bukkit.util.Vector(0, -1, 0));
                plugin.getSheepManager().registerMeteorFireball(fireball.getUniqueId(), thrower.getUniqueId());
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (plugin.getSheepManager().consumeMeteorFireball(fireball.getUniqueId()) != null
                            && fireball.isValid()) fireball.remove();
                }, 100L);
                count++;
            }
        }.runTaskTimer(plugin, 0L, balance.integer("sheep.meteor.projectile-period-ticks"));

        return true;
    }

    /** Resolves one tagged meteor projectile through controlled team-aware damage. */
    public void explodeProjectile(Player thrower, Location impact) {
        var balance = plugin.getGameplayBalance();
        createSheepExplosion(thrower, impact,
                (float) balance.decimal("sheep.meteor.projectile-block-power"), false, true);
        applyExplosionDamage(thrower, impact, balance.decimal("sheep.meteor.projectile-radius"),
                balance.decimal("sheep.meteor.projectile-damage"));
    }
}
