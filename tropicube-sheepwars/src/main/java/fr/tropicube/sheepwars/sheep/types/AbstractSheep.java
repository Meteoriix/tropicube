package fr.tropicube.sheepwars.sheep.types;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.sheep.RadialDamage;
import fr.tropicube.sheepwars.sheep.SheepType;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;

import java.util.Map;
import java.util.UUID;

/** Common contract of abilities triggered on launch and sheep impact. */
public abstract class AbstractSheep {

    protected final SheepType type;
    /** Injected by {@link fr.tropicube.sheepwars.sheep.SheepManager} at time of recording. */
    protected TropicubeSheepwars plugin;

    /** Called by SheepManager after instantiation (dependency injection). */
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

    /** Whether this sheep enters the configured blink countdown before triggering. */
    public boolean hasCountdown() { return true; }

    /** Returns the damage multiplier of the DPS sheep kit without changing effect radii. */
    protected double sheepDamageMultiplier(Player thrower) {
        GamePlayer gp = plugin.getGameManager().getPlayer(thrower);
        if (gp != null && gp.getKit() == PlayerKit.DPS_SHEEP) {
            return plugin.getGameplayBalance().decimal("kits.dps-sheep-damage-multiplier");
        }
        return 1.0;
    }

    /**
     * Applies the sole source of player damage for a sheep explosion.
     * Native explosion damage is suppressed by the player listener.
     */
    protected void applyExplosionDamage(Player thrower, Location center, double radius, double maximumDamage) {
        applyExplosionDamage(thrower, center, radius, maximumDamage, null, 0);
    }

    /** Applies capped radial damage shared by every hit of one multi-explosion ability. */
    protected void applyExplosionDamage(Player thrower, Location center, double radius, double maximumDamage,
                                        Map<UUID, Double> damageLedger, double damageCap) {
        double multiplier = sheepDamageMultiplier(thrower);
        for (Player target : center.getNearbyPlayers(radius)) {
            if (!isEnemy(thrower, target)) continue;
            double dist = target.getLocation().distance(center);
            double alreadyApplied = damageLedger == null
                    ? 0 : damageLedger.getOrDefault(target.getUniqueId(), 0.0);
            double damage = RadialDamage.calculate(dist, radius, maximumDamage, multiplier,
                    alreadyApplied, damageCap);
            if (damage <= 0) continue;
            damageEnemy(thrower, target, damage);
            if (damageLedger != null) damageLedger.merge(target.getUniqueId(), damage, Double::sum);
        }
    }

    /** Creates block and visual explosion effects while native player damage is temporarily suppressed. */
    protected void createSheepExplosion(Player thrower, Location center, float blockPower,
                                        boolean setFire, boolean breakBlocks) {
        plugin.getSheepManager().createSheepExplosion(center, blockPower, setFire, breakBlocks, thrower);
    }

    /** Applies direct sheep damage with player attribution and no secondary melee multiplier. */
    protected void damageEnemy(Player thrower, Player target, double damage) {
        plugin.getSheepManager().damageWithSheep(target, damage, thrower);
    }

    /**
     * @return {@code true} if {@code target} is a living enemy of {@code thrower}
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
