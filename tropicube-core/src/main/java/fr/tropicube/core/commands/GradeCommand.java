package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.PermissionManager;
import fr.tropicube.core.util.DurationParser;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

// /rank <set|info|list> [joueur] [grade] [durée]
/** Administre les grades, leurs priorités et leur attribution aux joueurs. */
public class GradeCommand implements CommandExecutor, TabCompleter {

    private final TropicubeCore plugin;

    public GradeCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lm = plugin.getLanguageManager();
        var pm = plugin.getPermissionManager();

        if (!sender.hasPermission("tropicube.grade.admin")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.grade-usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "list" -> {
                sender.sendMessage(lm.getComponentForLang(lang(sender), "grade.list-header"));
                pm.getAllGrades().values().stream()
                        .sorted(Comparator.comparingInt(PermissionManager.Grade::priority))
                        .forEach(g -> sender.sendMessage(lm.getComponentForLang(lang(sender), "grade.list-entry",
                                g.prefix(), g.displayName(), g.priority())));
            }
            case "set" -> {
                if (args.length < 3) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.grade-set-usage"));
                    return true;
                }
                if (!pm.getAllGrades().containsKey(args[2].toUpperCase())) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "grade.invalid", args[2]));
                    return true;
                }
                if (sender instanceof Player sPlayer) {
                    int senderPrio = pm.getPriority(sPlayer.getUniqueId());
                    int gradePrio  = pm.getAllGrades().get(args[2].toUpperCase()).priority();
                    if (gradePrio >= senderPrio && !sender.isOp()) {
                        sender.sendMessage(lm.getComponentForLang(lang(sender), "grade.no-downgrade"));
                        return true;
                    }
                }
                long duration = -1;
                if (args.length >= 4) {
                    var parsed = DurationParser.parseSeconds(args[3]);
                    if (parsed.isEmpty()) {
                        sender.sendMessage(lm.getComponentForLang(lang(sender), "general.invalid-number"));
                        return true;
                    }
                    duration = parsed.getAsLong();
                }
                String targetName = args[1];
                Player online = plugin.getServer().getPlayer(targetName);
                UUID onlineUuid = online == null ? null : online.getUniqueId();
                String gradeName = args[2].toUpperCase();
                String language = lang(sender);
                long gradeDuration = duration;
                var g = pm.getAllGrades().get(args[2].toUpperCase());
                CommandAsync.run(plugin, sender, language, () -> {
                    UUID target = onlineUuid != null ? onlineUuid
                            : plugin.getPlayerDataManager().getUuidByName(targetName).orElse(null);
                    if (target != null) pm.setGrade(target, gradeName, gradeDuration);
                    return target;
                }, target -> {
                    if (target == null) sender.sendMessage(lm.getComponentForLang(language,
                            "general.player-not-found", targetName));
                    else sender.sendMessage(lm.getComponentForLang(language, "grade.set-success",
                            targetName, g.prefix() + g.displayName()));
                });
            }
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.grade-info-usage"));
                    return true;
                }
                String targetName = args[1];
                Player online = plugin.getServer().getPlayer(targetName);
                UUID onlineUuid = online == null ? null : online.getUniqueId();
                String language = lang(sender);
                CommandAsync.run(plugin, sender, language, () -> {
                    UUID target = onlineUuid != null ? onlineUuid
                            : plugin.getPlayerDataManager().getUuidByName(targetName).orElse(null);
                    return target == null ? null : pm.getGradeInfo(target);
                }, g -> {
                    if (g == null) {
                        sender.sendMessage(lm.getComponentForLang(language,
                                "general.player-not-found", targetName));
                        return;
                    }
                    sender.sendMessage(lm.getComponentForLang(language, "commands.grade-info-grade",
                            targetName, g.prefix() + g.displayName()));
                    String yes = lm.getForLang(language, "commands.grade-info-yes");
                    String no  = lm.getForLang(language, "commands.grade-info-no");
                    sender.sendMessage(lm.getComponentForLang(language, "commands.grade-info-flags",
                            g.isVip() ? yes : no, g.isStaff() ? yes : no));
                });
            }
            default -> sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.grade-usage"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("tropicube.grade.admin")) return Collections.emptyList();
        var pm = plugin.getPermissionManager();

        if (args.length == 1) {
            return filter(List.of("set", "info", "list"), args[0]);
        }
        if (args.length == 2 && !args[0].equalsIgnoreCase("list")) {
            return filter(plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            return filter(new ArrayList<>(pm.getAllGrades().keySet()), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }
}
