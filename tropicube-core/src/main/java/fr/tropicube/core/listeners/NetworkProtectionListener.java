package fr.tropicube.core.listeners;

import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Locale;
import java.util.Set;

/** Applies the network-wide restrictions that must be identical on every Paper backend. */
public final class NetworkProtectionListener implements Listener {
    private static final Set<Material> BLOCKED_CONTAINERS = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST, Material.BARREL,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.CRAFTING_TABLE);

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String input = event.getMessage().substring(1).stripLeading();
        if (input.isEmpty()) return;
        String label = input.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String root = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        if (label.startsWith("minecraft:") || label.startsWith("bukkit:")
                || root.equals("?") || root.equals("bukkit")) {
            event.setCancelled(true);
            return;
        }
        var command = event.getPlayer().getServer().getCommandMap().getCommand(label);
        if (command != null && !(command instanceof PluginCommand)) event.setCancelled(true);
    }

    /** Publishes only clean, non-namespaced commands owned by a Tropicube plugin. */
    @EventHandler
    public void onCommandsSent(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(label -> {
            if (label.indexOf(':') >= 0) return true;
            var command = event.getPlayer().getServer().getCommandMap().getCommand(label);
            return !(command instanceof PluginCommand pluginCommand)
                    || !pluginCommand.getPlugin().getName().startsWith("Tropicube");
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProtectedBlockInteraction(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Material material = event.getClickedBlock().getType();
        String name = material.name();
        if (BLOCKED_CONTAINERS.contains(material) || name.endsWith("_SIGN")
                || name.endsWith("_WALL_SIGN") || name.endsWith("_HANGING_SIGN")
                || name.endsWith("_WALL_HANGING_SIGN")) {
            event.setCancelled(true);
        }
    }

}
