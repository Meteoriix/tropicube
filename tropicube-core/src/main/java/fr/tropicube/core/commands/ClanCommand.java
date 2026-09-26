package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.clan.ClanService;
import fr.tropicube.core.clan.ClanPresentation;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Player-facing clan lifecycle and role administration. */
public final class ClanCommand implements CommandExecutor {
    private final TropicubeCore plugin;
    public ClanCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) return false;
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) { info(player); return true; }
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> create(player, args);
            case "invite" -> target(player, args, TargetAction.INVITE);
            case "accept" -> accept(player, args);
            case "leave" -> { run(player, plugin.getClanService().leave(player.getUniqueId())); yield true; }
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
        run(player, plugin.getClanService().create(player.getUniqueId(), args[1], args[2]));
        return true;
    }

    private boolean accept(Player player, String[] args) {
        if (args.length != 2) { usage(player); return true; }
        run(player, plugin.getClanService().accept(player.getUniqueId(), args[1]));
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
                        case INVITE -> plugin.getClanService().invite(player.getUniqueId(), target);
                        case KICK -> plugin.getClanService().kick(player.getUniqueId(), target);
                        case PROMOTE -> plugin.getClanService().setRole(player.getUniqueId(), target, ClanService.Role.OFFICER);
                        case DEMOTE -> plugin.getClanService().setRole(player.getUniqueId(), target, ClanService.Role.MEMBER);
                        case TRANSFER -> plugin.getClanService().transfer(player.getUniqueId(), target);
                    };
                    UUID actor = player.getUniqueId();
                    operation.thenCompose(result -> {
                        reply(player, result);
                        if (action == TargetAction.INVITE && result == ClanService.Result.SUCCESS) {
                            return plugin.getClanInvitations().notifyInvitation(actor, target);
                        }
                        return java.util.concurrent.CompletableFuture.completedFuture(null);
                    }).exceptionally(error -> { failure(player, error); return null; });
                });
        return true;
    }

    private void info(Player player) {
        plugin.getClanService().clan(player.getUniqueId()).thenAccept(clan ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (clan == null) { message(player, "clan.none"); return; }
                    message(player, "clan.info", clan.tag(), clan.name(), clan.level(), clan.experience());
                    for (ClanService.Member member : clan.members()) message(player, "clan.member",
                            plugin.getLanguageManager().get(player.getUniqueId(), ClanPresentation.roleKey(member.role())), member.username(), member.weeklyContribution());
                    for (ClanService.Challenge challenge : clan.challenges()) message(player, "clan.challenge",
                            plugin.getLanguageManager().get(player.getUniqueId(), ClanPresentation.challengeKey(challenge.id())), challenge.progress(), challenge.target(), challenge.completed() ? "✓" : "");
                })).exceptionally(error -> { failure(player, error); return null; });
    }

    private void ranking(Player player) {
        plugin.getClanService().currentRanking(20).thenAccept(values ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "clan.ranking-header"));
                    for (int index = 0; index < values.size(); index++) {
                        ClanService.Ranking value = values.get(index);
                        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(),
                                "clan.ranking-entry", index + 1, value.tag(), value.name(),
                                Math.round(value.score()), value.rankedMatches()));
                    }
                })).exceptionally(error -> { failure(player, error); return null; });
    }

    private void run(Player player, java.util.concurrent.CompletableFuture<ClanService.Result> operation) {
        operation.whenComplete((result, error) -> {
            if (error != null) failure(player, error); else reply(player, result);
        });
    }
    private void reply(Player player, ClanService.Result result) {
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) message(player, ClanPresentation.resultKey(result));
        });
    }
    private void failure(Player player, Throwable error) {
        plugin.getLogger().log(java.util.logging.Level.WARNING, "Clan command failed", error);
        if (plugin.isEnabled()) plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) message(player, "general.operation-failed");
        });
    }
    private void usage(Player player) { message(player, "clan.usage"); }
    private void message(Player player, String key, Object... arguments) {
        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), key, arguments));
    }
    private enum TargetAction { INVITE, KICK, PROMOTE, DEMOTE, TRANSFER }
}
