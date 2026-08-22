package fr.tropicube.velocity.listeners;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.velocity.managers.ConnectionRateLimiter;
import fr.tropicube.velocity.managers.MaintenanceManager;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.util.MessageStyle;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.ConfigurationNode;

/** Enforces ingress protection and publishes the public proxy status. */
public final class OperationsListener {
    private final ProxyServer proxy;
    private final TropiServerManager servers;
    private final MaintenanceManager maintenance;
    private final ConnectionRateLimiter limiter;
    private final ConfigurationNode config;

    public OperationsListener(ProxyServer proxy, TropiServerManager servers,
                              MaintenanceManager maintenance, ConnectionRateLimiter limiter,
                              ConfigurationNode config) {
        this.proxy = proxy;
        this.servers = servers;
        this.maintenance = maintenance;
        this.limiter = limiter;
        this.config = config;
    }

    @Subscribe
    public void onPreLogin(PreLoginEvent event) {
        String address = event.getConnection().getRemoteAddress().getAddress().getHostAddress();
        if (!limiter.allow(address)) {
            event.setResult(PreLoginEvent.PreLoginComponentResult.denied(
                    MessageStyle.component("<tc><red>Trop de connexions. Réessayez dans quelques instants.")));
        }
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (maintenance.blocksNetwork() && !event.getPlayer().hasPermission("tropicube.maintenance.bypass")) {
            String reason = maintenance.state("network").map(MaintenanceManager.DrainState::reason)
                    .orElse("Maintenance Tropicube");
            event.setResult(ResultedEvent.ComponentResult.denied(Component.text(reason)));
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        if (event.getPlayer().hasPermission("tropicube.maintenance.bypass")) return;
        servers.getInstanceByName(event.getOriginalServer().getServerInfo().getName())
                .filter(maintenance::blocks)
                .ifPresent(instance -> event.setResult(ServerPreConnectEvent.ServerResult.denied()));
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        String line1 = config.node("motd", "line-1").getString("<gold><bold>Tropicube</bold></gold> <gray>•</gray> <aqua>FR / EN</aqua>");
        String line2 = maintenance.blocksNetwork()
                ? config.node("motd", "maintenance-line").getString("<red>Maintenance en cours</red>")
                : config.node("motd", "line-2").getString("<green>{online} joueurs en ligne</green>");
        String rendered = (line1 + "\n" + line2).replace("{online}", Integer.toString(proxy.getPlayerCount()));
        event.setPing(event.getPing().asBuilder().description(MessageStyle.component(rendered)).build());
    }
}
