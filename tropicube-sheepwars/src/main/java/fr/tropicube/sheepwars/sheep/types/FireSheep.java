package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;

/** Incendiary sheep that sets its impact zone ablaze. */
public class FireSheep extends AbstractSheep {

    public FireSheep() {
        super(SheepType.FIRE);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        for (Entity entity : sheep.getNearbyEntities(5, 5, 5)) {
            if (entity instanceof Player target && isEnemy(thrower, target)) {
                target.setFireTicks(120);
            }
        }
        float power = explosionPower(thrower, 2.0F);
        Location loc = sheep.getLocation();
        loc.getWorld().createExplosion(loc, power, true, true, thrower);
        applyExplosionDamage(thrower, loc, power);
        return true;
    }
}
