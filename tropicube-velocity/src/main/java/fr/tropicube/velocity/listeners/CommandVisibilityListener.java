package fr.tropicube.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Locale;
import java.util.Set;

/** Publishes only Tropicube commands and rejects namespaced Bukkit/vanilla escape hatches. */
public final class CommandVisibilityListener {
    private static final Set<String> TROPICUBE_COMMANDS = Set.of(
            "money", "balance", "eco", "rank", "grade", "lang", "language", "langue",
            "mute", "unmute", "kick", "warn", "history", "permissions", "tropiperm",
            "coreadmin", "tropiadmin", "ca", "help", "friend", "friends", "ami", "amis",
            "party", "groupe", "pc", "spawn", "play", "servers", "sv", "languages",
            "vip", "boutique", "shop", "fly", "flymode", "fm", "replay", "playnext",
            "playagain", "rejouer", "replayconfirm", "rejoin", "tropicube", "tropi", "cm",
            "server", "lobby", "hub", "send", "pull", "find", "nick", "queue", "file",
            "whitelist");

    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        event.getRootNode().getChildren().removeIf(child -> !isVisible(child.getName()));
    }

    static boolean isVisible(String command) {
        return command != null && TROPICUBE_COMMANDS.contains(command.toLowerCase(Locale.ROOT));
    }

    @Subscribe
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player)) return;
        String input = event.getCommand().stripLeading();
        if (input.isEmpty()) return;
        String label = input.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        String root = label.contains(":") ? label.substring(label.indexOf(':') + 1) : label;
        if (label.startsWith("minecraft:") || label.startsWith("bukkit:")
                || root.equals("?") || root.equals("bukkit")) {
            event.setResult(CommandExecuteEvent.CommandResult.denied());
        }
    }
}
