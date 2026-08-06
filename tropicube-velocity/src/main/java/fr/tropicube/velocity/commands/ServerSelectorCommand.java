package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.velocity.TropicubeVelocity;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

// ============================================================
//  /server [nom] — Voir les serveurs ou se connecter
// ============================================================
/** Liste les instances connues et connecte le joueur à celle demandée. */
public class ServerSelectorCommand implements SimpleCommand {
    private final TropicubeVelocity plugin;
    private final TropiServerManager manager;
    private final VelocityLanguageManager lm;

    public ServerSelectorCommand(TropicubeVelocity plugin, TropiServerManager manager, VelocityLanguageManager lm) {
        this.plugin = plugin;
        this.manager = manager;
        this.lm = lm;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length == 0) {
            source.sendMessage(lm.getComponent(source, "proxy.servers-header"));
            manager.getActiveInstances().values().stream()
                    .filter(i -> i.getStatus() == ServerInstance.Status.GAME_WAITING)
                    .forEach(i -> source.sendMessage(lm.getComponent(source, "proxy.servers-entry",
                            i.getServerName(), i.getServerType(),
                            i.getOnlinePlayers(), i.getMaxPlayers())));
            source.sendMessage(lm.getComponent(source, "proxy.servers-usage"));
            return;
        }

        if (!(source instanceof Player player)) {
            source.sendMessage(lm.getComponent(source, "general.player-only"));
            return;
        }

        manager.getInstanceByName(args[0]).ifPresentOrElse(
                instance -> {
                    if (!instance.isJoinable()) {
                        player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.server-unavailable",
                                instance.getStatus()));
                        return;
                    }
                    plugin.getServer().getServer(instance.getServerName()).ifPresentOrElse(srv -> {
                                player.createConnectionRequest(srv).connect().whenComplete((result, error) -> {
                                    if (error != null || result == null || !result.isSuccessful()) {
                                        player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.transfer-failed"));
                                        return;
                                    }
                                    player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.server-connecting",
                                            instance.getServerName()));
                                });
                            },
                            () -> player.sendMessage(lm.getComponent(player.getUniqueId(),
                                    "proxy.server-unavailable", instance.getStatus())));
                },
                () -> player.sendMessage(lm.getComponent(player.getUniqueId(), "proxy.server-not-found", args[0]))
        );
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return manager.getActiveInstances().values().stream()
                    .filter(i -> i.getStatus() == ServerInstance.Status.GAME_WAITING)
                    .map(ServerInstance::getServerName)
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
