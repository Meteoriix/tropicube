package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

// /kick <joueur> [raison]
/** Expulse un joueur et conserve la sanction dans l'historique de modération. */
public class KickCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public KickCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lm = plugin.getLanguageManager();

        if (!sender.hasPermission("tropicube.kick")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }
        if (args.length < 1) { sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.kick-usage")); return true; }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.player-offline"));
            return true;
        }

        String reason = args.length >= 2
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : lm.getForLang("fr", "general.no-reason");

        target.kick(lm.getComponent(target.getUniqueId(), "moderation.kick-message", reason));
        sender.sendMessage(lm.getComponentForLang(lang(sender), "moderation.kick-success", target.getName(), reason));
        return true;
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }
}
