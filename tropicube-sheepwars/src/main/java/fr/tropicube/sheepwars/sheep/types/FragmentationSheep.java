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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Explosive sheep that projects several secondary charges. */
public class FragmentationSheep extends AbstractSheep {

    public FragmentationSheep() {
        super(SheepType.FRAGMENTATION);
    }

    @Override
    public boolean hasCountdown() { return false; }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location loc = sheep.getLocation();
        var balance = plugin.getGameplayBalance();
        double damageCap = balance.decimal("sheep.fragmentation.total-damage-cap");
        Map<UUID, Double> damageLedger = new HashMap<>();

        // Small central explosion
        createSheepExplosion(thrower, loc,
                (float) balance.decimal("sheep.fragmentation.central-block-power"), false, false);
        applyExplosionDamage(thrower, loc, balance.decimal("sheep.fragmentation.central-radius"),
                balance.decimal("sheep.fragmentation.central-damage"), damageLedger, damageCap);

        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 1.5F);

        // Projects FRAG_COUNT fragments in random directions.
        for (int i = 0; i < balance.integer("sheep.fragmentation.fragments"); i++) {
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
                plugin.getSheepManager().tagGameSheep(
                        s, SheepType.FRAGMENTATION, thrower.getUniqueId());
            });

            // Each fragment explodes after 0.8 to 1.3 seconds.
            int delay = 16 + rng.nextInt(11);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!fragSheep.isValid()) return;
                    Location fragLoc = fragSheep.getLocation();
                    createSheepExplosion(thrower, fragLoc,
                            (float) balance.decimal("sheep.fragmentation.fragment-block-power"), false, false);
                    applyExplosionDamage(thrower, fragLoc,
                            balance.decimal("sheep.fragmentation.fragment-radius"),
                            balance.decimal("sheep.fragmentation.fragment-damage"), damageLedger, damageCap);
                    fragSheep.remove();
                }
            }.runTaskLater(plugin, delay);
        }

        return true;
    }
}
