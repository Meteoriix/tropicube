package fr.tropicube.velocity.managers;

import fr.tropicube.velocity.util.MessageStyle;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import fr.tropicube.docker.client.DockerManager;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.docker.model.ServerTemplate;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurationNode;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Manages the complete lifecycle of dynamic Minecraft servers.
 * Creates/deletes Docker containers and registers/deregisters them with Velocity.
 */
public class TropiServerManager {

    private final ProxyServer proxy;
    private final DockerManager dockerManager;
    private final RedisManager redisManager;
    private final ConfigurationNode config;
    private final Logger logger;
    private final VelocityLanguageManager languageManager;

    // Available templates (loaded from config)
    private final Map<String, ServerTemplate> templates = new ConcurrentHashMap<>();
    // Instances actives : instanceId -> ServerInstance
    private final Map<String, ServerInstance> activeInstances = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> pendingCreations = new ConcurrentHashMap<>();
    private final InFlightCreationRegistry<String, ServerInstance> matchmakingCreations =
            new InFlightCreationRegistry<>();
    private final MatchmakingWaitlist matchmakingWaitlist = new MatchmakingWaitlist();
    private final Map<String, Long> lastHealthyAt = new ConcurrentHashMap<>();
    private final Map<String, Long> emptySince = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Boolean>> finishingGames = new ConcurrentHashMap<>();
    // Scheduled executor for periodic tasks
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public TropiServerManager(ProxyServer proxy,
                              DockerManager dockerManager, RedisManager redisManager,
                              ConfigurationNode config, Logger logger,
                              VelocityLanguageManager languageManager) {
        this.proxy = proxy;
        this.dockerManager = dockerManager;
        this.redisManager = redisManager;
        this.config = config;
        this.logger = logger;
        this.languageManager = languageManager;
    }

    public void initialize() {
        loadTemplates();
        restoreActiveInstances();
        cleanupOrphanContainers();
        ensureMinInstances();
        startAutoScaler();
        startHealthChecker();
        subscribeToProxyCommands();
        publishTemplates();
        logger.info(MessageStyle.log("PROXY", "<gray>TropiServerManager initialisé avec {} templates."), templates.size());
    }

    private void subscribeToProxyCommands() {
        redisManager.subscribeToCommands(message -> {
            // Format: "PROXY:CONNECT:<uuid>:<serverName>"
            if (message.startsWith("PROXY:CONNECT:")) {
                String rest = message.substring("PROXY:CONNECT:".length());
                int sep = rest.indexOf(':');
                if (sep < 0) return;
                String uuidStr = rest.substring(0, sep);
                String serverName = rest.substring(sep + 1);
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Optional<RegisteredServer> target = "lobby".equalsIgnoreCase(serverName)
                            ? getBestLobby()
                            : proxy.getServer(serverName);
                    proxy.getPlayer(uuid).ifPresent(player ->
                            target.ifPresent(srv -> {
                                redisManager.set("transfer:" + uuidStr, "1", 10);
                                player.createConnectionRequest(srv).connect().whenComplete((result, error) -> {
                                    if (error != null || result == null || !result.isSuccessful()) {
                                        redisManager.delete("transfer:" + uuidStr);
                                        logger.warn(MessageStyle.log("PROXY", "<yellow>Échec du transfert de {} vers {}"),
                                                player.getUsername(), srv.getServerInfo().getName(), error);
                                    }
                                });
                            }));
                } catch (Exception e) {
                    logger.warn(MessageStyle.log("PROXY", "<yellow>Erreur traitement commande CONNECT: {}"), e.getMessage());
                }
                return;
            }

            // Format: "PROXY:CREATE_HOST:<uuid>:<templateId>:<whitelisted>"
            if (message.startsWith("PROXY:CREATE_HOST:")) {
                String rest = message.substring("PROXY:CREATE_HOST:".length());
                String[] args = rest.split(":");
                if (args.length < 3) return;
                String uuidStr = args[0];
                String templateId = args[1];
                boolean whitelisted = Boolean.parseBoolean(args[2]);
                String creationKey = "host-creation:" + uuidStr;
                boolean creationReserved = false;
                try {
                    ServerTemplate tpl = templates.get(templateId);
                    if (tpl == null || !tpl.isEnabled() || "LOBBY".equalsIgnoreCase(tpl.getServerType())) {
                        logger.warn(MessageStyle.log("PROXY", "<yellow>CREATE_HOST refusé pour template invalide/lobby : {}"), templateId);
                        redisManager.publishCommand("LOBBY", "CREATE_HOST_FAILED:" + uuidStr);
                        return;
                    }
                    // NX lock also covers double clicks and processed requests
                    // simultaneously before the host:<uuid> key can be created.
                    creationReserved = redisManager.reserveUnlessBlocked(
                            creationKey, "host:" + uuidStr, templateId, 300);
                    if (!creationReserved) {
                        redisManager.publishCommand("LOBBY", "CREATE_HOST_EXISTS:" + uuidStr);
                        return;
                    }
                    Map<String, String> extraEnv = Map.of(
                            "IS_HOST", "true",
                            "HOST_UUID", uuidStr,
                            "CUSTOM_GAME_PRIVATE", Boolean.toString(whitelisted));
                    createServer(templateId, null, whitelisted, extraEnv)
                            .thenAccept(instance -> {
                                if (whitelisted) {
                                    instance.addWhitelistedPlayer(UUID.fromString(uuidStr));
                                    redisManager.saveInstance(instance);
                                }
                                redisManager.set("host:" + uuidStr, instance.getInstanceId(), 14400);
                                redisManager.delete(creationKey);
                                redisManager.publishCommand("PROXY", "CONNECT:" + uuidStr + ":" + instance.getServerName());
                            })
                            .exceptionally(ex -> {
                                redisManager.delete(creationKey);
                                logger.warn(MessageStyle.log("PROXY", "<yellow>Échec création partie personnalisée pour {} : {}"), uuidStr, ex.getMessage());
                                redisManager.publishCommand("LOBBY", "CREATE_HOST_FAILED:" + uuidStr);
                                return null;
                            });
                } catch (Exception e) {
                    if (creationReserved) redisManager.delete(creationKey);
                    logger.warn(MessageStyle.log("PROXY", "<yellow>Erreur commande CREATE_HOST: {}"), e.getMessage());
                    redisManager.publishCommand("LOBBY", "CREATE_HOST_FAILED:" + uuidStr);
                }
            }

            // Format: "PROXY:CREATE_GAME:<templateId>:<sourceInstanceId>"
            if (message.startsWith("PROXY:CREATE_GAME:")) {
                String rest = message.substring("PROXY:CREATE_GAME:".length());
                int sep = rest.indexOf(':');
                if (sep < 0) return;
                String templateId = rest.substring(0, sep);
                String sourceInstanceId = rest.substring(sep + 1);
                ServerTemplate tpl = templates.get(templateId);
                if (tpl == null || !tpl.isEnabled()) {
                    logger.warn(MessageStyle.log("PROXY", "<yellow>CREATE_GAME refusé pour template invalide : {}"), templateId);
                    return;
                }
                ensureMatchmakingCreation(templateId)
                        .thenAccept(instance -> {
                            redisManager.set("sw:next-game:" + sourceInstanceId, instance.getServerName(), 7200);
                            logger.info(MessageStyle.log("PROXY", "<gray>Prochain jeu préparé : {} pour instance {}"), instance.getServerName(), sourceInstanceId);
                        })
                        .exceptionally(ex -> {
                            logger.warn(MessageStyle.log("PROXY", "<yellow>Échec CREATE_GAME pour {} : {}"), sourceInstanceId, ex.getMessage());
                            return null;
                        });
                return;
            }

            // Format: "PROXY:START_GAME:<templateId>:<playerUuid>"
            if (message.startsWith("PROXY:START_GAME:")) {
                String rest = message.substring("PROXY:START_GAME:".length());
                int sep = rest.indexOf(':');
                if (sep < 0) return;
                String templateId = rest.substring(0, sep);
                String uuidStr = rest.substring(sep + 1);
                ServerTemplate tpl = templates.get(templateId);
                if (tpl == null || !tpl.isEnabled()) {
                    redisManager.publishCommand("LOBBY", "GAME_START_FAILED:" + uuidStr);
                    return;
                }
                try {
                    queueForMatchmaking(templateId, UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    logger.warn(MessageStyle.log("PROXY", "<yellow>UUID invalide dans START_GAME : {}"), uuidStr);
                    redisManager.publishCommand("LOBBY", "GAME_START_FAILED:" + uuidStr);
                }
                return;
            }

            // Format: "PROXY:FINISH_GAME:<instanceId>"
            if (message.startsWith("PROXY:FINISH_GAME:")) {
                String instanceId = message.substring("PROXY:FINISH_GAME:".length());
                finishGameServer(instanceId).exceptionally(error -> {
                    logger.error(MessageStyle.log("PROXY", "<red>Échec de destruction après fin de partie : {}"), instanceId, error);
                    return false;
                });
                return;
            }

            // Format: "PROXY:STOP_HOST:<uuid>"
            if (message.startsWith("PROXY:STOP_HOST:")) {
                String uuidStr = message.substring("PROXY:STOP_HOST:".length());
                String instanceId = redisManager.get("host:" + uuidStr);
                if (instanceId == null || redisManager.exists("sw:game-started:" + instanceId)) {
                    redisManager.publishCommand("LOBBY", "STOP_HOST_FAILED:" + uuidStr);
                    return;
                }
                stopServer(instanceId)
                        .thenAccept(ok -> {
                            if (ok) {
                                redisManager.delete("host:" + uuidStr);
                            } else {
                                redisManager.publishCommand("LOBBY", "STOP_HOST_FAILED:" + uuidStr);
                            }
                        })
                        .exceptionally(ex -> {
                            logger.warn(MessageStyle.log("PROXY", "<yellow>Échec arrêt partie hôte pour {} : {}"), uuidStr, ex.getMessage());
                            redisManager.publishCommand("LOBBY", "STOP_HOST_FAILED:" + uuidStr);
                            return null;
                        });
                return;
            }

            // Format: "PROXY:HOST_WHITELIST:<hostUuid>:<ADD|REMOVE>:<nameOrUuid>"
            if (message.startsWith("PROXY:HOST_WHITELIST:")) {
                String[] args = message.substring("PROXY:HOST_WHITELIST:".length()).split(":", 3);
                if (args.length != 3) return;
                try {
                    UUID hostId = UUID.fromString(args[0]);
                    boolean add = "ADD".equalsIgnoreCase(args[1]);
                    if (!add && !"REMOVE".equalsIgnoreCase(args[1])) return;
                    WhitelistUpdate update = updateHostedWhitelist(hostId, args[2], add);
                    proxy.getPlayer(hostId).ifPresent(player -> player.sendMessage(
                            languageManager.getComponent(hostId, update.messageKey(), update.targetName())));
                } catch (IllegalArgumentException e) {
                    logger.warn(MessageStyle.log("PROXY", "<yellow>Commande HOST_WHITELIST invalide : {}"), e.getMessage());
                }
            }
        });
    }

    private void loadTemplates() {
        ConfigurationNode templatesNode = config.node("templates");
        if (templatesNode.virtual()) return;

        templatesNode.childrenMap().forEach((key, node) -> {
            try {
                ServerTemplate template = new ServerTemplate();
                template.setId(key.toString());
                template.setName(node.node("name").getString(key.toString()));
                template.setDockerImage(node.node("image").getString("itzg/minecraft-server:latest"));
                template.setServerType(node.node("type").getString("SURVIVAL").toUpperCase());
                template.setMinPort(node.node("port-min").getInt(25600));
                template.setMaxPort(node.node("port-max").getInt(25700));
                template.setMaxPlayers(node.node("max-players").getInt(50));
                template.setMinRam(node.node("ram-min").getInt(512));
                template.setMaxRam(node.node("ram-max").getInt(1024));
                template.setEnabled(node.node("enabled").getBoolean(true));
                if ("LOBBY".equalsIgnoreCase(template.getServerType()) && !template.isEnabled()) {
                    logger.warn(MessageStyle.log("PROXY", "<yellow>Template lobby '{}' ne peut pas être désactivé — forcé à enabled."), template.getId());
                    template.setEnabled(true);
                }
                template.setAutoStart(node.node("auto-start").getBoolean(false));
                template.setAutoStop(node.node("auto-stop").getBoolean(true));
                template.setAutoStopDelay(node.node("auto-stop-delay").getInt(120));
                template.setMinInstances(node.node("min-instances").getInt(0));
                template.setMaxInstances(node.node("max-instances").getInt(5));

                // Variables d'environnement
                ConfigurationNode envNode = node.node("environment");
                if (!envNode.virtual()) {
                    Map<String, String> env = new HashMap<>();
                    envNode.childrenMap().forEach((k, v) -> env.put(k.toString(), v.getString("")));
                    template.setEnvironmentVariables(env);
                }

                // Volumes (bind mounts, absolute paths on Docker host)
                ConfigurationNode volumesNode = node.node("volumes");
                if (!volumesNode.virtual()) {
                    List<String> volumes = new ArrayList<>();
                    volumesNode.childrenList().forEach(v -> {
                        String vol = v.getString("");
                        if (!vol.isEmpty()) volumes.add(vol);
                    });
                    template.setVolumes(volumes);
                }

                templates.put(template.getId(), template);
                logger.info(MessageStyle.log("PROXY", "<gray>Template chargé : {}"), template.getId());
            } catch (Exception e) {
                logger.error(MessageStyle.log("PROXY", "<red>Erreur chargement template {}"), key, e);
            }
        });
    }

    private void cleanupOrphanContainers() {
        Set<String> knownIds = activeInstances.values().stream()
                .map(ServerInstance::getContainerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        dockerManager.cleanupOrphanContainers(knownIds);
        logger.info(MessageStyle.log("PROXY", "<gray>Nettoyage orphelins terminé ({} instances connues)."), knownIds.size());
    }

    private void restoreActiveInstances() {
        List<ServerInstance> redisInstances = redisManager.getAllInstances();
        for (ServerInstance instance : redisInstances) {
            if (instance.getStatus() == ServerInstance.Status.GAME_WAITING || instance.getStatus() == ServerInstance.Status.GAME_STARTING ||
                    instance.getStatus() == ServerInstance.Status.GAME_PLAYING || instance.getStatus() == ServerInstance.Status.GAME_ENDING ||
                    instance.getStatus() == ServerInstance.Status.STARTING) {
                try {
                    if (instance.getContainerId() == null
                            || !dockerManager.isContainerRunning(instance.getContainerId())) {
                        purgeRedisInstance(instance);
                        logger.warn(MessageStyle.log("PROXY", "<yellow>Instance Redis sans conteneur actif supprimée : {}"),
                                instance.getServerName());
                        continue;
                    }
                    dockerManager.reservePorts(instance);
                    activeInstances.put(instance.getInstanceId(), instance);
                    if (instance.getOnlinePlayers() == 0) {
                        emptySince.put(instance.getInstanceId(), System.currentTimeMillis() / 1000);
                    }
                    registerServerToVelocity(instance);
                    logger.info(MessageStyle.log("PROXY", "<gray>Instance restaurée : {}"), instance.getServerName());
                } catch (RuntimeException e) {
                    purgeRedisInstance(instance);
                    logger.error(MessageStyle.log("PROXY", "<red>Instance restaurée invalide, elle sera nettoyée : {}"),
                            instance.getServerName(), e);
                }
            }
        }
    }

    private void ensureMinInstances() {
        templates.values().forEach(template -> {
            if (!template.isEnabled() || !template.isAutoStart() || template.getMinInstances() <= 0) return;
            long current = activeInstances.values().stream()
                    .filter(i -> i.getTemplateId().equals(template.getId()))
                    .filter(i -> i.getStatus() != ServerInstance.Status.ERROR
                              && i.getStatus() != ServerInstance.Status.STOPPED)
                    .count();
            for (long i = current; i < template.getMinInstances(); i++) {
                createServer(template.getId(), null, false)
                        .exceptionally(ex -> {
                            logger.error(MessageStyle.log("PROXY", "<red>Échec création instance {} au démarrage"), template.getId(), ex);
                            return null;
                        });
            }
        });
    }

    /**
     * Creates a new server from a template.
     * @return CompletableFuture with the created instance
     */
    public CompletableFuture<ServerInstance> createServer(String templateId, String customName, boolean whitelisted) {
        return createServer(templateId, customName, whitelisted, Collections.emptyMap());
    }

    /**
     * Creates a new server with additional environment variables
     * that are added to (and may override) the template values.
     */
    public CompletableFuture<ServerInstance> createServer(String templateId, String customName, boolean whitelisted,
                                                          Map<String, String> extraEnv) {
        return CompletableFuture.supplyAsync(() -> {
            ServerTemplate template = templates.get(templateId);
            if (template == null) throw new IllegalArgumentException("Template introuvable : " + templateId);
            if (!template.isEnabled()) throw new IllegalStateException("Template désactivé : " + templateId);
            if (template.isMaintenanceMode()) throw new IllegalStateException("Template en maintenance.");

            AtomicInteger pending = pendingCreations.computeIfAbsent(templateId, _ -> new AtomicInteger());
            synchronized (template) {
                long instanceCount = activeInstances.values().stream()
                        .filter(i -> i.getTemplateId().equals(templateId))
                        .filter(i -> i.getStatus() != ServerInstance.Status.ERROR
                                && i.getStatus() != ServerInstance.Status.STOPPED)
                        .count();
                if (instanceCount + pending.get() >= template.getMaxInstances()) {
                    throw new IllegalStateException("Nombre maximum d'instances atteint pour : " + templateId);
                }
                pending.incrementAndGet();
            }

            String instanceId = UUID.randomUUID().toString();
            String serverName = customName != null ? customName :
                    template.getName() + "-" + instanceId.substring(0, 8);

            ServerInstance instance = null;
            try {
                dockerManager.pullImageIfAbsent(template.getDockerImage());
                instance = dockerManager.createServer(template, instanceId, serverName, whitelisted, extraEnv);
                instance.setServerType(template.getServerType());

                activeInstances.put(instanceId, instance);
                redisManager.saveInstance(instance);
                return instance;

            } catch (Exception e) {
                if (instance != null) {
                    activeInstances.remove(instanceId, instance);
                    redisManager.purgeInstance(instanceId, template.getServerType(), instance.getServerName());
                    try {
                        dockerManager.removeServer(instance);
                    } catch (Exception cleanupError) {
                        e.addSuppressed(cleanupError);
                    }
                }
                logger.error(MessageStyle.log("PROXY", "<red>Erreur création serveur {}"), serverName, e);
                throw new RuntimeException(e);
            } finally {
                if (pending.decrementAndGet() == 0) pendingCreations.remove(templateId, pending);
            }
        }, scheduler).thenCompose(instance -> waitForServerReady(instance).thenApply(_ -> {
            registerServerToVelocity(instance);
            redisManager.publishServerEvent("SERVER_STARTED",
                    instance.getInstanceId() + ":" + instance.getServerName() + ":" + instance.getServerType());
            logger.info(MessageStyle.log("PROXY", "<gray>Serveur démarré : {} (port {})"), instance.getServerName(), instance.getPort());
            return instance;
        }));
    }

    /**
     * Adds a player to the queue of a classic template. An already reachable instance is used as a priority;
     * otherwise all players share the same creation in progress and will be transferred when it is ready.
     */
    private void queueForMatchmaking(String templateId, UUID playerId) {
        matchmakingWaitlist.add(templateId, playerId);
        findJoinableMatchmakingInstance(templateId, playerId).ifPresentOrElse(
                instance -> dispatchMatchmakingPlayers(templateId, instance),
                () -> ensureMatchmakingCreation(templateId));
    }

    /** Removes a player disconnected from any expectation of classic creation. */
    public void removeFromMatchmaking(UUID playerId) {
        matchmakingWaitlist.remove(playerId);
    }

    private Optional<ServerInstance> findJoinableMatchmakingInstance(String templateId, UUID playerId) {
        return activeInstances.values().stream()
                .filter(instance -> templateId.equals(instance.getTemplateId()))
                .filter(instance -> !instance.isWhitelisted())
                .filter(instance -> instance.getStatus() == ServerInstance.Status.GAME_WAITING
                        || instance.getStatus() == ServerInstance.Status.GAME_STARTING)
                .filter(instance -> instance.isJoinable(playerId))
                .min(Comparator.comparingInt(ServerInstance::getOnlinePlayers));
    }

    /** Returns the only classic creation in progress for this template, or starts one. */
    private CompletableFuture<ServerInstance> ensureMatchmakingCreation(String templateId) {
        CompletableFuture<ServerInstance> creation = matchmakingCreations.getOrCreate(
                templateId, () -> createServer(templateId, null, false, Collections.emptyMap()));
        creation.whenComplete((instance, error) -> {
            if (matchmakingCreations.remove(templateId, creation)) {
                if (error != null) {
                    List<UUID> failedPlayers = matchmakingWaitlist.removeAll(templateId);
                    failedPlayers.forEach(playerId -> redisManager.publishCommand(
                            "LOBBY", "GAME_START_FAILED:" + playerId));
                    logger.warn(MessageStyle.log("PROXY", "<yellow>Échec de la création matchmaking {} pour {} joueur(s)"),
                            templateId, failedPlayers.size(), error);
                    return;
                }
                dispatchMatchmakingPlayers(templateId, instance);
            }
        });
        return creation;
    }

    private void dispatchMatchmakingPlayers(String templateId, ServerInstance instance) {
        List<UUID> waitingPlayers = matchmakingWaitlist.removeAll(templateId);
        if (waitingPlayers.isEmpty()) return;

        int availableSlots = Math.max(0, instance.getMaxPlayers() - instance.getOnlinePlayers());
        int transferredPlayers = 0;
        for (UUID playerId : waitingPlayers) {
            if (proxy.getPlayer(playerId).isEmpty()) continue;
            if (transferredPlayers < availableSlots) {
                redisManager.publishCommand(
                        "PROXY", "CONNECT:" + playerId + ":" + instance.getServerName());
                transferredPlayers++;
            } else {
                matchmakingWaitlist.add(templateId, playerId);
            }
        }

        if (matchmakingWaitlist.hasPlayers(templateId)) {
            ensureMatchmakingCreation(templateId);
        }
    }

    private void publishTemplates() {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (ServerTemplate t : templates.values()) {
            if (!t.isEnabled()) continue;
            if ("LOBBY".equalsIgnoreCase(t.getServerType())) continue;
            if (!first) sb.append(",");
            first = false;
            sb.append("{\"id\":\"").append(escape(t.getId())).append("\"")
              .append(",\"name\":\"").append(escape(t.getName())).append("\"")
              .append(",\"type\":\"").append(escape(t.getServerType())).append("\"")
              .append(",\"maxPlayers\":").append(t.getMaxPlayers())
              .append("}");
        }
        sb.append("]");
        redisManager.saveTemplatesJson(sb.toString());
        logger.info(MessageStyle.log("PROXY", "<gray>Templates publiés dans Redis ({} templates)."), templates.size());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Stops and deletes a server.
     */
    public CompletableFuture<Boolean> stopServer(String instanceId) {
        InstanceStopAttempt preparation = prepareStop(instanceId);
        if (preparation == null) return CompletableFuture.completedFuture(false);
        ServerInstance instance = preparation.instance();
        return transferPlayers(instance, 5).thenApplyAsync(_ -> {
            boolean stopped = dockerManager.stopServer(instance);
            if (stopped) {
                proxy.getServer(instance.getServerName()).ifPresent(s ->
                        proxy.unregisterServer(s.getServerInfo()));
                dockerManager.removeServer(instance);
                activeInstances.remove(instanceId);
                emptySince.remove(instanceId);
                lastHealthyAt.remove(instanceId);
                purgeRedisInstance(instance);
                redisManager.publishServerEvent("SERVER_STOPPED", instanceId + ":" + instance.getServerName());
                logger.info(MessageStyle.log("PROXY", "<gray>Serveur arrêté : {}"), instance.getServerName());
            } else {
                restoreAfterFailedStop(preparation);
            }
            return stopped;
        }, scheduler);
    }

    /**
     * Forces the immediate shutdown of a server (SIGKILL).
     * Migrates players, unregisters from Velocity and cleans Redis.
     */
    public CompletableFuture<Boolean> killServer(String instanceId) {
        InstanceStopAttempt preparation = prepareStop(instanceId);
        if (preparation == null) return CompletableFuture.completedFuture(false);
        ServerInstance instance = preparation.instance();
        return transferPlayers(instance, 3).thenApplyAsync(_ -> {
            // Removes the instance from the Velocity registry.
            proxy.getServer(instance.getServerName()).ifPresent(s ->
                    proxy.unregisterServer(s.getServerInfo()));

            boolean killed = dockerManager.killServer(instance);
            dockerManager.removeServer(instance);
            activeInstances.remove(instanceId);
            emptySince.remove(instanceId);
            lastHealthyAt.remove(instanceId);
            purgeRedisInstance(instance);
            redisManager.publishServerEvent("SERVER_STOPPED", instanceId + ":" + instance.getServerName());
            logger.info(MessageStyle.log("PROXY", "<gray>Serveur tué (kill) : {}"), instance.getServerName());
            return killed;
        }, scheduler);
    }

    private InstanceStopAttempt prepareStop(String instanceId) {
        ServerInstance instance = activeInstances.get(instanceId);
        if (instance == null) return null;
        InstanceStopAttempt preparation = InstanceStopAttempt.begin(instance);
        if (preparation != null) redisManager.saveInstance(instance);
        return preparation;
    }

    private void restoreAfterFailedStop(InstanceStopAttempt preparation) {
        ServerInstance instance = preparation.instance();
        if (activeInstances.get(instance.getInstanceId()) != instance) return;
        preparation.restore();
        redisManager.saveInstance(instance);
        logger.warn(MessageStyle.log("PROXY", "<yellow>Arrêt échoué pour {} : statut restauré à {}."),
                instance.getServerName(), preparation.previousStatus());
    }

    /** Actually transfers all players and then immediately destroys a completed minigame instance. */
    public CompletableFuture<Boolean> finishGameServer(String instanceId) {
        ServerInstance instance = activeInstances.get(instanceId);
        if (instance == null || "LOBBY".equalsIgnoreCase(instance.getServerType())) {
            return CompletableFuture.completedFuture(false);
        }
        synchronized (instance) {
            if (instance.getStatus() == ServerInstance.Status.STOPPING
                    || instance.getStatus() == ServerInstance.Status.STOPPED) {
                return CompletableFuture.completedFuture(false);
            }
            instance.setStatus(ServerInstance.Status.GAME_ENDING);
            redisManager.saveInstance(instance);
        }

        CompletableFuture<Boolean> created = new CompletableFuture<>();
        CompletableFuture<Boolean> concurrent = finishingGames.putIfAbsent(instanceId, created);
        if (concurrent != null) return concurrent;
        created.whenComplete((_, _) -> finishingGames.remove(instanceId, created));
        attemptFinishedGameTransfer(instance, created);
        return created;
    }

    private void attemptFinishedGameTransfer(ServerInstance instance, CompletableFuture<Boolean> completion) {
        if (activeInstances.get(instance.getInstanceId()) != instance) {
            completion.complete(false);
            return;
        }
        transferPlayers(instance, 3).whenCompleteAsync((transferred, error) -> {
            if (error == null && Boolean.TRUE.equals(transferred) && !hasConnectedPlayers(instance)) {
                killServer(instance.getInstanceId()).whenComplete((killed, killError) -> {
                    if (killError != null) completion.completeExceptionally(killError);
                    else completion.complete(killed);
                });
            } else {
                logger.warn(MessageStyle.log("PROXY", "<yellow>Fin de partie en attente : tous les joueurs de {} ne sont pas encore au lobby"),
                        instance.getServerName());
                try {
                    scheduler.schedule(
                            () -> attemptFinishedGameTransfer(instance, completion), 1, TimeUnit.SECONDS);
                } catch (RejectedExecutionException schedulingError) {
                    completion.completeExceptionally(schedulingError);
                }
            }
        }, scheduler);
    }

    private boolean hasConnectedPlayers(ServerInstance instance) {
        return proxy.getServer(instance.getServerName())
                .map(server -> !server.getPlayersConnected().isEmpty())
                .orElse(false);
    }

    private CompletableFuture<Boolean> transferPlayers(ServerInstance instance, long timeoutSeconds) {
        List<CompletableFuture<Boolean>> transfers = proxy.getAllPlayers().stream()
                .filter(player -> player.getCurrentServer()
                        .map(ServerConnection::getServer)
                        .map(server -> server.getServerInfo().getName().equals(instance.getServerName()))
                        .orElse(false))
                .map(player -> {
                    player.sendMessage(languageManager.getComponent(
                            player.getUniqueId(), "proxy.server-shutdown"));
                    return transferToLobby(player);
                })
                .toList();
        return CompletableFuture.allOf(transfers.toArray(CompletableFuture[]::new))
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .thenApply(_ -> transfers.stream().allMatch(CompletableFuture::join))
                .exceptionally(error -> {
                    logger.warn(MessageStyle.log("PROXY", "<yellow>Délai dépassé pendant le transfert des joueurs de {}"),
                            instance.getServerName(), error);
                    return false;
                });
    }

    /**
     * Stops manager tasks and, if requested, deletes dynamic servers.
     * A normal proxy restart preserves the containers so they can be restored
     * by {@link #restoreActiveInstances()} at the next startup.
     */
    public void shutdown(boolean stopDynamicServers) {
        matchmakingWaitlist.clear();
        matchmakingCreations.clear();
        if (stopDynamicServers) {
            stopAllServers();
            return;
        }
        logger.info(MessageStyle.log("PROXY", "<gray>Arrêt du proxy : conservation de {} serveur(s) dynamique(s)."),
                activeInstances.size());
        scheduler.shutdownNow();
    }

    /**
     * Stops and deletes all dynamic servers.
     * Known stops are parallelized to stay within Compose's stop_grace_period.
     * A final sweep forcibly removes every remaining dynamic container, including those
     * still in CREATING and therefore absent from activeInstances.
     */
    public void stopAllServers() {
        logger.info(MessageStyle.log("PROXY", "<gray>Arrêt de tous les serveurs ({})..."), activeInstances.size());
        scheduler.shutdownNow();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = activeInstances.values().stream()
                    .map(instance -> exec.submit(() -> {
                        try {
                            dockerManager.stopServer(instance);
                            dockerManager.removeServer(instance);
                            purgeRedisInstance(instance);
                        } catch (Exception e) {
                            logger.warn(MessageStyle.log("PROXY", "<yellow>Erreur arrêt {}"), instance.getServerName(), e);
                        }
                    }))
                    .collect(Collectors.toList());

            long deadline = System.currentTimeMillis() + 40_000;
            for (Future<?> f : futures) {
                try {
                    long remaining = deadline - System.currentTimeMillis();
                    if (remaining > 0) f.get(remaining, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {}
            }
        }

        activeInstances.clear();
        emptySince.clear();
        lastHealthyAt.clear();
        pendingCreations.clear();
        matchmakingCreations.clear();
        matchmakingWaitlist.clear();
        // Final sweep: removes any dynamic container still alive (startup in progress, crash, etc.).
        dockerManager.removeAllDynamicContainers();
    }

    private static final int MINECRAFT_INTERNAL_PORT = 25565;

    private void registerServerToVelocity(ServerInstance instance) {
        InetSocketAddress address = new InetSocketAddress(
                instance.getHost() != null ? instance.getHost() : "127.0.0.1",
                MINECRAFT_INTERNAL_PORT
        );
        ServerInfo info = new ServerInfo(instance.getServerName(), address);
        proxy.getServer(instance.getServerName())
                .ifPresent(server -> proxy.unregisterServer(server.getServerInfo()));
        proxy.registerServer(info);
        logger.info(MessageStyle.log("PROXY", "<gray>Serveur Velocity enregistré : {} -> {}:{}"), instance.getServerName(), address.getHostString(), MINECRAFT_INTERNAL_PORT);
    }

    private CompletableFuture<Boolean> transferToLobby(com.velocitypowered.api.proxy.Player player) {
        Optional<RegisteredServer> lobby = getBestLobby();
        if (lobby.isEmpty()) {
            player.sendMessage(languageManager.getComponent(player.getUniqueId(), "proxy.hub-none"));
            return CompletableFuture.completedFuture(false);
        }
        return player.createConnectionRequest(lobby.orElseThrow()).connect().handle((result, error) -> {
            if (error != null || result == null || !result.isSuccessful()) {
                logger.warn(MessageStyle.log("PROXY", "<yellow>Impossible de transférer {} vers le lobby"),
                        player.getUsername(), error);
                player.sendMessage(languageManager.getComponent(player.getUniqueId(), "proxy.transfer-failed"));
                return false;
            }
            return true;
        });
    }

    private CompletableFuture<Void> waitForServerReady(ServerInstance instance) {
        if (instance.getContainerId() == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Instance sans identifiant de conteneur : " + instance.getInstanceId()));
        }
        return dockerManager.waitForServerReadyViaLogs(instance.getContainerId(), 120)
                .handleAsync((_, ex) -> {
                    if (ex != null) {
                        instance.setStatus(ServerInstance.Status.ERROR);
                        logger.error(MessageStyle.log("PROXY", "<red>Le serveur {} n'est pas devenu disponible."),
                                instance.getServerName(), ex);
                        try {
                            dockerManager.removeServer(instance);
                        } finally {
                            activeInstances.remove(instance.getInstanceId(), instance);
                            purgeRedisInstance(instance);
                        }
                        throw new CompletionException(ex);
                    }
                    instance.setStatus(ServerInstance.Status.GAME_WAITING);
                    instance.setStartedAt(System.currentTimeMillis() / 1000);
                    emptySince.put(instance.getInstanceId(), instance.getStartedAt());
                    lastHealthyAt.put(instance.getInstanceId(), instance.getStartedAt());
                    redisManager.saveInstance(instance);
                    return null;
                }, scheduler);
    }

    private void startAutoScaler() {
        scheduler.scheduleAtFixedRate(() -> templates.values().forEach(template -> {
            if (!template.isEnabled() || template.isMaintenanceMode()) return;
            long current = activeInstances.values().stream()
                    .filter(i -> i.getTemplateId().equals(template.getId()))
                    .filter(i -> i.getStatus() != ServerInstance.Status.ERROR
                              && i.getStatus() != ServerInstance.Status.STOPPED)
                    .count();

            // Scale UP if insufficient (respects auto-start: without auto-start, the min is never forced)
            if (template.isAutoStart() && current < template.getMinInstances()) {
                logger.info(MessageStyle.log("PROXY", "<gray>Auto-scale UP : {}"), template.getId());
                createServer(template.getId(), null, false)
                        .exceptionally(ex -> {
                            logger.error(MessageStyle.log("PROXY", "<red>Échec auto-scale UP pour {}"), template.getId(), ex);
                            return null;
                        });
            }

            // Hitchhiking empty servers
            if (template.isAutoStop()) {
                activeInstances.values().stream()
                        .filter(i -> i.getTemplateId().equals(template.getId()))
                        .filter(i -> i.getOnlinePlayers() == 0)
                        .filter(i -> i.getStatus() == ServerInstance.Status.GAME_ENDING
                                || i.getStatus() == ServerInstance.Status.GAME_WAITING)
                        .filter(i -> System.currentTimeMillis() / 1000
                                - emptySince.getOrDefault(i.getInstanceId(), System.currentTimeMillis() / 1000)
                                > template.getAutoStopDelay())
                        .filter(_ -> current > template.getMinInstances())
                        .findFirst()
                        .ifPresent(i -> {
                            logger.info(MessageStyle.log("PROXY", "<gray>Auto-stop serveur vide : {}"), i.getServerName());
                            stopServer(i.getInstanceId());
                        });
            }
        }), 30, 30, TimeUnit.SECONDS);
    }

    private void startHealthChecker() {
        long intervalSeconds = requirePositiveConfig("health-check.interval-seconds", 10);
        long staleTimeoutSeconds = requirePositiveConfig("health-check.stale-timeout-seconds", 60);
        int connectTimeoutMillis = Math.toIntExact(requirePositiveConfig("health-check.connect-timeout-millis", 2000));
        if (staleTimeoutSeconds < intervalSeconds) {
            throw new IllegalArgumentException("health-check.stale-timeout-seconds doit être supérieur ou égal à l'intervalle");
        }
        scheduler.scheduleAtFixedRate(() -> activeInstances.values().forEach(instance -> {
            if (!HealthCheckPolicy.isMonitored(instance.getStatus())) return;
            long now = System.currentTimeMillis() / 1000;
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new InetSocketAddress(
                        instance.getHost() != null ? instance.getHost() : "127.0.0.1",
                        MINECRAFT_INTERNAL_PORT), connectTimeoutMillis);
                lastHealthyAt.put(instance.getInstanceId(), now);
            } catch (Exception e) {
                long lastHealthy = lastHealthyAt.computeIfAbsent(instance.getInstanceId(), _ -> now);
                long silentSeconds = Math.max(0, now - lastHealthy);
                logger.warn(MessageStyle.log("PROXY", "<yellow>Health check échoué depuis {} s : {}"),
                        silentSeconds, instance.getServerName());
                if (HealthCheckPolicy.isStale(lastHealthy, now, staleTimeoutSeconds)) {
                    instance.setStatus(ServerInstance.Status.ERROR);
                    redisManager.saveInstance(instance);
                    killServer(instance.getInstanceId()).exceptionally(error -> {
                        logger.error(MessageStyle.log("PROXY", "<red>Purge impossible après {} s sans healthcheck : {}"),
                                staleTimeoutSeconds, instance.getServerName(), error);
                        return false;
                    });
                }
            }
        }), intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    private long requirePositiveConfig(String path, long fallback) {
        long value = config.node((Object[]) path.split("\\.")).getLong(fallback);
        if (value <= 0) throw new IllegalArgumentException(path + " doit être strictement positif");
        return value;
    }

    private void purgeRedisInstance(ServerInstance instance) {
        int removedReferences = redisManager.purgeInstance(
                instance.getInstanceId(), instance.getServerType(), instance.getServerName());
        if (removedReferences > 0) {
            logger.info(MessageStyle.log("PROXY", "<gray>{} référence(s) Redis secondaire(s) purgée(s) pour {}"),
                    removedReferences, instance.getServerName());
        }
    }

    /**
     * Returns the best available lobby (least loaded).
     */
    public Optional<RegisteredServer> getBestLobby() {
        return activeInstances.values().stream()
                .filter(i -> i.getServerType().equals("LOBBY") && i.isJoinable())
                .min(Comparator.comparingInt(ServerInstance::getOnlinePlayers))
                .flatMap(i -> proxy.getServer(i.getServerName()));
    }

    /**
     * Returns all instances of a certain type.
     */
    public List<ServerInstance> getInstancesByType(String type) {
        return activeInstances.values().stream()
                .filter(i -> i.getServerType().equalsIgnoreCase(type))
                .collect(Collectors.toList());
    }

    public Map<String, ServerTemplate> getTemplates() { return Map.copyOf(templates); }
    public Map<String, ServerInstance> getActiveInstances() { return Map.copyOf(activeInstances); }

    public Optional<ServerInstance> getInstanceById(String instanceId) {
        return Optional.ofNullable(activeInstances.get(instanceId));
    }

    public Optional<ServerInstance> getInstanceByName(String name) {
        return activeInstances.values().stream()
                .filter(i -> i.getServerName().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Mutates the private instance owned by a host. This method is the sole whitelist write path.
     * It validates ownership again even for trusted Redis commands coming from a backend.
     */
    public WhitelistUpdate updateHostedWhitelist(UUID hostId, String playerIdentifier, boolean add) {
        Objects.requireNonNull(hostId, "hostId");
        String hostedInstanceId = redisManager.get("host:" + hostId);
        ServerInstance instance = hostedInstanceId == null ? null : activeInstances.get(hostedInstanceId);
        if (instance == null) return new WhitelistUpdate("proxy.whitelist-no-host", "");
        if (!instance.isWhitelisted()) return new WhitelistUpdate("proxy.whitelist-not-private", "");

        UUID targetId = resolveKnownPlayer(playerIdentifier);
        if (targetId == null) return new WhitelistUpdate("proxy.whitelist-player-unknown", playerIdentifier);
        String targetName = resolveKnownPlayerName(targetId, playerIdentifier);
        if (!add && targetId.equals(hostId)) {
            return new WhitelistUpdate("proxy.whitelist-host-protected", targetName);
        }

        synchronized (instance) {
            boolean changed = add
                    ? instance.addWhitelistedPlayer(targetId)
                    : instance.removeWhitelistedPlayer(targetId);
            if (!changed) {
                return new WhitelistUpdate(add
                        ? "proxy.whitelist-already-added"
                        : "proxy.whitelist-not-added", targetName);
            }
            redisManager.saveInstance(instance);
        }
        return new WhitelistUpdate(add ? "proxy.whitelist-added" : "proxy.whitelist-removed", targetName);
    }

    private UUID resolveKnownPlayer(String identifier) {
        if (identifier == null || identifier.isBlank()) return null;
        try {
            return UUID.fromString(identifier);
        } catch (IllegalArgumentException ignored) {
            // Player names are resolved from the proxy first, then from the durable Redis cache.
        }
        Optional<Player> online = proxy.getPlayer(identifier);
        if (online.isPresent()) return online.get().getUniqueId();
        String cached = redisManager.get("player:uuid:" + identifier.toLowerCase(Locale.ROOT));
        if (cached == null) return null;
        try {
            return UUID.fromString(cached);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String resolveKnownPlayerName(UUID playerId, String fallback) {
        return proxy.getPlayer(playerId).map(Player::getUsername)
                .orElseGet(() -> {
                    String cached = redisManager.get("player:name:" + playerId);
                    return cached == null || cached.isBlank() ? fallback : cached;
                });
    }

    /** Localized result returned to both the proxy command and the backend GUI. */
    public record WhitelistUpdate(String messageKey, String targetName) {}

    public void updateInstancePlayers(String instanceId, int count) {
        ServerInstance instance = activeInstances.get(instanceId);
        if (instance != null && instance.getOnlinePlayers() != count) {
            instance.setOnlinePlayers(count);
            long now = System.currentTimeMillis() / 1000;
            if (count == 0) emptySince.putIfAbsent(instanceId, now);
            else emptySince.remove(instanceId);
            redisManager.saveInstance(instance);
        }
    }

    public void refreshPlayerCounts() {
        activeInstances.values().forEach(instance -> proxy.getServer(instance.getServerName())
                .ifPresent(server -> updateInstancePlayers(
                        instance.getInstanceId(), server.getPlayersConnected().size())));
    }
}
