package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.guild.GuildService;
import fr.tropicube.core.guild.GuildPresentation;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Player-facing guild lifecycle and role administration. */
public final class GuildCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public GuildCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) { info(player); return true; }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "invite" -> target(player, args, TargetAction.INVITE);
            case "accept" -> accept(player, args);
            case "leave" -> { run(player, plugin.getGuildService().leave(player.getUniqueId())); yield true; }
            case "kick" -> target(player, args, TargetAction.KICK);
            case "promote" -> target(player, args, TargetAction.PROMOTE);
            case "demote" -> target(player, args, TargetAction.DEMOTE);
            case "transfer" -> target(player, args, TargetAction.TRANSFER);
            case "ranking", "classement" -> { ranking(player); yield true; }
            default -> { usage(player); yield true; }
        };
    }

    private boolean create(Player player, String[] args) {
        if (args.length != 3) { usage(player); return true; }
        run(player, plugin.getGuildService().create(player.getUniqueId(), args[1], args[2]));
        return true;
    }

    private boolean accept(Player player, String[] args) {
        if (args.length != 2) { usage(player); return true; }
        run(player, plugin.getGuildService().accept(player.getUniqueId(), args[1]));
        return true;
    }

    private boolean target(Player player, String[] args, TargetAction action) {
        if (args.length != 2) { usage(player); return true; }
        String language = plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId());
        CommandAsync.run(plugin, player, language,
                () -> plugin.getPlayerDataManager().getUuidByName(args[1]).orElse(null), target -> {
                    if (target == null || target.equals(player.getUniqueId())) {
                        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(),
                                "general.player-not-found", args[1]));
                        return;
                    }
                    var operation = switch (action) {
                        case INVITE -> plugin.getGuildService().invite(player.getUniqueId(), target);
                        case KICK -> plugin.getGuildService().kick(player.getUniqueId(), target);
                        case PROMOTE -> plugin.getGuildService().setRole(player.getUniqueId(), target, GuildService.Role.OFFICER);
                        case DEMOTE -> plugin.getGuildService().setRole(player.getUniqueId(), target, GuildService.Role.MEMBER);
                        case TRANSFER -> plugin.getGuildService().transfer(player.getUniqueId(), target);
                    };
                    UUID actor = player.getUniqueId();
                    operation.thenCompose(result -> {
                        reply(player, result);
                        if (action == TargetAction.INVITE && result == GuildService.Result.SUCCESS) {
                            return plugin.getGuildInvitations().notifyInvitation(actor, target);
                        }
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }).exceptionally(error -> { failure(player, error); return null; });
                });
        return true;
    }

    private void info(Player player) {
        plugin.getGuildService().guild(player.getUniqueId()).thenAccept(guild ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (guild == null) { message(player, "guild.none"); return; }
                    message(player, "guild.info", guild.tag(), guild.name(), guild.level(), guild.experience());
                    for (GuildService.Member member : guild.members()) message(player, "guild.member",
                            plugin.getLanguageManager().get(player.getUniqueId(), GuildPresentation.roleKey(member.role())), member.username(), member.weeklyContribution());
                    for (GuildService.Challenge challenge : guild.challenges()) message(player, "guild.challenge",
                            plugin.getLanguageManager().get(player.getUniqueId(), GuildPresentation.challengeKey(challenge.id())), challenge.progress(), challenge.target(), challenge.completed() ? "✓" : "");
                })).exceptionally(error -> { failure(player, error); return null; });
    }

    private void ranking(Player player) {
        plugin.getGuildService().currentRanking(20).thenAccept(values ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "guild.ranking-header"));
                    for (int index = 0; index < values.size(); index++) {
                        GuildService.Ranking value = values.get(index);
                        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(),
                                "guild.ranking-entry", index + 1, value.tag(), value.name(),
                                Math.round(value.score()), value.rankedMatches()));
                    }
                })).exceptionally(error -> { failure(player, error); return null; });
    }

    private void run(Player player, java.util.concurrent.CompletableFuture<GuildService.Result> operation) {
        operation.whenComplete((result, error) -> {
            if (error != null) failure(player, error); else reply(player, result);
        });
    }
    private void reply(Player player, GuildService.Result result) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) message(player, GuildPresentation.resultKey(result));
        });
    }
    private void failure(Player player, Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.WARNING, "Guild command failed", error);
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) message(player, "general.operation-failed");
        });
    }
    private void usage(Player player) { message(player, "guild.usage"); }
    private void message(Player player, String key, Object... arguments) {
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), key, arguments));
    }
    private enum TargetAction { INVITE, KICK, PROMOTE, DEMOTE, TRANSFER }
}
