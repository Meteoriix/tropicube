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
        var balance = plugin.getGameplayBalance();
        double radius = balance.decimal("sheep.fire.radius");
        for (Entity entity : sheep.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player target && isEnemy(thrower, target)) {
                target.setFireTicks(balance.ticks("sheep.fire.fire-seconds"));
            }
        }
        Location loc = sheep.getLocation();
        createSheepExplosion(thrower, loc, (float) balance.decimal("sheep.fire.block-power"), true, true);
        applyExplosionDamage(thrower, loc, radius, balance.decimal("sheep.fire.damage"));
        return true;
    }
}
