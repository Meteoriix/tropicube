package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import fr.tropicube.velocity.managers.AccessProfileCache;
import fr.tropicube.velocity.managers.QueueManager;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

import java.util.List;

/** Adds a player to the queue of a full instance. */
public class QueueCommand implements SimpleCommand {

    private final TropiServerManager serverManager;
    private final QueueManager queueManager;
    private final AccessProfileCache accessProfiles;
    private final VelocityLanguageManager languageManager;

    public QueueCommand(TropiServerManager serverManager, QueueManager queueManager,
                        AccessProfileCache accessProfiles, VelocityLanguageManager languageManager) {
        this.serverManager = serverManager;
        this.queueManager = queueManager;
        this.accessProfiles = accessProfiles;
        this.languageManager = languageManager;
    }

    @Override
    public void execute(Invocation invocation) {
        if (!(invocation.source() instanceof Player player)) {
            invocation.source().sendMessage(languageManager.getComponent(invocation.source(), "general.player-only"));
            return;
        }
        String[] args = invocation.arguments();
        if (args.length != 1) {
            player.sendMessage(languageManager.getComponent(player.getUniqueId(), "proxy.queue-usage"));
            return;
        }

        serverManager.getInstanceByName(args[0]).ifPresentOrElse(instance -> {
            boolean queueableStatus = instance.getStatus() == fr.tropicube.docker.model.ServerInstance.Status.GAME_WAITING
                    || instance.getStatus() == fr.tropicube.docker.model.ServerInstance.Status.GAME_STARTING
                    || instance.getStatus() == fr.tropicube.docker.model.ServerInstance.Status.GAME_PLAYING;
            if (!queueableStatus || instance.isWhitelisted()) {
                player.sendMessage(languageManager.getComponent(player.getUniqueId(),
                        "proxy.server-unavailable", instance.getStatus()));
                return;
            }
            boolean priority = accessProfiles.hasPermission(player.getUniqueId(), "tropicube.queue.priority");
            queueManager.addToQueue(player, instance.getInstanceId(), priority);
        }, () -> player.sendMessage(languageManager.getComponent(player.getUniqueId(),
                "proxy.server-not-found", args[0])));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return serverManager.getActiveInstances().values().stream()
                    .filter(i -> i.isOnline() && i.getOnlinePlayers() >= i.getMaxPlayers())
                    .filter(i -> !i.isWhitelisted())
                    .map(i -> i.getServerName())
                    .sorted()
                    .toList();
        }
        return List.of();
    }
}
