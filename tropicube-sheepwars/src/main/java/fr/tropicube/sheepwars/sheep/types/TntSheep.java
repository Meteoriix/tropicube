package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;

/** Mouton offensif qui provoque une explosion conventionnelle à l'impact. */
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
