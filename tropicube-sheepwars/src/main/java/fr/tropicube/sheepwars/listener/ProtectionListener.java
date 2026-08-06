package fr.tropicube.sheepwars.listener;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

/**
 * Protection anti-grief et règles de jeu globales.
 */
public class ProtectionListener implements Listener {

    private final TropicubeSheepwars plugin;

    public ProtectionListener(TropicubeSheepwars plugin) {
        this.plugin = plugin;
    }

    /**
     * Empêcher la destruction de blocs hors jeu par les joueurs, et empêcher les drops d'objets et d'XP quoiqu'il arrive
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        event.setDropItems(false);
        event.setExpToDrop(0);

        if (isNotPlaying()) event.setCancelled(true);
    }

    /**
     * Empêcher la destruction de blocs hors jeu par les explosions, et empêcher les drops d'objets et d'XP quoiqu'il arrive
     */
    @EventHandler
    public void onBlockDestroy(BlockDestroyEvent event) {
        event.setWillDrop(false);
        event.setExpToDrop(0);

        if (isNotPlaying()) event.setCancelled(true);
    }

    /**
     * Empêcher la pose de blocs
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    /**
     * Empêcher la perte de nourriture des joueurs
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

    /** Empêche le friendly fire entre coéquipiers. */
    @EventHandler
    public void onPvP(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;

        var victimGp   = plugin.getGameManager().getPlayer(victim);
        var attackerGp = plugin.getGameManager().getPlayer(attacker);

        if (victimGp != null && attackerGp != null && victimGp.getTeam() == attackerGp.getTeam()) event.setCancelled(true);
    }

    /** Empêche le flow d'eau pour préserver les maps. */
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
