package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.LanguageManager;
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

// /tropiperm <add|remove|list|grade> ...
/** Consulte et modifie les permissions associées aux grades Tropicube. */
public class PermissionCommand implements CommandExecutor, TabCompleter {

    private final TropicubeCore plugin;

    public PermissionCommand(TropicubeCore p) { this.plugin = p; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var lm = plugin.getLanguageManager();
        var pm = plugin.getPermissionManager();

        if (!sender.hasPermission("tropicube.admin.perm")) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "general.no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-add-usage"));
                    return true;
                }
                long expiry = -1;
                if (args.length >= 4) {
                    var parsed = DurationParser.parseSeconds(args[3]);
                    if (parsed.isEmpty()) {
                        sender.sendMessage(lm.getComponentForLang(lang(sender), "general.invalid-number"));
                        return true;
                    }
                    expiry = parsed.getAsLong();
                }
                UUID grantedBy = sender instanceof Player p ? p.getUniqueId() : null;
                String name = args[1], permission = args[2], language = lang(sender);
                long duration = expiry;
                UUID onlineUuid = onlineUuid(name);
                CommandAsync.run(plugin, sender, language, () -> {
                    UUID target = resolveOffline(name, onlineUuid);
                    if (target != null) pm.addPermission(target, permission, duration, grantedBy);
                    return target;
                }, target -> sendTargetResult(sender, lm, language, name, target,
                        "permissions.add-success", permission));
            }
            case "remove" -> {
                if (args.length < 3) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-remove-usage"));
                    return true;
                }
                String name = args[1], permission = args[2], language = lang(sender);
                UUID onlineUuid = onlineUuid(name);
                CommandAsync.run(plugin, sender, language, () -> {
                    UUID target = resolveOffline(name, onlineUuid);
                    if (target != null) pm.removePermission(target, permission);
                    return target;
                }, target -> sendTargetResult(sender, lm, language, name, target,
                        "permissions.remove-success", permission));
            }
            case "list" -> {
                if (args.length < 2) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-usage"));
                    return true;
                }
                String name = args[1], language = lang(sender);
                UUID onlineUuid = onlineUuid(name);
                CommandAsync.run(plugin, sender, language, () -> {
                    UUID target = resolveOffline(name, onlineUuid);
                    if (target == null) return null;
                    return new PermissionView(pm.getGrade(target), pm.getIndividualPermissions(target));
                }, view -> {
                if (view == null) {
                    sender.sendMessage(lm.getComponentForLang(language, "general.player-not-found", name));
                    return;
                }
                sender.sendMessage(lm.getComponentForLang(language, "permissions.list-header", name));
                PermissionManager.Grade gradeObj = pm.getAllGrades().get(view.gradeName());
                if (gradeObj != null) {
                    String gradePerms = gradeObj.permissions().isEmpty() ? "-" : String.join(", ", gradeObj.permissions());
                    sender.sendMessage(lm.getComponentForLang(language, "permissions.list-grade", view.gradeName(), gradePerms));
                }
                Set<String> individual = view.individual();
                if (individual.isEmpty()) {
                    sender.sendMessage(lm.getComponentForLang(language, "permissions.list-none"));
                } else {
                    individual.stream().sorted().forEach(p ->
                            sender.sendMessage(lm.getComponentForLang(language, "permissions.list-individual", p)));
                }
                });
            }
            case "grade" -> handleGrade(sender, args, lm, pm);
            default -> sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-usage"));
        }
        return true;
    }

    private void handleGrade(CommandSender sender, String[] args, LanguageManager lm, PermissionManager pm) {
        if (args.length < 2) {
            sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-grade-usage"));
            return;
        }
        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 4) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-grade-add-usage"));
                    return;
                }
                String gradeName = args[2].toUpperCase();
                if (!pm.getAllGrades().containsKey(gradeName)) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "permissions.grade-invalid", args[2]));
                    return;
                }
                String permission = args[3], language = lang(sender);
                CommandAsync.run(plugin, sender, language, () -> {
                    pm.addGradePermission(gradeName, permission);
                    return true;
                }, ignored -> sender.sendMessage(lm.getComponentForLang(language,
                        "permissions.grade-add-success", permission, gradeName)));
            }
            case "remove" -> {
                if (args.length < 4) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-grade-remove-usage"));
                    return;
                }
                String gradeName = args[2].toUpperCase();
                if (!pm.getAllGrades().containsKey(gradeName)) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "permissions.grade-invalid", args[2]));
                    return;
                }
                String permission = args[3], language = lang(sender);
                CommandAsync.run(plugin, sender, language, () -> {
                    pm.removeGradePermission(gradeName, permission);
                    return true;
                }, ignored -> sender.sendMessage(lm.getComponentForLang(language,
                        "permissions.grade-remove-success", permission, gradeName)));
            }
            case "list" -> {
                if (args.length < 3) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-grade-list-usage"));
                    return;
                }
                String gradeName = args[2].toUpperCase();
                if (!pm.getAllGrades().containsKey(gradeName)) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "permissions.grade-invalid", args[2]));
                    return;
                }
                sender.sendMessage(lm.getComponentForLang(lang(sender), "permissions.grade-list-header", gradeName));
                Set<String> perms = pm.getGradePermissions(gradeName);
                if (perms.isEmpty()) {
                    sender.sendMessage(lm.getComponentForLang(lang(sender), "permissions.grade-list-none"));
                } else {
                    perms.stream().sorted().forEach(p ->
                            sender.sendMessage(lm.getComponentForLang(lang(sender), "permissions.grade-list-entry", p)));
                }
            }
            default -> sender.sendMessage(lm.getComponentForLang(lang(sender), "commands.perm-grade-usage"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("tropicube.admin.perm")) return Collections.emptyList();
        var pm = plugin.getPermissionManager();

        if (args.length == 1) {
            return filter(List.of("add", "remove", "list", "grade"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("grade")) {
                return filter(List.of("add", "remove", "list"), args[1]);
            }
            return filter(plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("grade")) {
            return filter(new ArrayList<>(pm.getAllGrades().keySet()), args[2]);
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("grade")
                && args[1].equalsIgnoreCase("remove")) {
            return filter(new ArrayList<>(pm.getGradePermissions(args[2].toUpperCase())), args[3]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .sorted()
                .collect(Collectors.toList());
    }

    private UUID onlineUuid(String name) {
        Player player = plugin.getServer().getPlayer(name);
        return player == null ? null : player.getUniqueId();
    }

    private UUID resolveOffline(String name, UUID onlineUuid) {
        return onlineUuid != null ? onlineUuid
                : plugin.getPlayerDataManager().getUuidByName(name).orElse(null);
    }

    private void sendTargetResult(CommandSender sender, LanguageManager lm, String language,
                                  String name, UUID target, String successKey, String permission) {
        if (target == null) sender.sendMessage(lm.getComponentForLang(language,
                "general.player-not-found", name));
        else sender.sendMessage(lm.getComponentForLang(language, successKey, permission, name));
    }

    private String lang(CommandSender sender) {
        return sender instanceof Player p
                ? plugin.getLanguageManager().getPlayerLanguage(p.getUniqueId()) : "fr";
    }

    private record PermissionView(String gradeName, Set<String> individual) {}
}
