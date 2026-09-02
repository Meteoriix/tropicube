package fr.tropicube.velocity;

import fr.tropicube.velocity.util.MessageStyle;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import fr.tropicube.docker.client.DockerManager;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.AccessPolicy;
import fr.tropicube.velocity.commands.*;
import fr.tropicube.velocity.listeners.NickListener;
import fr.tropicube.velocity.listeners.CommandVisibilityListener;
import fr.tropicube.velocity.listeners.PlayerConnectionListener;
import fr.tropicube.velocity.listeners.ServerSwitchListener;
import fr.tropicube.velocity.listeners.OperationsListener;
import fr.tropicube.velocity.managers.AnnouncementManager;
import fr.tropicube.velocity.managers.AccessProfileCache;
import fr.tropicube.velocity.managers.ConnectionRateLimiter;
import fr.tropicube.velocity.managers.MaintenanceManager;
import fr.tropicube.velocity.managers.NickManager;
import fr.tropicube.velocity.managers.PartyCoordinator;
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
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import com.velocitypowered.api.scheduler.ScheduledTask;

@Plugin(
        id = "tropicube-velocity",
        name = "Tropicube Velocity",
        version = "1.0.0",
        authors = {"Tropicube Team"},
        description = "Gestion dynamique de serveurs Minecraft via Docker"
)
/**
 * Velocity entry point to the Tropicube network. It initializes Redis, Docker,
 * discovery of dynamic servers, queues and proxy commands.
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
    private PartyCoordinator partyCoordinator;
    private NickManager nickManager;
    private MaintenanceManager maintenanceManager;
    private ConnectionRateLimiter connectionRateLimiter;
    private AnnouncementManager announcementManager;
    private AccessProfileCache accessProfileCache;
    private ScheduledTask maintenanceTask;
    private ScheduledTask announcementTask;
    private final AtomicBoolean shuttingDown = new AtomicBoolean();

    @Inject
    public TropicubeVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        logger.info(MessageStyle.log("PROXY", "<gray>Velocity <white>v1.0.0</white> démarre."));

        try {
            loadConfig();
            initRedis();
            initLanguageManager();
            initDocker();
            initManagers();
            initOperations();
            initNickManager();
            registerCommands();
            registerListeners();
        } catch (RuntimeException e) {
            logger.error(MessageStyle.log("PROXY", "<red>Initialisation interrompue."), e);
            shutdownComponents();
            throw e;
        }

        logger.info(MessageStyle.log("PROXY", "<gray>Plugin initialisé avec succès !"));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        logger.info(MessageStyle.log("PROXY", "<gray>Arrêt en cours..."));
        shutdownComponents();
    }

    private void shutdownComponents() {
        if (!shuttingDown.compareAndSet(false, true)) return;
        if (partyCoordinator != null) {
            partyCoordinator.close();
        }
        if (announcementTask != null) announcementTask.cancel();
        if (maintenanceTask != null) maintenanceTask.cancel();
        if (queueManager != null) {
            queueManager.shutdown();
        }
        if (tropiServerManager != null) {
            boolean stopDynamicServers = stopDynamicServersOnShutdown(config);
            tropiServerManager.shutdown(stopDynamicServers);
        }
        if (redisManager != null) {
            redisManager.close();
        }
        if (dockerManager != null) {
            dockerManager.close();
        }
    }

    /**
     * Determines whether dynamic containers and their anonymous volumes should be deleted.
     * The absence of configuration favors cleaning so as not to leave any data
     * ephemeral on the host after stopping the proxy.
     */
    static boolean stopDynamicServersOnShutdown(ConfigurationNode config) {
        return config == null || config.node("shutdown", "stop-dynamic-servers").getBoolean(true);
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
            logger.info(MessageStyle.log("PROXY", "<gray>Configuration chargée."));
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
        logger.info(MessageStyle.log("PROXY", "<gray>Redis connecté sur {}:{}"), host, port);
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
        logger.info(MessageStyle.log("PROXY", "<gray>Docker manager initialisé (base-path: {})."), basePath.isEmpty() ? "none" : basePath);
    }

    private String environmentOrConfig(String environmentName, Object... configPath) {
        String value = System.getenv(environmentName);
        return value == null || value.isBlank() ? config.node(configPath).getString("") : value;
    }

    private void initLanguageManager() {
        String defaultLang = config.node("language", "default").getString("fr");
        languageManager = new VelocityLanguageManager(dataDirectory, redisManager, logger, defaultLang);
        logger.info(MessageStyle.log("PROXY", "<gray>Gestionnaire de langue initialisé."));
    }

    private void initManagers() {
        accessProfileCache = new AccessProfileCache(redisManager, logger,
                new AccessPolicy(accessThresholds("vip"), accessThresholds("mod")));
        tropiServerManager = new TropiServerManager(server, dockerManager, redisManager, config, logger, languageManager);
        queueManager = new QueueManager(server, tropiServerManager, languageManager);
        tropiServerManager.initialize();
        int partyDisconnectGraceSeconds = partyDisconnectGraceSeconds(config);
        partyCoordinator = new PartyCoordinator(this, tropiServerManager, redisManager, languageManager, logger,
                partyDisconnectGraceSeconds);
    }

    private void initOperations() {
        int addressLimit = positiveInt("connection-protection.address-limit", 8);
        int globalLimit = positiveInt("connection-protection.global-limit", 120);
        int windowSeconds = positiveInt("connection-protection.window-seconds", 10);
        int quarantineSeconds = positiveInt("connection-protection.quarantine-seconds", 30);
        if (globalLimit < addressLimit) {
            throw new IllegalArgumentException("connection-protection.global-limit doit être >= address-limit");
        }
        connectionRateLimiter = new ConnectionRateLimiter(Clock.systemUTC(), addressLimit, globalLimit,
                TimeUnit.SECONDS.toMillis(windowSeconds), TimeUnit.SECONDS.toMillis(quarantineSeconds));
        maintenanceManager = new MaintenanceManager(server, tropiServerManager, redisManager, logger, Clock.systemUTC());
        announcementManager = new AnnouncementManager(server, tropiServerManager, languageManager, config);
        maintenanceTask = server.getScheduler().buildTask(this, maintenanceManager::enforceDeadlines)
                .repeat(10, TimeUnit.SECONDS).schedule();
        int announcementSeconds = positiveInt("announcements.interval-seconds", 300);
        announcementTask = server.getScheduler().buildTask(this, announcementManager::broadcastNext)
                .delay(announcementSeconds, TimeUnit.SECONDS)
                .repeat(announcementSeconds, TimeUnit.SECONDS).schedule();
    }

    private int positiveInt(String path, int defaultValue) {
        int value = config.node((Object[]) path.split("\\.")).getInt(defaultValue);
        if (value <= 0) throw new IllegalArgumentException(path + " doit être strictement positif");
        return value;
    }

    static int maintenanceDefaultDeadlineMinutes(ConfigurationNode config) {
        int value = config.node("maintenance", "default-deadline-minutes").getInt(30);
        if (value < 1 || value > 1_440) {
            throw new IllegalArgumentException(
                    "maintenance.default-deadline-minutes doit être compris entre 1 et 1440, valeur reçue : " + value);
        }
        return value;
    }

    private java.util.Map<String, Integer> accessThresholds(String axis) {
        java.util.Map<String, Integer> values = new java.util.HashMap<>();
        config.node("access", "permission-thresholds", axis).childrenMap().forEach((key, node) ->
                values.put(String.valueOf(key), node.getInt()));
        return values;
    }

    static int partyDisconnectGraceSeconds(ConfigurationNode config) {
        int value = config.node("party", "disconnect-grace-seconds").getInt(60);
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "party.disconnect-grace-seconds doit être strictement positif, valeur reçue : " + value);
        }
        return value;
    }

    private void initNickManager() {
        List<String> extraUuids   = List.of();
        try {
            extraUuids    = config.node("nick", "skin-uuids").getList(String.class, List.of());
        } catch (Exception e) {
            logger.warn(MessageStyle.log("NICK", "<yellow>Impossible de lire la configuration nick : {}"), e.getMessage());
        }
        nickManager = new NickManager(redisManager, logger, extraUuids, accessProfileCache);
        logger.info(MessageStyle.log("PROXY", "<gray>Nick manager initialisé (vipLevel 3, {} UUIDs dans le pool)."),
            extraUuids.size() + 3);
    }

    private void registerCommands() {
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("tropicube").aliases("tropi", "cm").build(),
                new TropiAdminCommand(tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("languageeditorreload").build(),
                new LanguageEditorReloadCommand(this, server, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("server").build(),
                new ServerSelectorCommand(this, tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("lobby").aliases("hub").build(),
                new HubCommand(tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("send").build(),
                new SendCommand(server, tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("pull").build(),
                new PullCommand(server, tropiServerManager, languageManager)
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
                new QueueCommand(tropiServerManager, queueManager, accessProfileCache, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("whitelist").build(),
                new WhitelistCommand(this, tropiServerManager, languageManager)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("maintenance").build(),
                new MaintenanceCommand(maintenanceManager, tropiServerManager,
                        maintenanceDefaultDeadlineMinutes(config))
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("networkdiag").aliases("netdiag").build(),
                new NetworkDiagnosticCommand(server, tropiServerManager, redisManager,
                        maintenanceManager, connectionRateLimiter)
        );
        server.getCommandManager().register(
                server.getCommandManager().metaBuilder("announce").build(),
                new AnnounceCommand(announcementManager)
        );
        logger.info(MessageStyle.log("PROXY", "<gray>Commandes enregistrées."));
    }

    private void registerListeners() {
        server.getEventManager().register(this, new PlayerConnectionListener(this, tropiServerManager, redisManager,
                accessProfileCache, logger, languageManager));
        server.getEventManager().register(this, new ServerSwitchListener(this, redisManager, nickManager, partyCoordinator, logger));
        server.getEventManager().register(this, new NickListener(nickManager, logger));
        server.getEventManager().register(this, new CommandVisibilityListener());
        server.getEventManager().register(this, new OperationsListener(server, tropiServerManager,
                maintenanceManager, connectionRateLimiter, config, redisManager));
        logger.info(MessageStyle.log("PROXY", "<gray>Listeners enregistrés."));
    }

    // ===== Getters =====

    public ProxyServer getServer() { return server; }
    public Logger getLogger() { return logger; }
    public ConfigurationNode getConfig() { return config; }
    public RedisManager getRedisManager() { return redisManager; }
    public VelocityLanguageManager getLanguageManager() { return languageManager; }
    public TropiServerManager getTropiServerManager() { return tropiServerManager; }
    public QueueManager getQueueManager() { return queueManager; }
    public PartyCoordinator getPartyCoordinator() { return partyCoordinator; }
}
