package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;

/** Offensive sheep that causes a conventional explosion on impact. */
public class TntSheep extends AbstractSheep {

    public TntSheep() {
        super(SheepType.TNT);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        float power = explosionPower(thrower, 4.5F);
        Location loc = sheep.getLocation();
        loc.getWorld().createExplosion(loc, power, false, true, thrower);
        applyExplosionDamage(thrower, loc, power);
        return true;
    }
}
