package fr.tropicube.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.velocity.managers.ConnectionRateLimiter;
import fr.tropicube.velocity.managers.MaintenanceManager;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.util.MessageStyle;

/** Read-only operational summary for staff. */
public final class NetworkDiagnosticCommand implements SimpleCommand {
    private final ProxyServer proxy;
    private final TropiServerManager servers;
    private final RedisManager redis;
    private final MaintenanceManager maintenance;
    private final ConnectionRateLimiter limiter;

    public NetworkDiagnosticCommand(ProxyServer proxy, TropiServerManager servers, RedisManager redis,
                                    MaintenanceManager maintenance, ConnectionRateLimiter limiter) {
        this.proxy = proxy;
        this.servers = servers;
        this.redis = redis;
        this.maintenance = maintenance;
        this.limiter = limiter;
    }

    @Override public void execute(Invocation invocation) {
        long started = System.nanoTime();
        boolean redisReady;
        try {
            redis.getTemplatesJson();
            redisReady = true;
        } catch (RuntimeException error) {
            redisReady = false;
        }
        long redisMillis = (System.nanoTime() - started) / 1_000_000;
        long onlineInstances = servers.getActiveInstances().values().stream().filter(instance -> instance.isOnline()).count();
        invocation.source().sendMessage(MessageStyle.component("<tc><aqua>Diagnostic réseau</aqua>\n"
                + "<gray>Joueurs:</gray> <white>" + proxy.getPlayerCount() + "</white>\n"
                + "<gray>Instances:</gray> <white>" + onlineInstances + "/" + servers.getActiveInstances().size() + "</white>\n"
                + "<gray>Redis:</gray> " + (redisReady ? "<green>OK" : "<red>ERREUR") + " <dark_gray>(" + redisMillis + " ms)</dark_gray>\n"
                + "<gray>Maintenance réseau:</gray> " + (maintenance.blocksNetwork() ? "<yellow>ACTIVE" : "<green>NON") + "\n"
                + "<gray>Adresses en quarantaine:</gray> <white>" + limiter.quarantinedAddresses()));
    }

    @Override public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("tropicube.admin.diagnostic");
    }
}
