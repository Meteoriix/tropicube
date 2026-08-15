package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

import java.util.List;

/** Pulls an online player to the backend currently used by a staff member. */
public final class PullCommand implements SimpleCommand {
    private static final String PERMISSION = "tropicube.admin.pull";

    private final ProxyServer proxy;
    private final TropiServerManager manager;
    private final VelocityLanguageManager languages;

    public PullCommand(ProxyServer proxy, TropiServerManager manager, VelocityLanguageManager languages) {
        this.proxy = proxy;
        this.manager = manager;
        this.languages = languages;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player source)) {
            invocation.source().sendMessage(languages.getComponent(invocation.source(), "general.player-only"));
            return;
        }
        if (!source.hasPermission(PERMISSION)) {
            source.sendMessage(languages.getComponent(source, "general.no-permission"));
            return;
        }
        if (invocation.arguments().length != 1) {
            source.sendMessage(languages.getComponent(source, "proxy.pull-usage"));
            return;
        }
        String targetName = invocation.arguments()[0];
        proxy.getPlayer(targetName).ifPresentOrElse(target -> pull(source, target),
                () -> source.sendMessage(languages.getComponent(source, "proxy.pull-player-not-found", targetName)));
    }

    private void pull(Player source, Player target) {
        if (source.getUniqueId().equals(target.getUniqueId())) {
            source.sendMessage(languages.getComponent(source, "proxy.pull-self"));
            return;
        }
        var current = source.getCurrentServer();
        if (current.isEmpty()) {
            source.sendMessage(languages.getComponent(source, "proxy.pull-no-server"));
            return;
        }
        String serverName = current.get().getServerInfo().getName();
        if (target.getCurrentServer().map(connection -> connection.getServerInfo().getName()
                .equalsIgnoreCase(serverName)).orElse(false)) {
            source.sendMessage(languages.getComponent(source, "proxy.pull-already-here", target.getUsername()));
            return;
        }
        var instance = manager.getInstanceByName(serverName);
        if (instance.isPresent() && !instance.get().isJoinable(target.getUniqueId())) {
            source.sendMessage(languages.getComponent(source, "proxy.pull-unavailable", target.getUsername()));
            return;
        }
        target.createConnectionRequest(current.get().getServer()).connect().whenComplete((result, error) -> {
            if (error != null || result == null || !result.isSuccessful()) {
                source.sendMessage(languages.getComponent(source, "proxy.transfer-failed"));
                return;
            }
            source.sendMessage(languages.getComponent(source, "proxy.pull-success", target.getUsername(), serverName));
            target.sendMessage(languages.getComponent(target.getUniqueId(), "proxy.pull-received",
                    source.getUsername(), serverName));
        });
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (!hasPermission(invocation) || invocation.arguments().length > 1) return List.of();
        String prefix = invocation.arguments().length == 0 ? "" : invocation.arguments()[0];
        return proxy.getAllPlayers().stream().map(Player::getUsername)
                .filter(name -> name.regionMatches(true, 0, prefix, 0, prefix.length()))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission(PERMISSION);
    }
}
