package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.PermissionManager;
import fr.tropicube.core.util.CommandAsync;
import fr.tropicube.docker.model.AccessPolicy;
import fr.tropicube.docker.model.PlayerAccessProfile;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

/** Reads and permanently changes the two cumulative player access levels. */
public final class LevelCommand implements CommandExecutor, TabCompleter {
    private final TropicubeCore plugin;

    public LevelCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String language = language(sender);
        if (args.length != 1 && args.length != 3) {
            sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language, "access.usage"));
            return true;
        }
        Player online = plugin.getServer().getPlayer(args[0]);
        UUID onlineId = online == null ? null : online.getUniqueId();
        if (sender instanceof Player actor) {
            PlayerAccessProfile actorAccess = plugin.getPermissionManager().getCachedAccessProfile(actor.getUniqueId());
            if (!AccessPolicy.canManageVip(actorAccess)) {
                sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language, "general.no-permission"));
                return true;
            }
            if (actor.getUniqueId().equals(onlineId)) {
                sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language, "access.no-self"));
                return true;
            }
        }
        PermissionManager.Axis axis = null;
        int level = -1;
        if (args.length == 3) {
            try {
                axis = PermissionManager.Axis.valueOf(args[1].toUpperCase(Locale.ROOT));
                level = Integer.parseInt(args[2]);
            } catch (IllegalArgumentException error) {
                sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language, "access.invalid"));
                return true;
            }
            int maximum = axis == PermissionManager.Axis.VIP ? 3 : 4;
            if (level < 0 || level > maximum) {
                sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language, "access.invalid"));
                return true;
            }
            if (sender instanceof Player actor && axis == PermissionManager.Axis.MOD
                    && !AccessPolicy.canAssignMod(plugin.getPermissionManager().getCachedAccessProfile(actor.getUniqueId()), level)) {
                sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language, "access.no-escalation"));
                return true;
            }
        }
        PermissionManager.Axis requestedAxis = axis;
        int requestedLevel = level;
        UUID actorId = sender instanceof Player player ? player.getUniqueId() : null;
        String targetName = args[0];
        CommandAsync.run(plugin, sender, language, () -> {
            UUID target = onlineId != null ? onlineId : plugin.getPlayerDataManager().getUuidByName(targetName).orElse(null);
            if (target == null) return null;
            if (requestedAxis != null && !target.equals(actorId))
                plugin.getPermissionManager().setAccessLevel(target, requestedAxis, requestedLevel, actorId);
            return new Result(target, plugin.getPermissionManager().getGrade(target),
                    plugin.getPermissionManager().getAccessProfile(target));
        }, result -> {
            if (result == null) sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language,
                    "general.player-not-found", targetName));
            else if (requestedAxis != null && result.uuid().equals(actorId))
                sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language, "access.no-self"));
            else sender.sendMessage(plugin.getLanguageManager().getComponentForLang(language,
                    requestedAxis == null ? "access.info" : "access.changed", targetName, result.grade(),
                    result.profile().vipLevel(), result.profile().modLevel()));
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).sorted().toList();
        if (args.length == 2) return List.of("vip", "mod").stream().filter(v -> v.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 3) {
            int maximum = args[1].equalsIgnoreCase("vip") ? 3
                    : sender instanceof Player player ? Math.max(0, plugin.getPermissionManager().getModLevel(player.getUniqueId()) - 1) : 4;
            List<String> values = new ArrayList<>();
            for (int i = 0; i <= maximum; i++) values.add(Integer.toString(i));
            return values.stream().filter(value -> value.startsWith(args[2])).toList();
        }
        return List.of();
    }

    private String language(CommandSender sender) {
        return sender instanceof Player player
                ? plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId()) : "fr";
    }

    private record Result(UUID uuid, String grade, PlayerAccessProfile profile) {}
}
