package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;

import java.util.Comparator;
import java.util.List;

/** Mouton tactique qui échange la position de joueurs adverses. */
public class SwapSheep extends AbstractSheep {

    public SwapSheep() {
        super(SheepType.SWAP);
    }

    @Override
    public boolean onImpact(Player thrower, Sheep sheep) {
        Location impact = sheep.getLocation();

        List<Player> nearby = impact.getNearbyPlayers(5).stream()
                .filter(p -> isEnemy(thrower, p))
                .toList();

        Player target = nearby.stream()
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(impact)))
                .orElse(null);

        if (target == null) {
            thrower.setVelocity(thrower.getLocation().getDirection().normalize().multiply(1.8));
            return true;
        }

        Location throwerLoc = thrower.getLocation().clone();
        Location targetLoc = target.getLocation().clone();

        spawnSwapParticles(throwerLoc);
        spawnSwapParticles(targetLoc);

        thrower.teleport(targetLoc);
        target.teleport(throwerLoc);

        thrower.playSound(thrower.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);

        return true;
    }

    private void spawnSwapParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.PORTAL, loc, 40, 0.4, 1.0, 0.4);
    }
}
