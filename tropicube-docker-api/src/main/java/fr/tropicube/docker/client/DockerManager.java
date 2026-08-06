package fr.tropicube.docker.client;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.docker.model.ServerTemplate;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * Gestionnaire principal des containers Docker pour les serveurs Minecraft.
 * Gère le cycle de vie complet : création, démarrage, arrêt et suppression.
 */
public class DockerManager implements Closeable {

    private static final System.Logger LOGGER = System.getLogger(DockerManager.class.getName());
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    /** Port interne utilisé par RCON à l'intérieur du container. */
    private static final int RCON_INTERNAL_PORT = 25575;

    /** Client Docker utilisé pour interagir avec le daemon Docker. */
    private final DockerClient dockerClient;

    /** Nom du réseau Docker sur lequel les containers seront connectés. */
    private final String networkName;

    /** Préfixe appliqué au nom de chaque container créé dynamiquement. */
    private final String containerPrefix;

    /**
     * Pool des ports Minecraft disponibles.
     * La clé est le numéro de port, la valeur indique s'il est déjà utilisé (true = occupé).
     */
    private final Map<Integer, Boolean> portPool;

    /** Borne inférieure de la plage de ports Minecraft. */
    private final int portRangeStart;

    /** Borne supérieure de la plage de ports Minecraft. */
    private final int portRangeEnd;

    /**
     * Pool des ports RCON disponibles.
     * Même principe que {@code portPool} mais pour les connexions RCON.
     */
    private final Map<Integer, Boolean> rconPortPool;

    /** Borne inférieure de la plage de ports RCON. */
    private final int rconPortRangeStart;

    /** Borne supérieure de la plage de ports RCON. */
    private final int rconPortRangeEnd;

    /** Mot de passe partagé pour toutes les connexions RCON. */
    private final String rconPassword;

    /**
     * Répertoire de base sur l'hôte utilisé pour résoudre les chemins relatifs
     * déclarés dans les volumes des templates.
     */
    private final String basePath;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Construit un {@code DockerManager} et initialise la connexion au daemon Docker.
     *
     * @param dockerHost          URI du daemon Docker (ex. {@code unix:///var/run/docker.sock}).
     * @param networkName         Nom du réseau Docker à utiliser (créé s'il n'existe pas).
     * @param containerPrefix     Préfixe des noms de containers.
     * @param portRangeStart      Premier port de la plage Minecraft.
     * @param portRangeEnd        Dernier port de la plage Minecraft.
     * @param rconPortRangeStart  Premier port de la plage RCON.
     * @param rconPortRangeEnd    Dernier port de la plage RCON.
     * @param rconPassword        Mot de passe RCON (null ou vide pour désactiver RCON).
     * @param basePath            Chemin de base pour la résolution des volumes relatifs.
     */
    public DockerManager(String dockerHost, String networkName, String containerPrefix,
                         int portRangeStart, int portRangeEnd,
                          int rconPortRangeStart, int rconPortRangeEnd, String rconPassword,
                          String basePath) {
        this.networkName = requireNonBlank(networkName, "networkName");
        this.containerPrefix = sanitizeName(requireNonBlank(containerPrefix, "containerPrefix"));
        validatePortRange(portRangeStart, portRangeEnd, "Minecraft");
        boolean rconConfigured = rconPassword != null && !rconPassword.isBlank();
        if (rconConfigured) {
            validatePortRange(rconPortRangeStart, rconPortRangeEnd, "RCON");
            if (rangesOverlap(portRangeStart, portRangeEnd, rconPortRangeStart, rconPortRangeEnd)) {
                throw new IllegalArgumentException("Les plages Minecraft et RCON ne doivent pas se chevaucher");
            }
        }
        this.portRangeStart = portRangeStart;
        this.portRangeEnd = portRangeEnd;
        this.rconPortRangeStart = rconPortRangeStart;
        this.rconPortRangeEnd = rconPortRangeEnd;
        this.rconPassword = rconPassword != null ? rconPassword : "";
        // Supprime le slash final du basePath pour éviter les doubles séparateurs
        this.basePath = basePath != null ? basePath.replaceAll("/+$", "") : "";
        this.portPool = new ConcurrentHashMap<>();
        this.rconPortPool = new ConcurrentHashMap<>();

        // Initialise tous les ports de la plage Minecraft comme libres (false = disponible)
        for (int port = portRangeStart; port <= portRangeEnd; port++) {
            portPool.put(port, false);
        }

        // Initialise tous les ports de la plage RCON comme libres
        if (rconConfigured) {
            for (int port = rconPortRangeStart; port <= rconPortRangeEnd; port++) {
                rconPortPool.put(port, false);
            }
        }

        // Construction de la configuration du client Docker
        String validatedDockerHost = requireNonBlank(dockerHost, "dockerHost");
        URI dockerHostUri = URI.create(validatedDockerHost);
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(validatedDockerHost)
                .build();

        // Construction du client HTTP Apache sous-jacent avec des délais raisonnables
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerHostUri)
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);

        // Assure que le réseau Docker existe avant toute opération
        try {
            ensureNetworkExists();
        } catch (RuntimeException e) {
            close();
            throw new IllegalStateException("Impossible d'initialiser le réseau Docker " + networkName, e);
        }
    }

    private static void validatePortRange(int start, int end, String label) {
        if (start < 1 || end > 65_535 || start > end) {
            throw new IllegalArgumentException("Plage de ports " + label + " invalide : " + start + "-" + end);
        }
    }

    private static boolean rangesOverlap(int firstStart, int firstEnd, int secondStart, int secondEnd) {
        return firstStart <= secondEnd && secondStart <= firstEnd;
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " est obligatoire");
        return value;
    }

    /**
     * Vérifie que le réseau Docker {@code networkName} existe et le crée si nécessaire.
     * Le réseau est créé en mode "bridge" (réseau isolé local à l'hôte).
     */
    private void ensureNetworkExists() {
        boolean exists = dockerClient.listNetworksCmd()
                .withNameFilter(networkName)
                .exec()
                .stream()
                .anyMatch(n -> n.getName().equals(networkName));

        if (!exists) {
            dockerClient.createNetworkCmd()
                    .withName(networkName)
                    .withDriver("bridge")
                    .exec();
        }
    }

    /**
     * Télécharge une image Docker depuis le registre si elle n'est pas déjà présente localement.
     *
     * <p>L'attente est limitée à CINQ minutes. Si le thread est interrompu pendant le pull,
     * le flag d'interruption est restauré.
     *
     * @param imageName Nom complet de l'image (ex. {@code itzg/minecraft-server:latest}).
     */
    public void pullImageIfAbsent(String imageName) {
        requireOpen();
        requireNonBlank(imageName, "imageName");
        try {
            // Vérifie si l'image existe déjà localement via un filtre sur la référence
            boolean imageExists = dockerClient.listImagesCmd()
                    .withFilter("reference", List.of(imageName))
                    .exec()
                    .stream()
                    .findFirst()
                    .isPresent();

            if (!imageExists) {
                // Téléchargement bloquant avec un délai maximum de 5 minutes
                boolean completed = dockerClient.pullImageCmd(imageName)
                        .start()
                        .awaitCompletion(5, TimeUnit.MINUTES);
                if (!completed) {
                    throw new IllegalStateException("Délai dépassé lors du téléchargement de l'image " + imageName);
                }
            }
        } catch (InterruptedException e) {
            // Restaure le flag d'interruption conformément aux bonnes pratiques Java
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Téléchargement de l'image interrompu : " + imageName, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Impossible de préparer l'image Docker " + imageName, e);
        }
    }

    /**
     * Alloue un port Minecraft libre depuis le pool.
     * Méthode synchronisée pour éviter les conditions de course lors d'allocations concurrentes.
     *
     * @return Le numéro de port alloué, ou {@code -1} si aucun port n'est disponible.
     */
    private synchronized int allocatePort(int requestedStart, int requestedEnd) {
        int start = requestedStart == 0 ? portRangeStart : Math.max(requestedStart, portRangeStart);
        int end = requestedEnd == 0 ? portRangeEnd : Math.min(requestedEnd, portRangeEnd);
        if (start > end) {
            throw new IllegalStateException("La plage du template ne chevauche pas la plage Minecraft globale");
        }
        for (int port = start; port <= end; port++) {
            if (!portPool.getOrDefault(port, true)) {
                portPool.put(port, true);
                return port;
            }
        }
        return -1; // Aucun port disponible
    }

    /**
     * Alloue un port RCON libre depuis le pool.
     * Méthode synchronisée pour éviter les conditions de course lors d'allocations concurrentes.
     *
     * @return Le numéro de port RCON alloué, ou {@code -1} si aucun port n'est disponible.
     */
    private synchronized int allocateRconPort() {
        for (int port = rconPortRangeStart; port <= rconPortRangeEnd; port++) {
            if (!rconPortPool.getOrDefault(port, true)) {
                rconPortPool.put(port, true);
                return port;
            }
        }
        return -1; // Aucun port RCON disponible
    }

    /** Réserve les ports d'une instance restaurée après un redémarrage du proxy. */
    public synchronized void reservePorts(ServerInstance instance) {
        requireOpen();
        Objects.requireNonNull(instance, "instance");
        reservePort(portPool, instance.getPort(), "Minecraft");
        if (instance.getRconPort() != 0) {
            try {
                reservePort(rconPortPool, instance.getRconPort(), "RCON");
            } catch (RuntimeException e) {
                portPool.put(instance.getPort(), false);
                throw e;
            }
        }
    }

    private static void reservePort(Map<Integer, Boolean> pool, int port, String label) {
        Boolean occupied = pool.get(port);
        if (occupied == null) throw new IllegalStateException("Port " + label + " hors plage : " + port);
        if (occupied) throw new IllegalStateException("Port " + label + " déjà réservé : " + port);
        pool.put(port, true);
    }

    /** Indique si un conteneur existe encore et est actuellement en cours d'exécution. */
    public boolean isContainerRunning(String containerId) {
        requireOpen();
        requireNonBlank(containerId, "containerId");
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            return inspect.getState() != null && Boolean.TRUE.equals(inspect.getState().getRunning());
        } catch (NotFoundException _) {
            return false;
        }
    }

    /**
     * Résout un chemin de volume en remplaçant les sources relatives par un chemin absolu
     * construit à partir de {@code basePath}.
     *
     * <p>Exemple : {@code "./data:/data"} avec {@code basePath="/opt/servers"} devient
     * {@code "/opt/servers/data:/data"}.
     *
     * @param volume Déclaration de volume au format {@code source:destination[:mode]}.
     * @return La déclaration de volume avec la source résolue en chemin absolu si nécessaire.
     */
    private String resolveVolume(String volume) {
        int firstColon = volume.indexOf(':');
        if (firstColon < 0) return volume; // Pas de séparateur : retourné tel quel

        String source = volume.substring(0, firstColon);
        String rest = volume.substring(firstColon); // Inclut ":" + destination [+ ":mode"]

        // Si la source n'est pas un chemin absolu et qu'un basePath est défini, on la préfixe
        if (!source.startsWith("/") && !basePath.isEmpty()) {
            source = basePath + "/" + source.replaceFirst("^\\./", ""); // Supprime le "./" initial si présent
        }
        return source + rest;
    }

    /**
     * Crée et démarre un container Minecraft à partir d'un template.
     *
     * <p>Les étapes sont les suivantes :
     * <ol>
     *   <li>Allocation d'un port Minecraft libre (et d'un port RCON si activé).</li>
     *   <li>Construction des variables d'environnement (EULA, mémoire, RCON, etc.).</li>
     *   <li>Configuration des bindings de ports et des volumes.</li>
     *   <li>Création puis démarrage du container.</li>
     *   <li>Récupération de l'adresse IP interne sur le réseau Docker.</li>
     * </ol>
     *
     * <p>Les entrées de {@code extraEnv} sont injectées <em>après</em> les variables du template,
     * ce qui leur permet de les surcharger avant de produire une liste sans doublon.
     *
     * @param template    Modèle de serveur décrivant l'image, la mémoire, les volumes, etc.
     * @param instanceId  Identifiant unique de l'instance (UUID).
     * @param serverName  Nom lisible du serveur (utilisé dans le nom du container).
     * @param whitelisted Statut de la whitelist du serveur
     * @param extraEnv    Variables d'environnement supplémentaires à injecter (peuvent écraser celles du template).
     * @return            L'instance {@link ServerInstance} représentant le serveur créé et en cours de démarrage.
     * @throws IllegalStateException si aucun port (Minecraft ou RCON) n'est disponible.
     */
    public ServerInstance createServer(ServerTemplate template, String instanceId, String serverName, boolean whitelisted,
                                       Map<String, String> extraEnv) {
        requireOpen();
        Objects.requireNonNull(template, "template").validate();
        requireNonBlank(instanceId, "instanceId");
        requireNonBlank(serverName, "serverName");
        Map<String, String> effectiveExtraEnv = Objects.requireNonNullElse(extraEnv, Map.of());
        // Tente d'allouer un port Minecraft disponible
        int port = allocatePort(template.getMinPort(), template.getMaxPort());
        if (port == -1) {
            throw new IllegalStateException("Aucun port disponible dans la plage " + portRangeStart + "-" + portRangeEnd);
        }

        // RCON est activé uniquement si le pool RCON est non vide ET qu'un mot de passe est défini
        boolean rconEnabled = !rconPortPool.isEmpty() && !rconPassword.isEmpty();
        int rconPort = rconEnabled ? allocateRconPort() : 0;
        if (rconEnabled && rconPort == -1) {
            // Libère le port Minecraft déjà alloué avant de lever l'exception
            releasePort(port);
            throw new IllegalStateException("Aucun port RCON disponible dans la plage " + rconPortRangeStart + "-" + rconPortRangeEnd);
        }

        // Nom du container : préfixe + nom du serveur normalisé + 8 premiers caractères de l'UUID
        String containerName = buildContainerName(serverName, instanceId);

        // Création de l'objet ServerInstance avec les métadonnées de base
        ServerInstance instance = new ServerInstance(instanceId, template.getId(), serverName, port, whitelisted);
        instance.setContainerName(containerName);
        instance.setMaxPlayers(template.getMaxPlayers());
        instance.setServerType(template.getServerType());
        instance.setStatus(ServerInstance.Status.CREATING);
        if (rconEnabled) instance.setRconPort(rconPort);

        String createdContainerId = null;
        try {
            // --- Construction des variables d'environnement ---
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("EULA", "TRUE");
            environment.put("SERVER_NAME", serverName);
            environment.put("INSTANCE_ID", instanceId);
            environment.put("MAX_PLAYERS", Integer.toString(template.getMaxPlayers()));
            environment.put("MEMORY", template.getMinRam() + "M");
            environment.put("MAX_MEMORY", template.getMaxRam() + "M");
            if (rconEnabled) {
                environment.put("ENABLE_RCON", "true");
                environment.put("RCON_PORT", Integer.toString(RCON_INTERNAL_PORT));
                environment.put("RCON_PASSWORD", rconPassword);
            }
            // Variables du template en premier, puis extraEnv pour permettre la surcharge
            putEnvironment(environment, template.getEnvironmentVariables());
            putEnvironment(environment, effectiveExtraEnv);
            List<String> envVars = environment.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList();

            // --- Configuration des bindings de ports ---
            ExposedPort exposedMinecraft = ExposedPort.tcp(25565); // Port Minecraft standard
            Ports portBindings = new Ports();
            portBindings.bind(exposedMinecraft, Ports.Binding.bindIpAndPort("127.0.0.1", port));

            List<ExposedPort> exposedPorts = new ArrayList<>();
            exposedPorts.add(exposedMinecraft);

            if (rconEnabled) {
                ExposedPort exposedRcon = ExposedPort.tcp(RCON_INTERNAL_PORT);
                portBindings.bind(exposedRcon, Ports.Binding.bindIpAndPort("127.0.0.1", rconPort));
                exposedPorts.add(exposedRcon);
            }

            // --- Résolution et configuration des volumes ---
            List<Bind> binds = new ArrayList<>();
            for (String volume : template.getVolumes()) {
                if (volume == null || volume.isBlank()) throw new IllegalArgumentException("Volume Docker vide");
                binds.add(Bind.parse(resolveVolume(volume)));
            }

            // --- Configuration de l'hôte (ressources, réseau, redémarrage) ---
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withPortBindings(portBindings)
                    .withNetworkMode(networkName)
                    .withMemory((long) template.getMaxRam() * 1024 * 1024)              // Limite mémoire dure (en octets)
                    .withMemoryReservation((long) template.getMinRam() * 1024 * 1024)   // Réservation mémoire souple (en octets)
                    .withRestartPolicy(RestartPolicy.noRestart())                        // Pas de redémarrage automatique
                    .withSecurityOpts(List.of("no-new-privileges:true"))
                    .withBinds(binds);

            // --- Création du container ---
            CreateContainerResponse container = dockerClient.createContainerCmd(template.getDockerImage())
                    .withName(containerName)
                    .withEnv(envVars)
                    .withExposedPorts(exposedPorts.toArray(new ExposedPort[0]))
                    .withHostConfig(hostConfig)
                    .withLabels(Map.of(
                            "fr.tropicube.dynamic", "true",
                            "fr.tropicube.instance-id", instanceId,
                            "fr.tropicube.template-id", template.getId()))
                    .exec();

            createdContainerId = container.getId();
            instance.setContainerId(createdContainerId);

            // --- Démarrage du container ---
            dockerClient.startContainerCmd(createdContainerId).exec();
            instance.setStatus(ServerInstance.Status.STARTING);
            instance.setStartedAt(Instant.now().getEpochSecond()); // Horodatage de démarrage

            // --- Récupération de l'IP interne sur le réseau Docker ---
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(createdContainerId).exec();
            if (inspect.getNetworkSettings() != null && inspect.getNetworkSettings().getNetworks() != null) {
                ContainerNetwork net = inspect.getNetworkSettings().getNetworks().get(networkName);
                if (net != null && net.getIpAddress() != null && !net.getIpAddress().isBlank()) {
                    instance.setHost(net.getIpAddress());
                }
            }
            if (instance.getHost() == null) {
                throw new IllegalStateException("Le conteneur n'est pas connecté au réseau Docker " + networkName);
            }

            return instance;

        } catch (Exception e) {
            if (createdContainerId != null) {
                try {
                    dockerClient.removeContainerCmd(createdContainerId).withForce(true).withRemoveVolumes(true).exec();
                } catch (Exception cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            // En cas d'erreur, libère les ports alloués et marque l'instance en erreur
            releasePort(port);
            if (rconEnabled && rconPort != 0) releaseRconPort(rconPort);
            instance.setStatus(ServerInstance.Status.ERROR);
            throw e;
        }
    }

    private static String resolveEnvironmentValue(String value) {
        Objects.requireNonNull(value, "Valeur de variable d'environnement");
        if (!value.startsWith("${") || !value.endsWith("}")) return value;
        String name = value.substring(2, value.length() - 1);
        String resolved = System.getenv(name);
        if (resolved == null)
            throw new IllegalStateException("Variable d'environnement requise absente : " + name);
        return resolved;
    }

    private static void putEnvironment(Map<String, String> target, Map<String, String> additions) {
        additions.forEach((name, value) -> {
            if (name == null || !ENVIRONMENT_NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Nom de variable d'environnement invalide : " + name);
            }
            target.put(name, resolveEnvironmentValue(value));
        });
    }

    private String buildContainerName(String serverName, String instanceId) {
        String serverPart = sanitizeName(serverName);
        String instancePart = sanitizeName(instanceId);
        if (instancePart.length() > 12) instancePart = instancePart.substring(0, 12);
        String suffix = "-" + instancePart;
        String base = containerPrefix + "-" + serverPart;
        int maxBaseLength = 128 - suffix.length();
        if (base.length() > maxBaseLength) base = base.substring(0, maxBaseLength);
        return base + suffix;
    }

    private static String sanitizeName(String value) {
        String sanitized = value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_.-]+", "-")
                .replaceAll("^[_.-]+|[_.-]+$", "");
        if (sanitized.isBlank()) throw new IllegalArgumentException("Nom Docker invalide : " + value);
        return sanitized;
    }

    /**
     * Surveille les logs du container et complète le {@link CompletableFuture} retourné
     * dès que le serveur Minecraft est prêt à accepter des connexions.
     *
     * <p>La détection s'appuie sur le message {@code "Done (Xs)!"} émis par Paper/Spigot
     * dans la sortie standard. Un timeout est appliqué : si le serveur ne démarre pas
     * dans le délai imparti, le future échoue avec une {@link java.util.concurrent.TimeoutException}.
     *
     * @param containerId    Identifiant Docker du container à surveiller.
     * @param timeoutSeconds Délai maximal d'attente en secondes avant échec.
     * @return Un {@code CompletableFuture<Void>} complété quand le serveur est prêt,
     *         ou échoué en cas de timeout ou d'erreur de log.
     */
    public CompletableFuture<Void> waitForServerReadyViaLogs(String containerId, long timeoutSeconds) {
        requireOpen();
        requireNonBlank(containerId, "containerId");
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds doit être strictement positif");
        CompletableFuture<Void> future = new CompletableFuture<>();

        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                // Si le future est déjà résolu, on ignore les trames suivantes
                if (future.isDone()) return;
                String line = new String(frame.getPayload(), StandardCharsets.UTF_8).trim();
                // Détection du message de fin de démarrage de Paper/Spigot : "Done (Xs)!"
                if (line.contains("Done (") && line.contains("s)!")) {
                    future.complete(null);
                    try { close(); } catch (IOException ignored) {}
                }
            }

            @Override
            public void onError(Throwable t) {
                // Propage l'erreur au future si celui-ci n'est pas encore résolu
                if (!future.isDone()) future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                if (!future.isDone()) {
                    future.completeExceptionally(new IllegalStateException(
                            "Le flux de logs s'est terminé avant que le serveur soit prêt"));
                }
            }
        };

        try {
            // Démarre le suivi des logs en temps réel (stdout + stderr)
            dockerClient.logContainerCmd(containerId)
                    .withFollowStream(true)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(callback);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        // Applique le timeout ; ferme le flux de logs dans tous les cas (succès, timeout ou erreur)
        CompletableFuture<Void> result = future.orTimeout(timeoutSeconds, TimeUnit.SECONDS);
        result.whenComplete((_, _) -> {
            try { callback.close(); } catch (IOException ignored) {}
        });
        return result;
    }

    /**
     * Libère un port Minecraft précédemment alloué, le rendant à nouveau disponible dans le pool.
     *
     * @param port Le numéro de port à libérer.
     */
    private synchronized void releasePort(int port) {
        if (portPool.containsKey(port)) portPool.put(port, false);
    }

    /**
     * Libère un port RCON précédemment alloué, le rendant à nouveau disponible dans le pool.
     *
     * @param port Le numéro de port RCON à libérer.
     */
    private synchronized void releaseRconPort(int port) {
        if (rconPortPool.containsKey(port)) rconPortPool.put(port, false);
    }

    /**
     * Arrête proprement un container en envoyant un signal d'arrêt et en attendant
     * jusqu'à 30 secondes que le processus se termine.
     *
     * <p>Les ports alloués sont libérés après l'arrêt.
     *
     * @param instance L'instance du serveur à arrêter.
     * @return {@code true} si l'arrêt s'est déroulé sans exception, {@code false} sinon.
     */
    public boolean stopServer(ServerInstance instance) {
        requireOpen();
        requireContainer(instance);
        try {
            instance.setStatus(ServerInstance.Status.STOPPING);
            dockerClient.stopContainerCmd(instance.getContainerId())
                    .withTimeout(30) // Délai d'attente avant SIGKILL (en secondes)
                    .exec();
            instance.setStatus(ServerInstance.Status.STOPPED);
            releasePort(instance.getPort());
            if (instance.getRconPort() != 0) releaseRconPort(instance.getRconPort());
            return true;
        } catch (NotFoundException _) {
            instance.setStatus(ServerInstance.Status.STOPPED);
            releasePorts(instance);
            return true;
        } catch (Exception e) {
            instance.setStatus(ServerInstance.Status.ERROR);
            LOGGER.log(System.Logger.Level.WARNING, "Impossible d'arrêter le conteneur " + instance.getContainerId(), e);
            return false;
        }
    }

    /**
     * Force l'arrêt immédiat d'un container par envoi d'un signal SIGKILL.
     *
     * <p>Contrairement à {@link #stopServer}, cette méthode ne laisse pas au processus
     * le temps de s'arrêter proprement. Les ports sont libérés immédiatement après.
     *
     * @param instance L'instance du serveur à tuer.
     * @return {@code true} si l'opération a réussi ou si le container était déjà absent,
     *         {@code false} en cas d'autre erreur.
     */
    public boolean killServer(ServerInstance instance) {
        requireOpen();
        requireContainer(instance);
        instance.setStatus(ServerInstance.Status.STOPPING);
        try {
            dockerClient.killContainerCmd(instance.getContainerId()).exec();
        } catch (NotFoundException _) {
            // Container déjà absent : on considère quand même le kill comme réussi
        } catch (Exception e) {
            instance.setStatus(ServerInstance.Status.ERROR);
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de tuer le conteneur " + instance.getContainerId(), e);
            return false;
        }
        instance.setStatus(ServerInstance.Status.STOPPED);
        releasePorts(instance);
        return true;
    }

    /**
     * Supprime définitivement un container Docker.
     *
     * <p>Le container est supprimé en force (même s'il est encore en cours d'exécution)
     * et ses volumes anonymes sont également supprimés.
     * Si le container est introuvable (déjà supprimé ou crashé), l'opération est ignorée silencieusement.
     *
     * @param instance L'instance du serveur à supprimer.
     */
    public void removeServer(ServerInstance instance) {
        requireOpen();
        requireContainer(instance);
        boolean removed = false;
        try {
            dockerClient.removeContainerCmd(instance.getContainerId())
                    .withForce(true)          // Suppression forcée même si le container tourne encore
                    .withRemoveVolumes(true)  // Suppression des volumes anonymes associés
                    .exec();
            removed = true;
        } catch (NotFoundException e) {
            // Container déjà absent (ex. crash avant suppression explicite) — considéré comme un succès
            removed = true;
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de supprimer le conteneur " + instance.getContainerId(), e);
        } finally {
            if (removed) releasePorts(instance);
        }
    }

    private void releasePorts(ServerInstance instance) {
        releasePort(instance.getPort());
        if (instance.getRconPort() != 0) releaseRconPort(instance.getRconPort());
    }

    private static void requireContainer(ServerInstance instance) {
        Objects.requireNonNull(instance, "instance");
        requireNonBlank(instance.getContainerId(), "instance.containerId");
    }

    /**
     * Supprime en force tous les containers Docker marqués {@code fr.tropicube.dynamic=true}.
     *
     * <p>Cette méthode est appelée lors du shutdown de l'application pour garantir qu'aucun
     * container dynamique ne survit, y compris ceux encore en cours de création et donc
     * absents du cache d'instances.
     */
    public void removeAllDynamicContainers() {
        requireOpen();
        try {
            // Liste tous les containers dynamiques, qu'ils soient en cours d'exécution ou arrêtés
            List<Container> containers = dockerClient.listContainersCmd()
                    .withLabelFilter(List.of("fr.tropicube.dynamic=true"))
                    .withShowAll(true)
                    .exec();
            for (Container container : containers) {
                try {
                    dockerClient.removeContainerCmd(container.getId())
                            .withForce(true)
                            .withRemoveVolumes(true)
                            .exec();
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Impossible de supprimer le conteneur dynamique " + container.getId(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de lister les conteneurs dynamiques", e);
        }
    }

    /**
     * Nettoie les containers orphelins laissés par un crash précédent.
     *
     * <p>Parcourt tous les containers marqués {@code fr.tropicube.dynamic=true} et supprime
     * ceux dont l'identifiant n'est pas dans l'ensemble {@code knownContainerIds} fourni.
     * Cette méthode est typiquement appelée au démarrage de l'application.
     *
     * @param knownContainerIds Ensemble des IDs de containers actuellement connus et gérés.
     */
    public void cleanupOrphanContainers(Set<String> knownContainerIds) {
        requireOpen();
        Set<String> knownIds = Set.copyOf(Objects.requireNonNull(knownContainerIds, "knownContainerIds"));
        try {
            // Liste tous les containers dynamiques, y compris ceux arrêtés
            List<Container> candidates = dockerClient.listContainersCmd()
                    .withLabelFilter(List.of("fr.tropicube.dynamic=true"))
                    .withShowAll(true)
                    .exec();
            for (Container container : candidates) {
                if (!knownIds.contains(container.getId())) {
                    try {
                        dockerClient.removeContainerCmd(container.getId())
                                .withForce(true)
                                .withRemoveVolumes(true)
                                .exec();
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Impossible de supprimer le conteneur orphelin " + container.getId(), e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de nettoyer les conteneurs orphelins", e);
        }
    }

    /**
     * Ferme le client Docker et libère les ressources associées (connexions HTTP, threads…).
     * Implémentation de {@link Closeable} pour permettre l'utilisation dans un try-with-resources.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            dockerClient.close();
        } catch (IOException e) {
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de fermer le client Docker", e);
        }
    }

    private void requireOpen() {
        if (closed.get()) throw new IllegalStateException("DockerManager est fermé");
    }
}
