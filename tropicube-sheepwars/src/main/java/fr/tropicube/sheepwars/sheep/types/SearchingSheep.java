package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Comparator;
import java.util.UUID;

/** Mouton à tête chercheuse qui poursuit une cible ennemie. */
public class SearchingSheep extends AbstractSheep {

    private static final int WAIT_TICKS = 160;   // 8 seconds scanning phase
    private static final double SCAN_RADIUS = 15;

    public SearchingSheep() {
        super(SheepType.SEARCHING);
    }

    @Override
    public boolean hasCountdown() { return false; }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        // Immobilise le mouton pendant la recherche de cible.
        sheep.setGravity(false);
        sheep.setAware(false);
        sheep.setInvulnerable(true);
        sheep.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        // Neutralise les dégâts de chute pendant la poursuite.
        FallDamageBlocker blocker = new FallDamageBlocker(sheep.getUniqueId());
        plugin.getServer().getPluginManager().registerEvents(blocker, plugin);

        new BukkitRunnable() {
            int ticks = 0;
            boolean pursuing = false;
            Player target = null;

            // Cliff-descent tracking
            double lastX = sheep.getLocation().getX();
            double lastZ = sheep.getLocation().getZ();
            int stuckTicks   = 0;   // consecutive ticks barely moving horizontally
            boolean descending  = false;  // true while we have direct velocity control
            int descentTicks = 0;

            @Override
            public void run() {
                if (!sheep.isValid()) {
                    org.bukkit.event.HandlerList.unregisterAll(blocker);
                    cancel();
                    return;
                }

                // Fait pulser la couleur et recherche une cible toutes les cinq ticks.
                if (!pursuing) {
                    if (ticks % 5 == 0) {
                        sheep.setColor(ticks % 10 < 5 ? DyeColor.LIME : DyeColor.WHITE);

                        target = sheep.getLocation().getNearbyPlayers(SCAN_RADIUS).stream()
                                .filter(p -> isEnemy(thrower, p))
                                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(sheep.getLocation())))
                                .orElse(null);

                        if (target != null) {
                            // Found a target — switch to pursuit mode
                            pursuing = true;
                            sheep.setGravity(true);
                            sheep.setAware(true);
                            sheep.setInvulnerable(false);
                            lastX = sheep.getLocation().getX();
                            lastZ = sheep.getLocation().getZ();
                        }
                    }

                    if (ticks >= WAIT_TICKS) {
                        // Explose lorsque la recherche expire sans cible.
                        explodeAt(thrower, sheep);
                        org.bukkit.event.HandlerList.unregisterAll(blocker);
                        cancel();
                        return;
                    }
                } else {
                    // Pursuit phase
                    if (!target.isOnline() || !isEnemy(thrower, target)) {
                        explodeAt(thrower, sheep);
                        org.bukkit.event.HandlerList.unregisterAll(blocker);
                        cancel();
                        return;
                    }

                    Location sheepLoc = sheep.getLocation();
                    double yDiff = target.getLocation().getY() - sheepLoc.getY();
                    Vector toTarget = target.getLocation().toVector().subtract(sheepLoc.toVector());
                    double hDist = Math.sqrt(toTarget.getX() * toTarget.getX() + toTarget.getZ() * toTarget.getZ());

                    // Suit le déplacement horizontal pour détecter un blocage au bord du vide.
                    double movedH = Math.sqrt(Math.pow(sheepLoc.getX() - lastX, 2) + Math.pow(sheepLoc.getZ() - lastZ, 2));
                    lastX = sheepLoc.getX();
                    lastZ = sheepLoc.getZ();

                    if (descending) {
                        // Direct velocity control — pathfinder is disabled
                        descentTicks++;
                        if (yDiff >= -1.0 || descentTicks > 80) {
                            // Rend le contrôle au pathfinder après la descente ou son expiration.
                            descending = false;
                            descentTicks = 0;
                            stuckTicks = 0;
                            sheep.setAware(true);
                        } else {
                            // Accélère horizontalement vers la cible et verticalement vers le bas.
                            if (hDist > 0.3) {
                                Vector vel = sheep.getVelocity();
                                double downVel = Math.max(vel.getY() - 0.10, -0.65);
                                sheep.setVelocity(new Vector(
                                    toTarget.getX() / hDist * 0.42,
                                    downVel,
                                    toTarget.getZ() / hDist * 0.42
                                ));
                            }
                        }
                    } else {
                        // Normal pathfinder-driven pursuit
                        if (ticks % 5 == 0) {
                            sheep.getPathfinder().moveTo(target, 1.6);
                            sheep.setTarget(target);
                        }

                        // Detect cliff stall: target significantly below, far away, sheep barely moving
                        if (yDiff < -2.0 && hDist > 2.0 && movedH < 0.05) {
                            stuckTicks++;
                        } else {
                            stuckTicks = Math.max(0, stuckTicks - 2);
                        }

                        if (stuckTicks >= 10) {
                            // Sheep is stuck at a cliff edge — take over movement
                            descending = true;
                            descentTicks = 0;
                            stuckTicks = 0;
                            sheep.setAware(false);
                        }
                    }

                    // Explode on contact
                    if (sheepLoc.distanceSquared(target.getLocation()) <= 4.0) {
                        explodeAt(thrower, sheep);
                        org.bukkit.event.HandlerList.unregisterAll(blocker);
                        cancel();
                        return;
                    }

                    // Safety timeout during pursuit (5 seconds)
                    if (ticks >= WAIT_TICKS + 100) {
                        explodeAt(thrower, sheep);
                        org.bukkit.event.HandlerList.unregisterAll(blocker);
                        cancel();
                        return;
                    }
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return false;
    }

    private void explodeAt(Player thrower, Sheep sheep) {
        if (!sheep.isValid()) return;
        float power = explosionPower(thrower, 3.0F);
        Location loc = sheep.getLocation();
        loc.getWorld().createExplosion(loc, power, false, true, thrower);
        applyExplosionDamage(thrower, loc, power);
        sheep.remove();
    }

    /**
     * Bloque les dégâts de chute du mouton chercheur pendant sa poursuite.
     */
        private record FallDamageBlocker(UUID sheepId) implements Listener {

        @EventHandler
            public void onEntityDamage(EntityDamageEvent event) {
                if (!(event.getEntity() instanceof Sheep s) || !s.getUniqueId().equals(sheepId)) return;
                if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                    event.setCancelled(true);
                }
            }
        }
}
