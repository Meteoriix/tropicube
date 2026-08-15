package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

/** Displays the localized command catalog shared by every Paper backend. */
public final class HelpCommand implements CommandExecutor, TabCompleter {
    private static final List<String> CATEGORIES = List.of("general", "games", "profile", "staff");
    private final TropicubeCore plugin;

    public HelpCommand(TropicubeCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getLanguageManager().getComponentForLang("fr", "general.player-only"));
            return true;
        }
        String category = args.length == 0 ? "general" : args[0].toLowerCase();
        if (!CATEGORIES.contains(category)) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "help.usage"));
            return true;
        }
        if (category.equals("staff") && !player.hasPermission("tropicube.staff")
                && !player.hasPermission("tropicube.admin")) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "general.no-permission"));
            return true;
        }
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "help.header"));
        plugin.getLanguageManager().getList(player.getUniqueId(), "help." + category).stream()
                .map(fr.tropicube.core.util.MessageStyle::component).forEach(player::sendMessage);
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "help.footer"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        return CATEGORIES.stream().filter(category -> !category.equals("staff")
                        || sender.hasPermission("tropicube.staff") || sender.hasPermission("tropicube.admin"))
                .filter(category -> category.startsWith(args[0].toLowerCase())).toList();
    }
}
