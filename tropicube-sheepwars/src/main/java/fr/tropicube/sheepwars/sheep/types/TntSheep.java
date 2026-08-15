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
        Location loc = sheep.getLocation();
        var balance = plugin.getGameplayBalance();
        createSheepExplosion(thrower, loc, (float) balance.decimal("sheep.tnt.block-power"), false, true);
        applyExplosionDamage(thrower, loc, balance.decimal("sheep.tnt.radius"),
                balance.decimal("sheep.tnt.damage"));
        return true;
    }
}
