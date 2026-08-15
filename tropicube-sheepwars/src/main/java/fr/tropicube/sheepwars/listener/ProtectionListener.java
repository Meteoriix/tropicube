package fr.tropicube.sheepwars.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameState;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Anti-grievance protection and global game rules.
 */
public class ProtectionListener implements Listener {

    private final TropicubeSheepwars plugin;

    public ProtectionListener(TropicubeSheepwars plugin) {
        this.plugin = plugin;
    }

    /**
     * Prevent players from destroying out-of-game blocks, and prevent item and XP drops no matter what
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        event.setDropItems(false);
        event.setExpToDrop(0);

        if (isNotPlaying()) event.setCancelled(true);
    }

    /**
     * Prevent out-of-game blocks from being destroyed by explosions, and prevent item and XP drops no matter what
     */
    @EventHandler
    public void onBlockDestroy(BlockDestroyEvent event) {
        event.setWillDrop(false);
        event.setExpToDrop(0);

        if (isNotPlaying()) event.setCancelled(true);
    }

    /**
     * Prevent the laying of blocks
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    /**
     * Prevent player food loss
     */
    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
        if (event.getEntity() instanceof Player player) player.setFoodLevel(20);
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }

    /** Suppresses rail items detached by block physics while still allowing the rail block to disappear. */
    @EventHandler
    public void onRailItemSpawn(ItemSpawnEvent event) {
        if (!isNotPlaying() && isRailMaterial(event.getEntity().getItemStack().getType())) {
            event.setCancelled(true);
        }
    }

    static boolean isRailMaterial(Material material) {
        return switch (material) {
            case RAIL, POWERED_RAIL, DETECTOR_RAIL, ACTIVATOR_RAIL -> true;
            default -> false;
        };
    }

    /** Prevents friendly fire between teammates. */
    @EventHandler
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        var victimGp   = plugin.getGameManager().getPlayer(victim);
        var attackerGp = plugin.getGameManager().getPlayer(attacker);

        if (victimGp != null && attackerGp != null && victimGp.getTeam() == attackerGp.getTeam()) event.setCancelled(true);
    }

    /** Prevents water flow to preserve maps. */
    @EventHandler
    public void onWaterFlow(BlockFromToEvent event) {
        if (event.getBlock().getType().name().contains("WATER")) event.setCancelled(true);
    }

    @EventHandler
    public void onWeather(WeatherChangeEvent event) {
        if (event.toWeatherState()) event.setCancelled(true);
    }

    // ── Utilitaire ────────────────────────────────────────────────────────

    private boolean isNotPlaying() {
        GameState state = plugin.getGameManager().getState();
        return state != GameState.PLAYING;
    }
}
