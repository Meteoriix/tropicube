package fr.tropicube.lobby.listeners;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.weather.WeatherChangeEvent;

import java.util.Set;

/**
 * Protège le lobby : pas de PvP, pas de faim, pas de dégâts, pas de construction,
 * pas d'interactions avec les conteneurs ou portails.
 */
public class LobbyProtectionListener implements Listener {

    private static final Set<Material> BLOCKED_INTERACTIONS = Set.of(
        Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
        Material.ENDER_CHEST,
        Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
        Material.CRAFTING_TABLE,
        Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
        Material.ENCHANTING_TABLE,
        Material.BEACON,
        Material.GRINDSTONE, Material.SMITHING_TABLE, Material.STONECUTTER,
        Material.LOOM, Material.CARTOGRAPHY_TABLE, Material.FLETCHING_TABLE,
        Material.HOPPER, Material.DROPPER, Material.DISPENSER,
        Material.SHULKER_BOX,
        // All 16 colored shulker boxes handled below via startsWith
        Material.DRAGON_EGG
    );

    private final TropicubeLobby plugin;

    public LobbyProtectionListener(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player) {
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return;
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player || e.getEntity() instanceof Player)
            e.setCancelled(true);
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player player) {
            e.setCancelled(true);
            player.setFoodLevel(20);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!e.getPlayer().hasPermission("tropicube.lobby.build")) e.setCancelled(true);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!e.getPlayer().hasPermission("tropicube.lobby.build")) e.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (!e.getPlayer().hasPermission("tropicube.lobby.drop")) e.setCancelled(true);
    }

    @EventHandler
    public void onPickup(PlayerAttemptPickupItemEvent e) {
        if (!e.getPlayer().hasPermission("tropicube.lobby.pickup")) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (GuiClickListener.isOurGui(e.getInventory().getHolder())) return;
        e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (GuiClickListener.isOurGui(e.getInventory().getHolder())) return;
        e.setCancelled(true);
    }

    /** Blocks interaction with containers, portals, anvils, enchanting tables, etc. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteractBlock(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Material type = e.getClickedBlock().getType();
        if (isBlocked(type)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onPortalCreate(org.bukkit.event.world.PortalCreateEvent e) {
        // Empêche la création de portails du Nether ou de l'End dans le lobby.
        e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerPortal(PlayerPortalEvent e) {
        // Prevent all dimension travel in lobby
        e.setCancelled(true);
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent e) {
        if (e.toWeatherState()) e.setCancelled(true);
    }

    @EventHandler
    public void onPlayerVoid(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        e.setCancelled(true);
        if (plugin.getPlayerLobbyListener().teleportToSpawn(player)) {
            player.sendMessage(LangHelper.component(player, "lobby.void-teleport"));
        }
    }

    @EventHandler
    public void disableRedstone(BlockRedstoneEvent e) {
        e.setNewCurrent(0);
    }

    private boolean isBlocked(Material type) {
        if (BLOCKED_INTERACTIONS.contains(type)) return true;
        // Colored shulker boxes
        String name = type.name();
        if (name.endsWith("_SHULKER_BOX") || name.endsWith("_DOOR") || name.endsWith("_GATE")) return true;
        // Nether portal block
        return type == Material.NETHER_PORTAL || type == Material.END_PORTAL;
    }
}
