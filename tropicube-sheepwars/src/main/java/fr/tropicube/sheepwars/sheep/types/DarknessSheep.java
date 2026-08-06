package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Mouton qui plonge les adversaires proches dans l'obscurité. */
public class DarknessSheep extends AbstractSheep {

    public DarknessSheep() {
        super(SheepType.DARKNESS);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        for (Entity entity : sheep.getNearbyEntities(6, 6, 6)) {
            if (entity instanceof Player target && isEnemy(thrower, target)) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3));
                target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 1));
            }
        }
        return true;
    }
}
