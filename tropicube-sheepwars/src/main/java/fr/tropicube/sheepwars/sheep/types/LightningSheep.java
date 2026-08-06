package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;

/** Mouton qui frappe les ennemis proches à l'aide d'éclairs. */
public class LightningSheep extends AbstractSheep {

    public LightningSheep() {
        super(SheepType.LIGHTNING);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location impact = sheep.getLocation();
        impact.getWorld().strikeLightningEffect(impact);

        List<Player> chainTargets = impact.getNearbyPlayers(10).stream()
                .filter(p -> isEnemy(thrower, p))
                .limit(3)
                .toList();

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= chainTargets.size()) {
                    cancel();
                    return;
                }
                Player target = chainTargets.get(index);
                if (target.isOnline() && isEnemy(thrower, target)) {
                    target.getWorld().strikeLightningEffect(target.getLocation());
                    target.damage(5.0, thrower);
                }
                index++;
            }
        }.runTaskTimer(plugin, 10L, 10L);

        return true;
    }
}
