package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.DurationParser;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.*;

// /mute <joueur> [durée] [raison]   /unmute <joueur>
/** Mutualise les commandes de mise en sourdine et de levée de sourdine. */
public class MuteCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public MuteCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, @NonNull Command cmd, @NonNull String label, String @NonNull [] args) {
        var lm = plugin.getLanguageManager();

        if (!sender.hasPermission("tropicube.mute")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }

        if (label.equalsIgnoreCase("unmute")) {
            if (args.length < 1) { sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.unmute-usage")); return true; }
            String language = lang(sender);
            String name = args[0];
            Player online = plugin.getServer().getPlayer(name);
            UUID onlineUuid = online == null ? null : online.getUniqueId();
            CommandAsync.run(plugin, sender, language, () -> {
                UUID uuid = onlineUuid != null ? onlineUuid
                        : plugin.getPlayerDataManager().getUuidByName(name).orElse(null);
                if (uuid != null) plugin.getPlayerDataManager().unmute(uuid);
                return uuid;
            }, uuid -> {
                if (uuid == null) {
                    sender.sendMessage(lm.getComponentForLang(language, "general.player-not-found", name));
                    return;
                }
                sender.sendMessage(lm.getComponentForLang(language, "moderation.unmute-success", name));
                Player target = plugin.getServer().getPlayer(uuid);
                if (target != null)
                    target.sendMessage(lm.getComponent(uuid, "moderation.unmute-received"));
            });
            return true;
        }

        if (args.length < 1) { sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.mute-usage")); return true; }

        long duration = -1;
        if (args.length >= 2) {
            var parsed = DurationParser.parseSeconds(args[1]);
            if (parsed.isEmpty()) {
                sender.sendMessage(lm.getComponentForLang(lang(sender), "general.invalid-number"));
                return true;
            }
            duration = parsed.getAsLong();
        }
        long parsedDuration = duration;

        String language = lang(sender);
        String name = args[0];
        Player online = plugin.getServer().getPlayer(name);
        UUID onlineUuid = online == null ? null : online.getUniqueId();
        UUID staffUuid = sender instanceof Player p ? p.getUniqueId() : null;
        String staffName = sender.getName();
        String reason = args.length >= 3
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length))
                : lm.getForLang(language, "general.no-reason");
        CommandAsync.run(plugin, sender, language, () -> {
            UUID uuid = onlineUuid != null ? onlineUuid
                    : plugin.getPlayerDataManager().getUuidByName(name).orElse(null);
            if (uuid != null)
                plugin.getPlayerDataManager().mutePlayer(uuid, parsedDuration, reason, staffUuid, staffName);
            return uuid;
        }, uuid -> {
            if (uuid == null) {
                sender.sendMessage(lm.getComponentForLang(language, "general.player-not-found", name));
                return;
            }
            String durationStr = formatDuration(parsedDuration, language);
            sender.sendMessage(lm.getComponentForLang(language, "moderation.mute-success",
                    name, durationStr, reason));
            Player target = plugin.getServer().getPlayer(uuid);
            if (target != null)
                target.sendMessage(lm.getComponent(uuid, "moderation.mute-received", durationStr, reason));
        });
        return true;
    }

    private String formatDuration(long seconds, String language) {
        if (seconds <= 0) return plugin.getLanguageManager().getForLang(language, "time.permanent");
        if (seconds < 60)    return plugin.getLanguageManager().getForLang(language, "time.seconds", seconds);
        if (seconds < 3600)  return plugin.getLanguageManager().getForLang(language, "time.minutes", seconds / 60);
        if (seconds < 86400) return plugin.getLanguageManager().getForLang(language, "time.hours", seconds / 3600);
        return plugin.getLanguageManager().getForLang(language, "time.days", seconds / 86400);
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }
}
