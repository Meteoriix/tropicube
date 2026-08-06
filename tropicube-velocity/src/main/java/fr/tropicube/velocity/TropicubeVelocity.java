package fr.tropicube.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.client.DockerManager;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.velocity.commands.*;
import fr.tropicube.velocity.listeners.NickListener;
import fr.tropicube.velocity.listeners.PlayerConnectionListener;
import fr.tropicube.velocity.listeners.ServerSwitchListener;
import fr.tropicube.velocity.managers.NickManager;
import fr.tropicube.velocity.managers.TropiServerManager;
import fr.tropicube.velocity.managers.QueueManager;
import fr.tropicube.velocity.managers.VelocityLanguageManager;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Plugin(
        id = "tropicube-velocity",
        name = "Tropicube Velocity",
        version = "1.0.0",
        authors = {"Tropicube Team"},
        description = "Gestion dynamique de serveurs Minecraft via Docker"
)
/**
 * Point d'entrée Velocity du réseau Tropicube. Il initialise Redis, Docker,
 * la découverte des serveurs dynamiques, les files d'attente et les commandes proxy.
 */
public class TropicubeVelocity {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;

    private ConfigurationNode config;
    private DockerManager dockerManager;
    private RedisManager redisManager;
    private VelocityLanguageManager languageManager;
    private TropiServerManager tropiServerManager;
    private QueueManager queueManager;
    private NickManager nickManager;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    @Inject
    public TropicubeVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info("╔══════════════════════════════╗");
        logger.info("║   Tropicube Velocity v1.0.0    ║");
        logger.info("╚══════════════════════════════╝");

        try {
            loadConfig();
            initRedis();
            initLanguageManager();
            initDocker();
            initManagers();
            initNickManager();
            registerCommands();
            registerListeners();
        } catch (RuntimeException e) {
            logger.error("[Tropicube] Initialisation interrompue.", e);
            shutdownComponents();
            throw e;
        }

        logger.info("[Tropicube] Plugin initialisé avec succès !");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info("[Tropicube] Arrêt en cours...");
        shutdownComponents();
    }

    private void shutdownComponents() {
        if (!shuttingDown.compareAndSet(false, true)) return;
        if (queueManager != null) {
            queueManager.shutdown();
        }
        if (tropiServerManager != null) {
            boolean stopDynamicServers = config != null
                    && config.node("shutdown", "stop-dynamic-servers").getBoolean(false);
            tropiServerManager.shutdown(stopDynamicServers);
        }
        if (redisManager != null) {
            redisManager.close();
        }
        if (dockerManager != null) {
            dockerManager.close();
        }
    }

    private void loadConfig() {
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            Path configPath = dataDirectory.resolve("config.yml");
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                    if (in == null) throw new IOException("Ressource /config.yml introuvable");
                    Files.copy(in, configPath);
                }
            }
            YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                    .path(configPath)
                    .build();
            config = loader.load();
            logger.info("[Tropicube] Configuration chargée.");
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de charger la configuration Velocity", e);
        }
    }

    private void initRedis() {
        String host = config.node("redis", "host").getString("localhost");
        int port = config.node("redis", "port").getInt(6379);
        String password = environmentOrConfig("REDIS_PASSWORD", "redis", "password");
        redisManager = new RedisManager(host, port, password);
        redisManager.initialize();
        logger.info("[Tropicube] Redis connecté sur {}:{}", host, port);
    }

    private void initDocker() {
        String dockerHost = config.node("docker", "host").getString("unix:///var/run/docker.sock");
        String networkName = config.node("docker", "network").getString("tropicube-network");
        String prefix = config.node("docker", "container-prefix").getString("tropicube");
        int portStart = config.node("docker", "port-range-start").getInt(25600);
        int portEnd = config.node("docker", "port-range-end").getInt(25700);
        int rconPortStart = config.node("docker", "rcon-port-range-start").getInt(25701);
        int rconPortEnd = config.node("docker", "rcon-port-range-end").getInt(25800);
        String rconPassword = environmentOrConfig("RCON_PASSWORD", "docker", "rcon-password");
        String basePath = config.node("docker", "base-path").getString("");
        dockerManager = new DockerManager(dockerHost, networkName, prefix, portStart, portEnd,
                rconPortStart, rconPortEnd, rconPassword, basePath);
        logger.info("[Tropicube] Docker manager initialisé (base-path: {}).", basePath.isEmpty() ? "none" : basePath);
    }

    private String environmentOrConfig(String environmentName, Object... configPath) {
        String value = System.getenv(environmentName);
        return value == null || value.isBlank() ? config.node(configPath).getString("") : value;
    }

    private void initLanguageManager() {
        String defaultLang = config.node("language", "default").getString("fr");
        languageManager = new VelocityLanguageManager(dataDirectory, redisManager, logger, defaultLang);
        logger.info("[Tropicube] Gestionnaire de langue initialisé.");
    }

    private void initManagers() {
        tropiServerManager = new TropiServerManager(server, dockerManager, redisManager, config, logger, languageManager);
        queueManager = new QueueManager(server, tropiServerManager, languageManager);
        tropiServerManager.initialize();
    }

    private void initNickManager() {
        List<String> extraUuids   = List.of();
        List<String> allowedGrades = List.of();
        try {
            extraUuids    = config.node("nick", "skin-uuids").getList(String.class, List.of());
            allowedGrades = config.node("nick", "allowed-grades").getList(String.class, List.of());
        } catch (Exception e) {
            logger.warn("[Nick] Could not read nick config: {}", e.getMessage());
        }
        nickManager = new NickManager(redisManager, logger, extraUuids, allowedGrades);
        logger.info("[Tropicube] Nick manager initialisé ({} grades autorisés, {} UUIDs dans le pool).",
            allowedGrades.size(), extraUuids.size() + 3);
    }

    private void registerCommands() {
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("tropi").aliases("tropicube", "cm").build(),
                new TropiAdminCommand(tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("server").build(),
                new ServerSelectorCommand(this, tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("hub").aliases("lobby").build(),
                new HubCommand(tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("send").build(),
                new SendCommand(server, tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("find").build(),
                new FindCommand(server, redisManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("nick").build(),
                new NickCommand(nickManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("queue").aliases("file").build(),
                new QueueCommand(tropiServerManager, queueManager, redisManager, languageManager)
        );
        logger.info("[Tropicube] Commandes enregistrées.");
    }

    private void registerListeners() {
        server.getEventManager().register(this, new PlayerConnectionListener(this, tropiServerManager, redisManager, logger, languageManager));
        server.getEventManager().register(this, new ServerSwitchListener(this, redisManager, nickManager, logger));
        server.getEventManager().register(this, new NickListener(nickManager, logger));
        logger.info("[Tropicube] Listeners enregistrés.");
    }

    // ===== Getters =====

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public ConfigurationNode getConfig() { return config; }
    public DockerManager getDockerManager() { return dockerManager; }
    public RedisManager getRedisManager() { return redisManager; }
    public VelocityLanguageManager getLanguageManager() { return languageManager; }
    public TropiServerManager getTropiServerManager() { return tropiServerManager; }
    public QueueManager getQueueManager() { return queueManager; }
    public NickManager getNickManager() { return nickManager; }
    public Path getDataDirectory() { return dataDirectory; }
}
