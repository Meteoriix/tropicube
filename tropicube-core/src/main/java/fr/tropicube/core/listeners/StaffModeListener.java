package fr.tropicube.core.listeners;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

/** Prevents a hidden staff spectator from influencing a live server. */
@SuppressWarnings("deprecation")
public final class StaffModeListener implements Listener {
    private final TropicubeCore plugin;
    public StaffModeListener(TropicubeCore plugin) { this.plugin = plugin; }

    @EventHandler public void interact(PlayerInteractEvent event) {
        if (active(event.getPlayer()) && event.getAction() != Action.PHYSICAL) event.setCancelled(true);
    }
    @EventHandler public void interactEntity(PlayerInteractEntityEvent event) {
        if (active(event.getPlayer())) event.setCancelled(true);
    }
    @EventHandler public void damage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && active(player)) event.setCancelled(true);
    }
    @EventHandler public void drop(PlayerDropItemEvent event) { if (active(event.getPlayer())) event.setCancelled(true); }
    @EventHandler public void pickup(PlayerPickupItemEvent event) { if (active(event.getPlayer())) event.setCancelled(true); }

    private boolean active(Player player) {
        return plugin.isStaffMode(player.getUniqueId());
    }
}
