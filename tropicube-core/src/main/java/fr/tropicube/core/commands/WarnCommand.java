package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.UUID;

// /warn <joueur> <raison>
/** Enregistre un avertissement de modération à l'encontre d'un joueur. */
public class WarnCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    private static final int MAX_WARNS = 3;
    public WarnCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lm = plugin.getLanguageManager();

        if (!sender.hasPermission("tropicube.warn")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }
        if (args.length < 2) { sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.warn-usage")); return true; }

        Player target = plugin.getServer().getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.player-offline"));
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;

        UUID targetUuid = target.getUniqueId();
        String targetName = target.getName();
        String staffName = sender.getName();
        String language = lang(sender);
        CommandAsync.run(plugin, sender, language, () -> {
            plugin.getPlayerDataManager().addWarn(targetUuid, reason, staffUuid, staffName);
            return plugin.getPlayerDataManager().getWarnCount(targetUuid);
        }, warns -> {
            sender.sendMessage(lm.getComponentForLang(language, "moderation.warn-success",
                    targetName, reason, warns, MAX_WARNS));
            Player currentTarget = plugin.getServer().getPlayer(targetUuid);
            if (currentTarget == null) return;
            currentTarget.sendMessage(lm.getComponent(targetUuid, "moderation.warn-received",
                    reason, warns, MAX_WARNS));
            if (warns >= MAX_WARNS)
                currentTarget.kick(lm.getComponent(targetUuid, "moderation.warn-kick", MAX_WARNS));
        });
        return true;
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }
}
