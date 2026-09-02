package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Internal console-only bridge used by the local language editor after synchronizing files. */
public final class LanguageEditorReloadCommand implements CommandExecutor {
    private final TropicubeCore plugin;

    public LanguageEditorReloadCommand(TropicubeCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
        if (sender instanceof Player) return true;
        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
            plugin.getLanguageManager().reloadFiles();
            plugin.getLogger().info("TROPICUBE > LANG > Fichiers de langue rechargés par l'éditeur local.");
        });
        sender.sendMessage("Rechargement des langues programmé.");
        return true;
    }
}
