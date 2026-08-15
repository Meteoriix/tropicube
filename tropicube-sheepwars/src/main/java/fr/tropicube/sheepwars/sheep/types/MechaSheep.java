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

/** Sheep who summons a mechanical unit controlled and monitored by the manager. */
public class MechaSheep extends AbstractSheep {

    public MechaSheep() {
        super(SheepType.MECHA);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep impactSheep) {
        Location loc = findSafeLocation(impactSheep.getLocation());
        var balance = plugin.getGameplayBalance();
        double mechaHealth = balance.decimal("sheep.mecha.health");
        int lifespan = balance.ticks("sheep.mecha.lifespan-seconds");

        // The sheep is the vehicle (bottom) — it keeps its AI and pilots the golem
        Sheep pilot = loc.getWorld().spawn(loc, Sheep.class, s -> {
            s.setColor(SheepType.MECHA.getWool());
            s.setSilent(true);
            s.setInvulnerable(true);
            // setAware(true) by default — the sheep keeps its AI and pathfinding
        });

        IronGolem golem = loc.getWorld().spawn(loc, IronGolem.class, g -> {
            AttributeInstance dmg = g.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmg != null) dmg.setBaseValue(balance.decimal("sheep.mecha.attack-damage"));

            AttributeInstance knockback = g.getAttribute(Attribute.ATTACK_KNOCKBACK);
            if (knockback != null) knockback.setBaseValue(balance.decimal("sheep.mecha.attack-knockback"));

            AttributeInstance maxHp = g.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(mechaHealth);
            g.setHealth(mechaHealth);

            // No need for speed on the golem, it's the sheep that moves
            g.setPlayerCreated(false);
        });

        // The golem is a passenger of the sheep: the sheep pilots, the golem attacks
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
                if (ticks >= lifespan) {
                    golem.remove();
                    pilot.remove();
                    sm.removeGolem(golem.getUniqueId());
                    cancel();
                    return;
                }

                if (ticks % 5 != 0) return;

                GamePlayer throwerGp = plugin.getGameManager().getPlayer(thrower);
                if (throwerGp == null) return;

                Player nearestEnemy = pilot.getLocation()
                        .getNearbyPlayers(balance.decimal("sheep.mecha.scan-radius")).stream()
                        .filter(p -> {
                            GamePlayer gp = plugin.getGameManager().getPlayer(p);
                            return gp != null && gp.isAlive() && gp.getTeam() != throwerGp.getTeam();
                        })
                        .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(pilot.getLocation())))
                        .orElse(null);

                if (nearestEnemy != null) {
                    // We direct the sheep (the pilot) towards the enemy
                    pilot.getPathfinder().moveTo(nearestEnemy, 1.0);
                    // The golem attacks the same target
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
