package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Sheep that plunges nearby opponents into darkness. */
public class DarknessSheep extends AbstractSheep {

    public DarknessSheep() {
        super(SheepType.DARKNESS);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        var balance = plugin.getGameplayBalance();
        double radius = balance.decimal("sheep.darkness.radius");
        int duration = balance.ticks("sheep.darkness.duration-seconds");
        for (Entity entity : sheep.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof Player target && isEnemy(thrower, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration,
                        balance.integer("sheep.darkness.slowness-amplifier")));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, duration,
                        balance.integer("sheep.darkness.mining-fatigue-amplifier")));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, duration,
                        balance.integer("sheep.darkness.blindness-amplifier")));
            }
        }
        return true;
    }
}
