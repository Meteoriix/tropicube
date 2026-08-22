package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import fr.tropicube.core.util.DurationParser;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.UUID;

/** Network ban, temporary ban and unban commands backed by MySQL and Redis. */
public final class BanCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public BanCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (!sender.hasPermission("tropicube.ban")) return false;
        if (!StaffSecurityGate.allow(plugin, sender)) return true;
        if (args.length < 1) {
            sender.sendMessage("Usage: /" + label + " <joueur> "
                    + (label.equalsIgnoreCase("tempban") ? "<durée> [raison]" : "[raison]"));
            return true;
        }
        String language = sender instanceof Player player
                ? plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId()) : "fr";
        String targetName = args[0];
        if (label.equalsIgnoreCase("unban")) {
            resolve(sender, language, targetName, target -> plugin.getModerationService().unban(target)
                    .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language,
                                    "moderation.unban-success", targetName)))));
            return true;
        }
        long duration = -1;
        int reasonStart = 1;
        if (label.equalsIgnoreCase("tempban")) {
            if (args.length < 2 || DurationParser.parseSeconds(args[1]).isEmpty()) {
                sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language, "general.invalid-number"));
                return true;
            }
            duration = DurationParser.parseSeconds(args[1]).orElseThrow();
            reasonStart = 2;
        }
        String reason = args.length > reasonStart ? String.join(" ", Arrays.copyOfRange(args, reasonStart, args.length))
                : plugin.getLanguageManager().getForLang(language, "general.no-reason");
        long finalDuration = duration;
        UUID staffId = sender instanceof Player player ? player.getUniqueId() : null;
        resolve(sender, language, targetName, target -> plugin.getModerationService()
                .ban(target, targetName, finalDuration, reason, staffId, sender.getName())
                .thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language,
                                "moderation.ban-success", targetName, reason)))));
        return true;
    }

    private void resolve(CommandSender sender, String language, String name,
                         java.util.function.Consumer<UUID> action) {
        Player online = plugin.getServer().getPlayer(name);
        UUID onlineId = online == null ? null : online.getUniqueId();
        CommandAsync.run(plugin, sender, language, () -> onlineId != null ? onlineId
                : plugin.getPlayerDataManager().getUuidByName(name).orElse(null), target -> {
            if (target == null) sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language,
                    "general.player-not-found", name));
            else action.accept(target);
        });
    }
}
