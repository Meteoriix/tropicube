package fr.tropicube.fallenkingdoms.listener;

import fr.tropicube.fallenkingdoms.game.GameSession;
import fr.tropicube.fallenkingdoms.game.GameState;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.weather.WeatherChangeEvent;

/** Enforces map-independent territorial rules at Paper event boundaries. */
public final class ProtectionListener implements Listener {
    private final GameSession session;
    public ProtectionListener(GameSession session) { this.session = session; }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void place(BlockPlaceEvent event) {
        if (!session.mayChangeBlock(event.getPlayer(), event.getBlock().getLocation(), event.getBlockPlaced().getType(), true))
            event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void breakBlock(BlockBreakEvent event) {
        if (!session.mayChangeBlock(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType(), false))
            event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void destroy(BlockDestroyEvent event) {
        if (protectsWaitingRoom(session.state())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && protectsWaitingRoom(session.state())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void food(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player) || !protectsWaitingRoom(session.state())) return;
        event.setCancelled(true);
        player.setFoodLevel(20);
        player.setSaturation(20F);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void drop(PlayerDropItemEvent event) {
        if (protectsWaitingRoom(session.state())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void weather(WeatherChangeEvent event) {
        if (event.toWeatherState()) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void move(PlayerMoveEvent event) {
        Location destination = event.getTo();
        if (destination != null && changedBlock(event.getFrom(), destination) && !session.mayEnter(event.getPlayer(), destination)) {
            session.warnEnemyBaseEntry(event.getPlayer(), destination);
            event.setTo(event.getFrom());
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void teleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && !session.mayEnter(event.getPlayer(), event.getTo())) {
            session.warnEnemyBaseEntry(event.getPlayer(), event.getTo());
            event.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void portal(PlayerPortalEvent event) {
        if (event.getTo() == null || !session.mayEnter(event.getPlayer(), event.getTo())) {
            if (event.getTo() != null) session.warnEnemyBaseEntry(event.getPlayer(), event.getTo());
            event.setCancelled(true);
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void pistonExtend(BlockPistonExtendEvent event) {
        for (var block : event.getBlocks()) if (!sameTerritory(block.getLocation(), block.getRelative(event.getDirection()).getLocation())) {
            event.setCancelled(true); return;
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void pistonRetract(BlockPistonRetractEvent event) {
        for (var block : event.getBlocks()) if (!sameTerritory(block.getLocation(), block.getRelative(event.getDirection().getOppositeFace()).getLocation())) {
            event.setCancelled(true); return;
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void fluid(BlockFromToEvent event) {
        if (!sameTerritory(event.getBlock().getLocation(), event.getToBlock().getLocation())) event.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void entityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> !session.mayExplosionChange(block));
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void blockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> !session.mayExplosionChange(block));
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void vehicleMove(VehicleMoveEvent event) {
        Player passenger = event.getVehicle().getPassengers().stream().filter(Player.class::isInstance)
                .map(Player.class::cast).findFirst().orElse(null);
        if (passenger != null && !session.mayEnter(passenger, event.getTo())) {
            session.warnEnemyBaseEntry(passenger, event.getTo());
            event.getVehicle().teleport(event.getFrom());
        }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void gate(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !event.getClickedBlock().getType().name().endsWith("_FENCE_GATE")) return;
        if (!session.mayToggleGate(event.getPlayer(), event.getClickedBlock().getLocation())) event.setCancelled(true);
    }
    private boolean sameTerritory(Location from, Location to) {
        return session.sameProtectionRegion(from,to);
    }
    private static boolean changedBlock(Location from, Location to) {
        return from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ();
    }
    static boolean protectsWaitingRoom(GameState state) {
        return !state.active();
    }
}
