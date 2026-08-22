package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.guild.GuildService;
import fr.tropicube.core.util.CommandAsync;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
                    operation.thenAccept(result -> {
                        reply(player, result);
                        if (action == TargetAction.INVITE && result == GuildService.Result.SUCCESS) {
                            plugin.getNotificationService().create(target, "GUILD", "guild.invitation",
                                    List.of(player.getName()), null,
                                    System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000);
                        }
                    });
                });
        return true;
    }

    private void info(Player player) {
        plugin.getGuildService().guild(player.getUniqueId()).thenAccept(guild ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (guild == null) { player.sendMessage(Component.text("Tu n'appartiens à aucune guilde.", NamedTextColor.RED)); return; }
                    player.sendMessage(Component.text("[" + guild.tag() + "] " + guild.name()
                            + " • niveau " + guild.level() + " • " + guild.experience() + " XP", NamedTextColor.GOLD));
                    for (GuildService.Member member : guild.members()) player.sendMessage(Component.text(
                            member.role() + " • " + member.username() + " • contribution "
                                    + member.weeklyContribution(), NamedTextColor.GRAY));
                    for (GuildService.Challenge challenge : guild.challenges()) player.sendMessage(Component.text(
                            "Défi " + challenge.id() + " • " + challenge.progress() + "/" + challenge.target()
                                    + (challenge.completed() ? " ✓" : ""), NamedTextColor.AQUA));
                }));
    }

    private void run(Player player, java.util.concurrent.CompletableFuture<GuildService.Result> operation) {
        operation.thenAccept(result -> reply(player, result));
    }
    private void reply(Player player, GuildService.Result result) {
        plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage(Component.text(
                "Guilde : " + result, result == GuildService.Result.SUCCESS ? NamedTextColor.GREEN : NamedTextColor.RED)));
    }
    private void usage(Player player) { player.sendMessage(Component.text(
            "/guild info|create <nom> <tag>|invite|accept|leave|kick|promote|demote|transfer", NamedTextColor.YELLOW)); }
    private enum TargetAction { INVITE, KICK, PROMOTE, DEMOTE, TRANSFER }
}
