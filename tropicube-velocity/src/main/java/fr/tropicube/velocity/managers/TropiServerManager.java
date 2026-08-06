package fr.tropicube.velocity.managers;

import com.velocitypowered.api.proxy.ProxyServer;
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
 * Gère le cycle de vie complet des serveurs Minecraft dynamiques.
 * Crée/supprime des containers Docker et les enregistre/désenregistre auprès de Velocity.
 */
public class TropiServerManager {

    private final ProxyServer proxy;
    private final DockerManager dockerManager;
    private final RedisManager redisManager;
    private final ConfigurationNode config;
    private final Logger logger;
    private final VelocityLanguageManager languageManager;

    // Templates disponibles (chargés depuis config)
    private final Map<String, ServerTemplate> templates = new ConcurrentHashMap<>();
    // Instances actives : instanceId -> ServerInstance
    private final Map<String, ServerInstance> activeInstances = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> pendingCreations = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> healthFailures = new ConcurrentHashMap<>();
    private final Map<String, Long> emptySince = new ConcurrentHashMap<>();
    // Scheduled executor pour les tâches périodiques
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
        logger.info("[Tropicube] TropiServerManager initialisé avec {} templates.", templates.size());
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
                                        logger.warn("[Tropicube] Échec du transfert de {} vers {}",
                                                player.getUsername(), srv.getServerInfo().getName(), error);
                                    }
                                });
                            }));
                } catch (Exception e) {
                    logger.warn("[Tropicube] Erreur traitement commande CONNECT: {}", e.getMessage());
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
                        logger.warn("[Tropicube] CREATE_HOST refusé pour template invalide/lobby : {}", templateId);
                        redisManager.publishCommand("LOBBY", "CREATE_HOST_FAILED:" + uuidStr);
                        return;
                    }
                    // Le verrou NX couvre aussi les doubles clics et les requêtes traitées
                    // simultanément avant que la clé host:<uuid> puisse être créée.
                    creationReserved = redisManager.reserveUnlessBlocked(
                            creationKey, "host:" + uuidStr, templateId, 300);
                    if (!creationReserved) {
                        redisManager.publishCommand("LOBBY", "CREATE_HOST_EXISTS:" + uuidStr);
                        return;
                    }
                    Map<String, String> extraEnv = Map.of("IS_HOST", "true", "HOST_UUID", uuidStr);
                    createServer(templateId, null, whitelisted, extraEnv)
                            .thenAccept(instance -> {
                                redisManager.set("host:" + uuidStr, instance.getInstanceId(), 14400);
                                redisManager.delete(creationKey);
                                redisManager.publishCommand("PROXY", "CONNECT:" + uuidStr + ":" + instance.getServerName());
                            })
                            .exceptionally(ex -> {
                                redisManager.delete(creationKey);
                                logger.warn("[Tropicube] Échec création partie personnalisée pour {} : {}", uuidStr, ex.getMessage());
                                redisManager.publishCommand("LOBBY", "CREATE_HOST_FAILED:" + uuidStr);
                                return null;
                            });
                } catch (Exception e) {
                    if (creationReserved) redisManager.delete(creationKey);
                    logger.warn("[Tropicube] Erreur commande CREATE_HOST: {}", e.getMessage());
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
                    logger.warn("[Tropicube] CREATE_GAME refusé pour template invalide : {}", templateId);
                    return;
                }
                createServer(templateId, null, false, Collections.emptyMap())
                        .thenAccept(instance -> {
                            redisManager.set("sw:next-game:" + sourceInstanceId, instance.getServerName(), 7200);
                            logger.info("[Tropicube] Prochain jeu préparé : {} pour instance {}", instance.getServerName(), sourceInstanceId);
                        })
                        .exceptionally(ex -> {
                            logger.warn("[Tropicube] Échec CREATE_GAME pour {} : {}", sourceInstanceId, ex.getMessage());
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
                createServer(templateId, null, false, Collections.emptyMap())
                        .thenAccept(instance ->
                                redisManager.publishCommand("PROXY", "CONNECT:" + uuidStr + ":" + instance.getServerName()))
                        .exceptionally(ex -> {
                            logger.warn("[Tropicube] Échec START_GAME pour {} : {}", uuidStr, ex.getMessage());
                            redisManager.publishCommand("LOBBY", "GAME_START_FAILED:" + uuidStr);
                            return null;
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
                            logger.warn("[Tropicube] Échec arrêt partie hôte pour {} : {}", uuidStr, ex.getMessage());
                            redisManager.publishCommand("LOBBY", "STOP_HOST_FAILED:" + uuidStr);
                            return null;
                        });
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
                    logger.warn("[Tropicube] Template lobby '{}' ne peut pas être désactivé — forcé à enabled.", template.getId());
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

                // Volumes (bind mounts, chemins absolus sur l'hôte Docker)
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
                logger.info("[Tropicube] Template chargé : {}", template.getId());
            } catch (Exception e) {
                logger.error("[Tropicube] Erreur chargement template {}", key, e);
            }
        });
    }

    private void cleanupOrphanContainers() {
        Set<String> knownIds = activeInstances.values().stream()
                .map(ServerInstance::getContainerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        dockerManager.cleanupOrphanContainers(knownIds);
        logger.info("[Tropicube] Nettoyage orphelins terminé ({} instances connues).", knownIds.size());
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
                        redisManager.removeInstance(instance.getInstanceId(), instance.getServerType());
                        logger.warn("[Tropicube] Instance Redis sans conteneur actif supprimée : {}",
                                instance.getServerName());
                        continue;
                    }
                    dockerManager.reservePorts(instance);
                    activeInstances.put(instance.getInstanceId(), instance);
                    if (instance.getOnlinePlayers() == 0) {
                        emptySince.put(instance.getInstanceId(), System.currentTimeMillis() / 1000);
                    }
                    registerServerToVelocity(instance);
                    logger.info("[Tropicube] Instance restaurée : {}", instance.getServerName());
                } catch (RuntimeException e) {
                    redisManager.removeInstance(instance.getInstanceId(), instance.getServerType());
                    logger.error("[Tropicube] Instance restaurée invalide, elle sera nettoyée : {}",
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
                            logger.error("[Tropicube] Échec création instance {} au démarrage", template.getId(), ex);
                            return null;
                        });
            }
        });
    }

    /**
     * Crée un nouveau serveur à partir d'un template.
     * @return CompletableFuture avec l'instance créée
     */
    public CompletableFuture<ServerInstance> createServer(String templateId, String customName, boolean whitelisted) {
        return createServer(templateId, customName, whitelisted, Collections.emptyMap());
    }

    /**
     * Crée un nouveau serveur avec des variables d'environnement supplémentaires
     * qui s'ajoutent (et peuvent surcharger) celles du template.
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
                    redisManager.removeInstance(instanceId, template.getServerType());
                    try {
                        dockerManager.removeServer(instance);
                    } catch (Exception cleanupError) {
                        e.addSuppressed(cleanupError);
                    }
                }
                logger.error("[Tropicube] Erreur création serveur {}", serverName, e);
                throw new RuntimeException(e);
            } finally {
                if (pending.decrementAndGet() == 0) pendingCreations.remove(templateId, pending);
            }
        }, scheduler).thenCompose(instance -> waitForServerReady(instance).thenApply(_ -> {
            registerServerToVelocity(instance);
            redisManager.publishServerEvent("SERVER_STARTED",
                    instance.getInstanceId() + ":" + instance.getServerName() + ":" + instance.getServerType());
            logger.info("[Tropicube] Serveur démarré : {} (port {})", instance.getServerName(), instance.getPort());
            return instance;
        }));
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
        logger.info("[Tropicube] Templates publiés dans Redis ({} templates).", templates.size());
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Arrête et supprime un serveur.
     */
    public CompletableFuture<Boolean> stopServer(String instanceId) {
        ServerInstance instance = prepareStop(instanceId);
        if (instance == null) return CompletableFuture.completedFuture(false);
        return transferPlayers(instance, 5).thenApplyAsync(_ -> {
            boolean stopped = dockerManager.stopServer(instance);
            if (stopped) {
                proxy.getServer(instance.getServerName()).ifPresent(s ->
                        proxy.unregisterServer(s.getServerInfo()));
                dockerManager.removeServer(instance);
                activeInstances.remove(instanceId);
                emptySince.remove(instanceId);
                healthFailures.remove(instanceId);
                redisManager.removeInstance(instanceId, instance.getServerType());
                redisManager.publishServerEvent("SERVER_STOPPED", instanceId + ":" + instance.getServerName());
                logger.info("[Tropicube] Serveur arrêté : {}", instance.getServerName());
            }
            return stopped;
        }, scheduler);
    }

    /**
     * Force l'arrêt immédiat d'un serveur (SIGKILL).
     * Migre les joueurs, désenregistre de Velocity et nettoie Redis.
     */
    public CompletableFuture<Boolean> killServer(String instanceId) {
        ServerInstance instance = prepareStop(instanceId);
        if (instance == null) return CompletableFuture.completedFuture(false);
        return transferPlayers(instance, 3).thenApplyAsync(_ -> {
            // Retire l'instance du registre Velocity.
            proxy.getServer(instance.getServerName()).ifPresent(s ->
                    proxy.unregisterServer(s.getServerInfo()));

            boolean killed = dockerManager.killServer(instance);
            dockerManager.removeServer(instance);
            activeInstances.remove(instanceId);
            emptySince.remove(instanceId);
            healthFailures.remove(instanceId);
            redisManager.removeInstance(instanceId, instance.getServerType());
            redisManager.publishServerEvent("SERVER_STOPPED", instanceId + ":" + instance.getServerName());
            logger.info("[Tropicube] Serveur tué (kill) : {}", instance.getServerName());
            return killed;
        }, scheduler);
    }

    private ServerInstance prepareStop(String instanceId) {
        ServerInstance instance = activeInstances.get(instanceId);
        if (instance == null) return null;
        synchronized (instance) {
            if (instance.getStatus() == ServerInstance.Status.STOPPING
                    || instance.getStatus() == ServerInstance.Status.STOPPED) return null;
            instance.setStatus(ServerInstance.Status.STOPPING);
            redisManager.saveInstance(instance);
            return instance;
        }
    }

    private CompletableFuture<Void> transferPlayers(ServerInstance instance, long timeoutSeconds) {
        CompletableFuture<?>[] transfers = proxy.getAllPlayers().stream()
                .filter(player -> player.getCurrentServer()
                        .map(ServerConnection::getServer)
                        .map(server -> server.getServerInfo().getName().equals(instance.getServerName()))
                        .orElse(false))
                .map(player -> {
                    player.sendMessage(languageManager.getComponent(
                            player.getUniqueId(), "proxy.server-shutdown"));
                    return transferToLobby(player);
                })
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(transfers)
                .orTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .exceptionally(error -> {
                    logger.warn("[Tropicube] Délai dépassé pendant le transfert des joueurs de {}",
                            instance.getServerName(), error);
                    return null;
                });
    }

    /**
     * Arrête les tâches du gestionnaire et, si demandé, supprime les serveurs dynamiques.
     * Un redémarrage normal du proxy conserve les conteneurs afin qu'ils soient restaurés
     * par {@link #restoreActiveInstances()} au prochain démarrage.
     */
    public void shutdown(boolean stopDynamicServers) {
        if (stopDynamicServers) {
            stopAllServers();
            return;
        }
        logger.info("[Tropicube] Arrêt du proxy : conservation de {} serveur(s) dynamique(s).",
                activeInstances.size());
        scheduler.shutdownNow();
    }

    /**
     * Arrête et supprime tous les serveurs dynamiques.
     * Les arrêts connus sont parallélisés pour rester dans le stop_grace_period de Compose.
     * Un sweep final force-supprime tout container dynamique restant, y compris ceux
     * encore en CREATING et donc absents de activeInstances.
     */
    public void stopAllServers() {
        logger.info("[Tropicube] Arrêt de tous les serveurs ({})...", activeInstances.size());
        scheduler.shutdownNow();

        try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = activeInstances.values().stream()
                    .map(instance -> exec.submit(() -> {
                        try {
                            dockerManager.stopServer(instance);
                            dockerManager.removeServer(instance);
                            redisManager.removeInstance(instance.getInstanceId(), instance.getServerType());
                        } catch (Exception e) {
                            logger.warn("[Tropicube] Erreur arrêt {}", instance.getServerName(), e);
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
        healthFailures.clear();
        pendingCreations.clear();
        // Sweep final : supprime tout container dynamique encore vivant (démarrage en cours, crash, etc.).
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
        logger.info("[Tropicube] Serveur Velocity enregistré : {} -> {}:{}", instance.getServerName(), address.getHostString(), MINECRAFT_INTERNAL_PORT);
    }

    private CompletableFuture<Boolean> transferToLobby(com.velocitypowered.api.proxy.Player player) {
        Optional<RegisteredServer> lobby = getBestLobby();
        if (lobby.isEmpty()) {
            player.sendMessage(languageManager.getComponent(player.getUniqueId(), "proxy.hub-none"));
            return CompletableFuture.completedFuture(false);
        }
        return player.createConnectionRequest(lobby.orElseThrow()).connect().handle((result, error) -> {
            if (error != null || result == null || !result.isSuccessful()) {
                logger.warn("[Tropicube] Impossible de transférer {} vers le lobby",
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
                        logger.error("[Tropicube] Le serveur {} n'est pas devenu disponible.",
                                instance.getServerName(), ex);
                        try {
                            dockerManager.removeServer(instance);
                        } finally {
                            activeInstances.remove(instance.getInstanceId(), instance);
                            redisManager.removeInstance(instance.getInstanceId(), instance.getServerType());
                        }
                        throw new CompletionException(ex);
                    }
                    instance.setStatus(ServerInstance.Status.GAME_WAITING);
                    instance.setStartedAt(System.currentTimeMillis() / 1000);
                    emptySince.put(instance.getInstanceId(), instance.getStartedAt());
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

            // Scale UP si insuffisant (respecte auto-start : sans auto-start, le min n'est jamais forcé)
            if (template.isAutoStart() && current < template.getMinInstances()) {
                logger.info("[Tropicube] Auto-scale UP : {}", template.getId());
                createServer(template.getId(), null, false)
                        .exceptionally(ex -> {
                            logger.error("[Tropicube] Échec auto-scale UP pour {}", template.getId(), ex);
                            return null;
                        });
            }

            // Auto-stop des serveurs vides
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
                            logger.info("[Tropicube] Auto-stop serveur vide : {}", i.getServerName());
                            stopServer(i.getInstanceId());
                        });
            }
        }), 30, 30, TimeUnit.SECONDS);
    }

    private void startHealthChecker() {
        scheduler.scheduleAtFixedRate(() -> activeInstances.values().forEach(instance -> {
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new InetSocketAddress(
                        instance.getHost() != null ? instance.getHost() : "127.0.0.1",
                        MINECRAFT_INTERNAL_PORT), 2000);
                healthFailures.remove(instance.getInstanceId());
                if (instance.getStatus() == ServerInstance.Status.STARTING) {
                    instance.setStatus(ServerInstance.Status.GAME_WAITING);
                    redisManager.saveInstance(instance);
                }
            } catch (Exception e) {
                if (instance.getStatus() != ServerInstance.Status.STOPPING
                        && instance.getStatus() != ServerInstance.Status.STOPPED
                        && instance.getStatus() != ServerInstance.Status.ERROR) {
                    int failures = healthFailures
                            .computeIfAbsent(instance.getInstanceId(), _ -> new AtomicInteger())
                            .incrementAndGet();
                    logger.warn("[Tropicube] Health check échoué ({}/3) : {}", failures, instance.getServerName());
                    if (failures >= 3) {
                        instance.setStatus(ServerInstance.Status.ERROR);
                        redisManager.saveInstance(instance);
                        killServer(instance.getInstanceId()).exceptionally(error -> {
                            logger.error("[Tropicube] Nettoyage impossible après échec de santé : {}",
                                    instance.getServerName(), error);
                            return false;
                        });
                    }
                }
            }
        }), 60, 60, TimeUnit.SECONDS);
    }

    /**
     * Retourne le meilleur lobby disponible (le moins chargé).
     */
    public Optional<RegisteredServer> getBestLobby() {
        return activeInstances.values().stream()
                .filter(i -> i.getServerType().equals("LOBBY") && i.isJoinable())
                .min(Comparator.comparingInt(ServerInstance::getOnlinePlayers))
                .flatMap(i -> proxy.getServer(i.getServerName()));
    }

    /**
     * Retourne toutes les instances d'un certain type.
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
