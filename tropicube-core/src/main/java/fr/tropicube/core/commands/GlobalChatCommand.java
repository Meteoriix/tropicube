package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

/** Explicit global channel used when a game changes the default chat channel. */
public final class GlobalChatCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public GlobalChatCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) return false;
        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "communication.global-usage"));
            return true;
        }
        plugin.getCommunicationService().publishGlobal(player, String.join(" ", args));
        return true;
    }
}
