package fr.tropicube.velocity.listeners;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.proxy.Player;

import java.util.Locale;

/** Hides the proxy command tree and rejects namespaced Bukkit/vanilla escape hatches. */
public final class CommandVisibilityListener {
    @Subscribe
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        event.getRootNode().getChildren().clear();
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
