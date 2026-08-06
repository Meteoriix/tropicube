package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.velocity.managers.QueueManager;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Ajoute un joueur à la file d'attente d'une instance pleine. */
public class QueueCommand implements SimpleCommand {

    private static final Set<String> PRIORITY_GRADES = Set.of("VIP_PLUS", "PREMIUM");

    private final TropiServerManager serverManager;
    private final QueueManager queueManager;
    private final RedisManager redisManager;
    private final VelocityLanguageManager languageManager;

    public QueueCommand(TropiServerManager serverManager, QueueManager queueManager,
                        RedisManager redisManager, VelocityLanguageManager languageManager) {
        this.serverManager = serverManager;
        this.queueManager = queueManager;
        this.redisManager = redisManager;
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
            String grade = redisManager.get("player:viplevel:" + player.getUniqueId());
            boolean priority = grade != null && PRIORITY_GRADES.contains(grade.toUpperCase(Locale.ROOT));
            queueManager.addToQueue(player, instance.getInstanceId(), priority);
        }, () -> player.sendMessage(languageManager.getComponent(player.getUniqueId(),
                "proxy.server-not-found", args[0])));
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        if (invocation.arguments().length <= 1) {
            return serverManager.getActiveInstances().values().stream()
                    .filter(i -> i.isOnline() && i.getOnlinePlayers() >= i.getMaxPlayers())
                    .map(i -> i.getServerName())
                    .sorted()
                    .toList();
        }
        return List.of();
    }
}
