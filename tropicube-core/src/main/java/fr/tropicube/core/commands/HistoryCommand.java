package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;

// /history <joueur>
/** Affiche l'historique des sanctions d'un joueur. */
public class HistoryCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public HistoryCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lm = plugin.getLanguageManager();

        if (!sender.hasPermission("tropicube.history")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }
        if (args.length < 1) { sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.history-usage")); return true; }

        String language = lang(sender);
        String name = args[0];
        CommandAsync.run(plugin, sender, language, () ->
                plugin.getPlayerDataManager().getUuidByName(name)
                        .map(plugin.getPlayerDataManager()::getSanctionHistory).orElse(null), history -> {
            if (history == null) {
                sender.sendMessage(lm.getComponentForLang(language, "general.player-not-found", name));
                return;
            }
            sender.sendMessage(lm.getComponentForLang(language, "moderation.history-header", name));
            if (history.isEmpty()) {
                sender.sendMessage(lm.getComponentForLang(language, "moderation.history-empty"));
            } else {
                var fmt = new SimpleDateFormat("dd/MM/yy HH:mm");
                history.forEach(entry -> {
                    String date = fmt.format(new Date(((Number) entry.get("timestamp")).longValue() * 1000));
                    sender.sendMessage(lm.getComponentForLang(language, "moderation.history-entry",
                            entry.get("type"), date, entry.get("reason"), entry.get("staff")));
                });
            }
        });
        return true;
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }
}
