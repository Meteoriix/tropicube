package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;

/** Contrat commun des capacités déclenchées au lancement et à l'impact d'un mouton. */
public abstract class AbstractSheep {

    protected final SheepType type;
    /** Injecté par {@link fr.tropicube.sheepwars.sheep.SheepManager} au moment de l'enregistrement. */
    protected TropicubeSheepwars plugin;

    /** Appelé par SheepManager après instanciation (injection de dépendance). */
    public void injectPlugin(TropicubeSheepwars plugin) {
        this.plugin = plugin;
    }

    protected AbstractSheep(SheepType type) {
        this.type = type;
    }

    public SheepType getType() { return type; }

    public void onLaunch(Player thrower, Sheep sheep) {}

    public boolean onTick() { return false; }

    public abstract boolean onImpact(Player thrower, Sheep sheep);

    /** Whether this sheep enters a 1.25s blink countdown before triggering. */
    public boolean hasCountdown() { return true; }

    /** Returns the explosion power, boosted by 50% for DPS_SHEEP kit. */
    protected float explosionPower(Player thrower, float base) {
        GamePlayer gp = plugin.getGameManager().getPlayer(thrower);
        if (gp != null && gp.getKit() == PlayerKit.DPS_SHEEP) {
            return base * 1.5f;
        }
        return base;
    }

    /**
     * Applies radial explosion damage to all alive enemies in range.
     * Linear falloff: full damage at distance 0, zero at power*2 blocks.
     * Complète {@code createExplosion} afin de garantir les dégâts aux joueurs.
     */
    protected void applyExplosionDamage(Player thrower, Location center, float power) {
        double radius = power * 2.0;
        for (Player target : center.getNearbyPlayers(radius)) {
            if (!isEnemy(thrower, target)) continue;
            double dist = target.getLocation().distance(center);
            double damage = power * 2.0 * Math.max(0, 1.0 - dist / radius);
            if (damage > 0) target.damage(damage, thrower);
        }
    }

    /**
     * @return {@code true} si {@code target} est un ennemi vivant de {@code thrower}
     * Used by AoE sheep to avoid hitting teammates.
     */
    protected boolean isEnemy(Player thrower, Player target) {
        if (target.equals(thrower)) return false;
        GamePlayer throwerGp = plugin.getGameManager().getPlayer(thrower);
        GamePlayer targetGp  = plugin.getGameManager().getPlayer(target);
        return throwerGp != null && targetGp != null
                && targetGp.isAlive()
                && targetGp.getTeam() != throwerGp.getTeam();
    }
}
