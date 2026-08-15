package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/** Sheep that temporarily disorients affected players. */
public class DistortSheep extends AbstractSheep {

    public static final String FB_KEY = "ender_sheep_fb";

    private static final double FLIGHT_TICKS = 12.0;
    // Horizontal drag factor: sum of 0.98^i over the duration of the flight.
    private static final double DRAG_FACTOR = (1.0 - Math.pow(0.98, FLIGHT_TICKS)) / 0.02;

    /** Cached key to avoid allocating a new NamespacedKey on every wave. */
    private NamespacedKey fbKey;

    public DistortSheep() {
        super(SheepType.DISTORT);
    }

    @Override
    public void onLaunch(Player thrower, Sheep sheep) {
        // The plugin is injected after construction; initializes the key on first use.
        if (fbKey == null) fbKey = new NamespacedKey(plugin, FB_KEY);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        sheep.setVelocity(new Vector(0, 0, 0));
        var balance = plugin.getGameplayBalance();
        int duration = balance.ticks("sheep.distort.duration-seconds");
        int wavePeriod = balance.integer("sheep.distort.waves-period-ticks");
        double maxRadius = balance.decimal("sheep.distort.radius");
        int blocksPerWave = balance.integer("sheep.distort.blocks-per-wave");

        sheep.getWorld().playSound(sheep.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5F, 0.7F);

        // Distorts a bounded part of the area for three seconds, then disappears.
        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!sheep.isValid()) {
                    cancel();
                    return;
                }

                // Six waves of at most twelve blocks limit map erosion and entity load.
                if (elapsed % wavePeriod == 0) {
                    spawnWave(sheep, maxRadius, blocksPerWave);
                }

                // Ambient portal particles
                if (elapsed % 2 == 0) {
                    sheep.getWorld().spawnParticle(Particle.PORTAL,
                            sheep.getLocation().add(0, 1, 0), 8, 1.0, 1.0, 1.0, 0.2);
                }

                // Ambient sound every second
                if (elapsed > 0 && elapsed % 20 == 0) {
                    sheep.getWorld().playSound(sheep.getLocation(),
                            Sound.ENTITY_ENDERMAN_AMBIENT, 0.6F, 0.8F);
                }

                elapsed++;

                if (elapsed >= duration) {
                    sheep.getWorld().spawnParticle(Particle.PORTAL,
                            sheep.getLocation().add(0, 1, 0), 120, 1.5, 1.5, 1.5, 0.3);
                    sheep.getWorld().playSound(sheep.getLocation(),
                            Sound.ENTITY_ENDERMAN_DEATH, 1.5F, 0.7F);
                    sheep.remove();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return false; // La tâche de cycle de vie conserve puis retire le mouton.
    }

    @SuppressWarnings("deprecation")
    private void spawnWave(Sheep sheep, double maxRadius, int blocksPerWave) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        Location sheepLoc = sheep.getLocation();
        Block centerBlock = sheepLoc.getBlock();
        Block below = centerBlock.getRelative(0, -1, 0);

        int r = (int) Math.ceil(maxRadius);

        // Center-weighted candidate collection:
        // The probability decreases quadratically with the distance from the center.
        List<Block> candidates = new ArrayList<>();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    Block b = centerBlock.getRelative(x, y, z);
                    if (!b.getType().isSolid() || b.getType().hasGravity() || b.equals(below)) continue;
                    double dist = Math.sqrt(x * x + y * y + z * z);
                    if (dist > maxRadius) continue;
                    double prob = Math.pow(1.0 - dist / maxRadius, 2);
                    if (rand.nextDouble() < prob) candidates.add(b);
                }
            }
        }

        Collections.shuffle(candidates, rand);

        List<FallingBlock> wave = new ArrayList<>();
        for (int i = 0; i < Math.min(blocksPerWave, candidates.size()); i++) {
            Block source = candidates.get(i);

            Vector toBlock = source.getLocation().subtract(sheepLoc).toVector();
            toBlock.setY(0);
            double distH = toBlock.length();

            Vector radial = distH > 0.01
                    ? toBlock.clone().normalize()
                    : randomHorizontal(rand);

            // Tangential = 90° rotation in horizontal plane (clockwise spin)
            Vector tangential = new Vector(-radial.getZ(), 0, radial.getX());

            double inward = rand.nextDouble(0.5, 2.0) * (distH / maxRadius);
            double spin   = rand.nextDouble(1.0, 3.0) * Math.max(0.3, distH / maxRadius);

            int dx = (int) Math.round(-radial.getX() * inward + tangential.getX() * spin) + rand.nextInt(-1, 2);
            int dy = rand.nextInt(-1, 2);
            int dz = (int) Math.round(-radial.getZ() * inward + tangential.getZ() * spin) + rand.nextInt(-1, 2);

            if (dx == 0 && dy == 0 && dz == 0) continue;

            // The destination is used only for direction calculation; no block is placed there.
            Location srcCenter = source.getLocation().add(0.5, 0.5, 0.5);
            Location dstCenter = source.getLocation().add(dx + 0.5, dy + 0.5, dz + 0.5);

            double ddx = dstCenter.getX() - srcCenter.getX();
            double ddy = dstCenter.getY() - srcCenter.getY();
            double ddz = dstCenter.getZ() - srcCenter.getZ();

            // Ballistic velocity: horizontal drag-compensated, vertical gravity-compensated
            Vector vel = new Vector(
                    ddx / DRAG_FACTOR,
                    (ddy + 0.04 * FLIGHT_TICKS * (FLIGHT_TICKS + 1) / 2.0) / FLIGHT_TICKS,
                    ddz / DRAG_FACTOR
            );

            BlockData movedData = source.getBlockData();
            source.setType(Material.AIR, false);

            FallingBlock fb = source.getWorld().spawnFallingBlock(srcCenter, movedData);
            fb.setDropItem(false);
            fb.setHurtEntities(false);
            fb.setVelocity(vel);
            fb.getPersistentDataContainer().set(fbKey, PersistentDataType.BYTE, (byte) 1);
            fb.setCancelDrop(true);

            sheep.getWorld().spawnParticle(Particle.PORTAL, srcCenter, 8, 0.2, 0.2, 0.2, 0.05);
            wave.add(fb);
        }

        // Removes animated blocks after 20 ticks with a particle effect.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (FallingBlock fb : wave) {
                    if (fb.isValid()) {
                        sheep.getWorld().spawnParticle(Particle.PORTAL, fb.getLocation(), 10, 0.2, 0.2, 0.2, 0.05);
                        fb.remove();
                    }
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    private Vector randomHorizontal(ThreadLocalRandom rand) {
        double angle = rand.nextDouble(Math.PI * 2);
        return new Vector(Math.cos(angle), 0, Math.sin(angle));
    }
}
