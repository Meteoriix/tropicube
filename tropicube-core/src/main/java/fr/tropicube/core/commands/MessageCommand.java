package fr.tropicube.core.commands;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.network.NetworkCommunicationService;
import fr.tropicube.core.util.CommandAsync;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.Arrays;
import java.util.UUID;

/** Handles /msg, /reply and /ignore across all Paper backends. */
public final class MessageCommand implements CommandExecutor {
    private final TropicubeCore plugin;

    public MessageCommand(TropicubeCore plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, @NonNull Command command,
                                       @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only");
            return true;
        }
        if (label.equalsIgnoreCase("ignore")) return ignore(player, args);
        if (label.equalsIgnoreCase("reply") || label.equalsIgnoreCase("r")) return reply(player, args);
        return message(player, args);
    }

    private boolean message(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "communication.msg-usage"));
            return true;
        }
        resolveAndSend(player, args[0], String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
        return true;
    }

    private boolean reply(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "communication.reply-usage"));
            return true;
        }
        UUID target = plugin.getCommunicationService().lastConversation(player.getUniqueId());
        if (target == null) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "communication.no-conversation"));
            return true;
        }
        String targetName = plugin.getServer().getOfflinePlayer(target).getName();
        send(player, target, targetName == null ? target.toString() : targetName, String.join(" ", args));
        return true;
    }

    private boolean ignore(Player player, String[] args) {
        if (args.length != 1) {
            player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "communication.ignore-usage"));
            return true;
        }
        String language = plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId());
        CommandAsync.run(plugin, player, language,
                () -> plugin.getPlayerDataManager().getUuidByName(args[0]).orElse(null), target -> {
                    if (target == null || target.equals(player.getUniqueId())) {
                        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "general.player-not-found", args[0]));
                        return;
                    }
                    plugin.getCommunicationService().toggleIgnore(player.getUniqueId(), target)
                            .thenAccept(ignored -> plugin.getServer().getScheduler().runTask(plugin, () ->
                                    player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(),
                                            ignored ? "communication.ignored" : "communication.unignored", args[0]))));
                });
        return true;
    }

    private void resolveAndSend(Player player, String name, String body) {
        String language = plugin.getLanguageManager().getPlayerLanguage(player.getUniqueId());
        CommandAsync.run(plugin, player, language,
                () -> plugin.getPlayerDataManager().getUuidByName(name).orElse(null), target -> {
                    if (target == null || target.equals(player.getUniqueId())) {
                        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(), "general.player-not-found", name));
                        return;
                    }
                    send(player, target, name, body);
                });
    }

    private void send(Player player, UUID target, String targetName, String body) {
        plugin.getCommunicationService().sendPrivate(player.getUniqueId(), player.getName(), target, body)
                .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () ->
                        player.sendMessage(plugin.getLanguageManager().getComponent(player.getUniqueId(),
                                switch (result) {
                                    case SENT -> "communication.sent";
                                    case STORED_OFFLINE -> "communication.stored";
                                    case IGNORED, PRIVACY_BLOCKED -> "communication.blocked";
                                }, targetName))));
    }
}
