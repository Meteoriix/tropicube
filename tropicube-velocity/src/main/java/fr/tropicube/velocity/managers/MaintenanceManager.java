package fr.tropicube.velocity.managers;

import com.google.gson.Gson;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.ServerInstance;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Network/type maintenance state with graceful draining and a hard deadline. */
public final class MaintenanceManager {
    private static final Gson GSON = new Gson();
    private static final String REDIS_PREFIX = "maintenance:";
    private static final int REDIS_TTL_SECONDS = 7 * 24 * 60 * 60;

    public record DrainState(String scope, long enabledAt, long deadlineAt, String reason) {
        public DrainState {
            if (scope == null || scope.isBlank()) throw new IllegalArgumentException("scope est obligatoire");
            if (enabledAt <= 0 || deadlineAt <= enabledAt) throw new IllegalArgumentException("Échéance invalide");
            if (reason == null || reason.isBlank()) reason = "Maintenance Tropicube";
        }
    }

    private final ProxyServer proxy;
    private final TropiServerManager servers;
    private final RedisManager redis;
    private final Logger logger;
    private final Clock clock;
    private final Map<String, DrainState> states = new ConcurrentHashMap<>();

    public MaintenanceManager(ProxyServer proxy, TropiServerManager servers, RedisManager redis,
                              Logger logger, Clock clock) {
        this.proxy = proxy;
        this.servers = servers;
        this.redis = redis;
        this.logger = logger;
        this.clock = Objects.requireNonNull(clock, "clock");
        restore("network");
        servers.getTemplates().keySet().forEach(this::restore);
    }

    public DrainState enable(String scope, long durationMillis, String reason) {
        if (durationMillis <= 0) throw new IllegalArgumentException("La durée doit être positive");
        String normalized = normalize(scope);
        long now = clock.millis();
        DrainState state = new DrainState(normalized, now, Math.addExact(now, durationMillis), reason);
        states.put(normalized, state);
        redis.set(REDIS_PREFIX + normalized, GSON.toJson(state), REDIS_TTL_SECONDS);
        setTemplateMaintenance(normalized, true);
        logger.info("event=maintenance_enabled scope={} deadline={} reason={}", normalized, state.deadlineAt(), state.reason());
        return state;
    }

    public boolean disable(String scope) {
        String normalized = normalize(scope);
        DrainState removed = states.remove(normalized);
        redis.delete(REDIS_PREFIX + normalized);
        setTemplateMaintenance(normalized, false);
        if (removed != null) logger.info("event=maintenance_disabled scope={}", normalized);
        return removed != null;
    }

    public Optional<DrainState> state(String scope) {
        return Optional.ofNullable(states.get(normalize(scope)));
    }

    public boolean blocksNetwork() {
        return states.containsKey("network");
    }

    public boolean blocks(ServerInstance instance) {
        return blocksNetwork() || states.containsKey(normalize(instance.getTemplateId()))
                || states.containsKey(normalize(instance.getServerType()));
    }

    /** Enforces only expired drains; active games remain untouched before the deadline. */
    public void enforceDeadlines() {
        long now = clock.millis();
        for (DrainState state : states.values()) {
            if (now < state.deadlineAt()) continue;
            for (Player player : proxy.getAllPlayers()) enforceForPlayer(state, player);
        }
    }

    private void enforceForPlayer(DrainState state, Player player) {
        if (player.hasPermission("tropicube.maintenance.bypass")) return;
        if ("network".equals(state.scope())) {
            player.disconnect(Component.text(state.reason()));
            return;
        }
        player.getCurrentServer().flatMap(connection ->
                        servers.getInstanceByName(connection.getServerInfo().getName()))
                .filter(instance -> matches(state.scope(), instance))
                .ifPresent(instance -> servers.getBestLobby().ifPresentOrElse(
                        lobby -> player.createConnectionRequest(lobby).fireAndForget(),
                        () -> player.disconnect(Component.text(state.reason()))));
    }

    private boolean matches(String scope, ServerInstance instance) {
        return scope.equals(normalize(instance.getTemplateId())) || scope.equals(normalize(instance.getServerType()));
    }

    private void restore(String scope) {
        String normalized = normalize(scope);
        String json = redis.get(REDIS_PREFIX + normalized);
        if (json == null || json.isBlank()) return;
        try {
            DrainState state = GSON.fromJson(json, DrainState.class);
            if (state != null) {
                states.put(normalized, state);
                setTemplateMaintenance(normalized, true);
            }
        } catch (RuntimeException error) {
            logger.warn("event=maintenance_restore_failed scope={}", normalized, error);
            redis.delete(REDIS_PREFIX + normalized);
        }
    }

    private void setTemplateMaintenance(String scope, boolean enabled) {
        if ("network".equals(scope)) {
            servers.getTemplates().values().stream()
                    .filter(template -> !"LOBBY".equalsIgnoreCase(template.getServerType()))
                    .forEach(template -> template.setMaintenanceMode(enabled));
            return;
        }
        servers.getTemplates().forEach((id, template) -> {
            if (scope.equals(normalize(id)) || scope.equals(normalize(template.getServerType()))) {
                template.setMaintenanceMode(enabled);
            }
        });
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Portée de maintenance vide");
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
