package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Comparator;

/** Sheep that strikes nearby enemies with lightning. */
public class LightningSheep extends AbstractSheep {

    public LightningSheep() {
        super(SheepType.LIGHTNING);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location impact = sheep.getLocation();
        var balance = plugin.getGameplayBalance();
        impact.getWorld().strikeLightningEffect(impact);

        List<Player> chainTargets = impact.getNearbyPlayers(balance.decimal("sheep.lightning.radius")).stream()
                .filter(p -> isEnemy(thrower, p))
                .sorted(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(impact)))
                .limit(balance.integer("sheep.lightning.targets"))
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
                    damageEnemy(thrower, target, balance.decimal("sheep.lightning.damage")
                            * sheepDamageMultiplier(thrower));
                }
                index++;
            }
        }.runTaskTimer(plugin, 10L, 10L);

        return true;
    }
}
