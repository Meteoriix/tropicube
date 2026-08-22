package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/** Player reporting and staff queue workflow. */
public final class ReportCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public ReportCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (label.equalsIgnoreCase("reports")) return staff(sender, args);
        if (!(sender instanceof Player player)) return false;
        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "moderation.report-usage"));
            return true;
        }
        String category = args[1].toUpperCase(Locale.ROOT);
        if (!java.util.Set.of("CHAT", "CHEAT", "BEHAVIOR", "OTHER").contains(category)) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "moderation.report-category"));
            return true;
        }
        String messageId = args.length >= 3 && args[2].startsWith("#") ? args[2].substring(1) : null;
        int detailStart = messageId == null ? 2 : 3;
        String details = args.length > detailStart ? String.join(" ", Arrays.copyOfRange(args, detailStart, args.length)) : "";
        String language = plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId());
        CommandAsync.run(plugin, player, language,
                () -> plugin.getPlayerDataManager().getUuidByName(args[0]).orElse(null), target -> {
                    if (target == null || target.equals(player.getUniqueId())) {
                        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "general.player-not-found", args[0]));
                        return;
                    }
                    String instance = System.getenv().getOrDefault("INSTANCE_ID", plugin.getServer().getName());
                    plugin.getModerationService().report(player.getUniqueId(), target, category, details, instance, messageId)
                            .thenAccept(id -> plugin.getServer().getScheduler().runTask(plugin, () ->
                                    player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(),
                                            "moderation.report-created", id))))
                            .exceptionally(error -> {
                                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(
                                        plugin.getLanguageManager().getComponent(player.getUniqueId(), "moderation.report-failed")));
                                return null;
                            });
                });
        return true;
    }

    private boolean staff(CommandSender sender, String[] args) {
        if (!sender.hasPermission("tropicube.reports.manage")) return false;
        if (!StaffSecurityGate.allow(plugin, sender)) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            plugin.getModerationService().openReports(20).thenAccept(reports ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("Signalements ouverts: " + reports.size());
                        reports.forEach(report -> sender.sendMessage("#" + report.id() + " " + report.category()
                                + " cible=" + report.targetId() + " statut=" + report.status()));
                    }));
            return true;
        }
        if (!(sender instanceof Player staff) || args.length < 2) return false;
        long id;
        try { id = Long.parseLong(args[1]); }
        catch (NumberFormatException error) { sender.sendMessage("ID invalide"); return true; }
        if (args[0].equalsIgnoreCase("claim")) {
            plugin.getModerationService().claim(id, staff.getUniqueId())
                    .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(ok ? "Signalement pris en charge." : "Signalement indisponible.")));
        } else if (args[0].equalsIgnoreCase("resolve") && args.length >= 3) {
            plugin.getModerationService().resolve(id, staff.getUniqueId(),
                            String.join(" ", Arrays.copyOfRange(args, 2, args.length)))
                    .thenAccept(ok -> plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(ok ? "Signalement résolu." : "Signalement indisponible.")));
        } else sender.sendMessage("Usage: /reports list|claim <id>|resolve <id> <résolution>");
        return true;
    }
}
