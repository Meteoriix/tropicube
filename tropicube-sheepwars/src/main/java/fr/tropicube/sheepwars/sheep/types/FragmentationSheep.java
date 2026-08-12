package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

/** Explosive sheep that projects several secondary charges. */
public class FragmentationSheep extends AbstractSheep {

    private static final int FRAG_COUNT = 5;
    private static final double FRAG_EXPLOSION_POWER = 2.7;

    public FragmentationSheep() {
        super(SheepType.FRAGMENTATION);
    }

    @Override
    public boolean hasCountdown() { return false; }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location loc = sheep.getLocation();

        // Small central explosion
        float centralPower = explosionPower(thrower, 1.0F);
        loc.getWorld().createExplosion(loc, centralPower, false, false, thrower);
        applyExplosionDamage(thrower, loc, centralPower);

        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.5F);

        // Projects FRAG_COUNT fragments in random directions.
        for (int i = 0; i < FRAG_COUNT; i++) {
            ThreadLocalRandom rng = ThreadLocalRandom.current();
            Vector dir = new Vector(
                rng.nextDouble(-1, 1),
                rng.nextDouble(0.3, 0.8),
                rng.nextDouble(-1, 1)
            ).normalize().multiply(1.4);

            Sheep fragSheep = loc.getWorld().spawn(loc, Sheep.class, s -> {
                s.setColor(DyeColor.BLACK);
                s.setBaby();
                s.setSilent(true);
                s.setAware(false);
                s.setInvulnerable(true);
                s.setVelocity(dir);
                // Tag so they don't trigger game sheep handlers unexpectedly
                s.getPersistentDataContainer().set(
                    plugin.getSheepManager().sheepTypeKey,
                    org.bukkit.persistence.PersistentDataType.STRING,
                    SheepType.FRAGMENTATION.name()
                );
            });

            // Each fragment explodes after 0.6 to 1.2 seconds.
            int delay = 12 + rng.nextInt(12);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!fragSheep.isValid()) return;
                    Location fragLoc = fragSheep.getLocation();
                    float power = explosionPower(thrower, (float) FRAG_EXPLOSION_POWER);
                    fragLoc.getWorld().createExplosion(fragLoc, power, false, false, thrower);
                    applyExplosionDamage(thrower, fragLoc, power);
                    fragSheep.remove();
                }
            }.runTaskLater(plugin, delay);
        }

        return true;
    }
}
