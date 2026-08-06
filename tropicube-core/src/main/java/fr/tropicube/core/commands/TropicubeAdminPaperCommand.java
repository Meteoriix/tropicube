package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// /tropiadmin <reload|info>
/** Fournit les opérations d'administration locale de TropicubeCore. */
public class TropicubeAdminPaperCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public TropicubeAdminPaperCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lm = plugin.getLanguageManager();

        if (!sender.hasPermission("tropicube.admin")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.tropiadmin-usage"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload" -> {
                plugin.reloadConfig();
                lm.reload();
                plugin.getEconomyManager().reloadConfiguration();
                String language = lang(sender);
                CommandAsync.run(plugin, sender, language, () -> {
                    plugin.getPermissionManager().reload();
                    return true;
                }, ignored -> sender.sendMessage(lm.getComponentForLang(language,
                        "commands.tropiadmin-reloaded")));
            }
            case "info" -> {
                sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.tropiadmin-info-header"));
                sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.tropiadmin-info-players",
                        plugin.getServer().getOnlinePlayers().size()));
                sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.tropiadmin-info-db",
                        plugin.getDatabaseManager().isConnected()));
                sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.tropiadmin-info-langs",
                        lm.getLanguages().size()));
                sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.tropiadmin-info-grades",
                        plugin.getPermissionManager().getAllGrades().size()));
            }
            default -> sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.tropiadmin-usage"));
        }
        return true;
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }
}
