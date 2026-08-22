package fr.tropicube.velocity.listeners;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.model.ServerTemplate;
import fr.tropicube.velocity.managers.ConnectionRateLimiter;
import fr.tropicube.velocity.managers.MaintenanceManager;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.util.MessageStyle;
import fr.tropicube.docker.client.RedisManager;
import net.kyori.adventure.text.Component;
import org.spongepowered.configurate.ConfigurationNode;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Enforces ingress protection and publishes the public proxy status. */
public final class OperationsListener {
    private final ProxyServer proxy;
    private final TropiServerManager servers;
    private final MaintenanceManager maintenance;
    private final ConnectionRateLimiter limiter;
    private final ConfigurationNode config;
    private final RedisManager redis;

    public OperationsListener(ProxyServer proxy, TropiServerManager servers,
                              MaintenanceManager maintenance, ConnectionRateLimiter limiter,
                              ConfigurationNode config, RedisManager redis) {
        this.proxy = proxy;
        this.servers = servers;
        this.maintenance = maintenance;
        this.limiter = limiter;
        this.config = config;
        this.redis = redis;
        redis.subscribeToCommands(this::receiveCommand);
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
    public EventTask onLogin(LoginEvent event) {
        return EventTask.async(() -> {
            if (redis.exists("ban:" + event.getPlayer().getUniqueId())) {
                event.setResult(ResultedEvent.ComponentResult.denied(
                        MessageStyle.component("<tc><red>Vous êtes banni du réseau Tropicube.")));
                return;
            }
            if (maintenance.blocksNetwork() && !event.getPlayer().hasPermission("tropicube.maintenance.bypass")) {
                String reason = maintenance.state("network").map(MaintenanceManager.DrainState::reason)
                        .orElse("Maintenance Tropicube");
                event.setResult(ResultedEvent.ComponentResult.denied(Component.text(reason)));
            }
        });
    }

    private void receiveCommand(String command) {
        if (!command.startsWith("PROXY:BAN_ENFORCE:")) return;
        try {
            java.util.UUID playerId = java.util.UUID.fromString(command.substring("PROXY:BAN_ENFORCE:".length()));
            proxy.getPlayer(playerId).ifPresent(player ->
                    player.disconnect(MessageStyle.component("<tc><red>Vous avez été banni du réseau Tropicube.")));
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed internal commands.
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
        String line1 = config.node("motd", "line-1").getString(
                "<gold><bold>Tropicube</bold></gold> <gray>•</gray> <aqua>Des cubes, du soleil et de l’aventure !</aqua>");
        String line2 = maintenance.blocksNetwork()
                ? config.node("motd", "maintenance-line").getString("<red>Maintenance en cours</red>")
                : config.node("motd", "line-2").getString("<yellow>{games}</yellow>");
        String rendered = (line1 + "\n" + line2).replace("{games}", availableGameNames());
        event.setPing(event.getPing().asBuilder().description(MessageStyle.component(rendered)).build());
    }

    private String availableGameNames() {
        String separator = config.node("motd", "games-separator").getString(" <dark_gray>•</dark_gray> ");
        String names = availableGameTypes(servers.getTemplates().values()).stream()
                .map(type -> config.node("motd", "game-" + type).getString(humanize(type)))
                .collect(Collectors.joining(separator));
        return names.isBlank()
                ? config.node("motd", "no-games").getString("De nouveaux jeux arrivent bientôt")
                : names;
    }

    static List<String> availableGameTypes(Collection<ServerTemplate> templates) {
        return templates.stream()
                .filter(ServerTemplate::isEnabled)
                .filter(template -> !template.isMaintenanceMode())
                .map(ServerTemplate::getServerType)
                .filter(type -> type != null && !type.isBlank() && !type.equalsIgnoreCase("LOBBY"))
                .map(type -> type.toUpperCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();
    }

    private static String humanize(String type) {
        String value = type.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }
}
