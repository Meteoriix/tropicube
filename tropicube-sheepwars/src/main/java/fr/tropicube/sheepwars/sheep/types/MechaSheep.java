package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.sheep.SheepManager;
import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Comparator;

/** Mouton qui invoque une unité mécanique contrôlée et suivie par le gestionnaire. */
public class MechaSheep extends AbstractSheep {

    private static final double MECHA_HP    = 100.0;
    private static final int    LIFESPAN    = 600;   // 30 seconds
    private static final double SCAN_RADIUS = 15;

    public MechaSheep() {
        super(SheepType.MECHA);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep impactSheep) {
        Location loc = findSafeLocation(impactSheep.getLocation());

        // Le mouton est le véhicule (en bas) — il garde son IA et pilote le golem
        Sheep pilot = loc.getWorld().spawn(loc, Sheep.class, s -> {
            s.setColor(SheepType.MECHA.getWool());
            s.setSilent(true);
            s.setInvulnerable(true);
            // setAware(true) par défaut — le mouton conserve son IA et son pathfinding
        });

        IronGolem golem = loc.getWorld().spawn(loc, IronGolem.class, g -> {
            AttributeInstance dmg = g.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(3.0);

            AttributeInstance knockback = g.getAttribute(Attribute.ATTACK_KNOCKBACK);
            if (knockback != null) knockback.setBaseValue(2.0);

            AttributeInstance maxHp = g.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(MECHA_HP);
            g.setHealth(MECHA_HP);

            // Pas besoin de vitesse sur le golem, c'est le mouton qui se déplace
            g.setPlayerCreated(false);
        });

        // Le golem est passager du mouton : le mouton pilote, le golem attaque
        pilot.addPassenger(golem);

        SheepManager sm = plugin.getSheepManager();
        sm.registerGolem(golem.getUniqueId(), new SheepManager.MechaData(thrower.getUniqueId(), pilot.getUniqueId()));

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (!golem.isValid() || !pilot.isValid()) {
                    sm.removeGolem(golem.getUniqueId());
                    cancel();
                    return;
                }

                ticks++;
                if (ticks >= LIFESPAN) {
                    golem.remove();
                    pilot.remove();
                    sm.removeGolem(golem.getUniqueId());
                    cancel();
                    return;
                }

                if (ticks % 5 != 0) return;

                GamePlayer throwerGp = plugin.getGameManager().getPlayer(thrower);
                if (throwerGp == null) return;

                Player nearestEnemy = pilot.getLocation().getNearbyPlayers(SCAN_RADIUS).stream()
                        .filter(p -> {
                            GamePlayer gp = plugin.getGameManager().getPlayer(p);
                            return gp != null && gp.isAlive() && gp.getTeam() != throwerGp.getTeam();
                        })
                        .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(pilot.getLocation())))
                        .orElse(null);

                if (nearestEnemy != null) {
                    // On dirige le mouton (le pilote) vers l'ennemi
                    pilot.getPathfinder().moveTo(nearestEnemy, 1.0);
                    // Le golem attaque la même cible
                    golem.setTarget(nearestEnemy);
                } else {
                    pilot.getPathfinder().stopPathfinding();
                    golem.setTarget(null);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);

        return true;
    }

    private Location findSafeLocation(Location origin) {
        for (int dy = 0; dy <= 5; dy++) {
            Location candidate = origin.clone().add(0, dy, 0);
            if (isClear(candidate)) return candidate;
        }
        for (int dy = 1; dy <= 5; dy++) {
            Location candidate = origin.clone().subtract(0, dy, 0);
            if (isClear(candidate)) return candidate;
        }
        return origin;
    }

    private boolean isClear(Location loc) {
        return loc.getBlock().getType() == Material.AIR
                && loc.clone().add(0, 1, 0).getBlock().getType() == Material.AIR
                && loc.clone().add(0, 2, 0).getBlock().getType() == Material.AIR;
    }
}
