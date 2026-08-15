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
 * Primary Docker container manager for Minecraft servers.
 * Manages the complete lifecycle: creation, startup, shutdown, and deletion.
 */
public class DockerManager implements Closeable {

    private static final System.Logger LOGGER = System.getLogger(DockerManager.class.getName());
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String DYNAMIC_LABEL = "fr.tropicube.dynamic";
    private static final String INSTANCE_ID_LABEL = "fr.tropicube.instance-id";
    private static final String TEMPLATE_ID_LABEL = "fr.tropicube.template-id";
    private static final String DATA_VOLUME_PATH = "/data";
    private static final int MAX_VOLUME_NAME_LENGTH = 255;

    /** Internal port used by RCON inside the container. */
    private static final int RCON_INTERNAL_PORT = 25575;

    /** Docker client used to interact with the Docker daemon. */
    private final DockerClient dockerClient;

    /** Name of the Docker network on which the containers will be connected. */
    private final String networkName;

    /** Prefix applied to the name of each dynamically created container. */
    private final String containerPrefix;

    /**
     * Pool of available Minecraft ports.
     * The key is the port number, the value indicates whether it is already in use (true = busy).
     */
    private final Map<Integer, Boolean> portPool;

    /** Lower bound of the Minecraft ports range. */
    private final int portRangeStart;

    /** Upper bound of the Minecraft ports range. */
    private final int portRangeEnd;

    /**
     * Pool of available RCON ports.
     * Same principle as {@code portPool} but for RCON connections.
     */
    private final Map<Integer, Boolean> rconPortPool;

    /** Lower bound of the RCON port range. */
    private final int rconPortRangeStart;

    /** Upper bound of the RCON port range. */
    private final int rconPortRangeEnd;

    /** Shared password for all RCON connections. */
    private final String rconPassword;

    /**
     * Base directory on host used to resolve relative paths
     * declared in the template volumes.
     */
    private final String basePath;
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * Builds a {@code DockerManager} and initializes the connection to the Docker daemon.
     *
     * @param dockerHost          Docker daemon URI (for example {@code unix:///var/run/docker.sock}).
     * @param networkName Name of the Docker network to use (created if it does not exist).
     * @param containerPrefix Prefix of container names.
     * @param portRangeStart      First port in the Minecraft range.
     * @param portRangeEnd        Last port in the Minecraft range.
     * @param rconPortRangeStart  First port in the RCON range.
     * @param rconPortRangeEnd    Last port in the RCON range.
     * @param rconPassword RCON password (null or empty to disable RCON).
     * @param basePath Basic path for solving relative volumes.
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
        // Remove trailing slash from basePath to avoid double separators
        this.basePath = basePath != null ? basePath.replaceAll("/+$", "") : "";
        this.portPool = new ConcurrentHashMap<>();
        this.rconPortPool = new ConcurrentHashMap<>();

        // Initializes all ports in the Minecraft range as free (false = available)
        for (int port = portRangeStart; port <= portRangeEnd; port++) {
            portPool.put(port, false);
        }

        // Initializes all ports in the RCON range as free
        if (rconConfigured) {
            for (int port = rconPortRangeStart; port <= rconPortRangeEnd; port++) {
                rconPortPool.put(port, false);
            }
        }

        // Build the Docker client configuration
        String validatedDockerHost = requireNonBlank(dockerHost, "dockerHost");
        URI dockerHostUri = URI.create(validatedDockerHost);
        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(validatedDockerHost)
                .build();

        // Building the underlying Apache HTTP client with reasonable delays
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerHostUri)
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);

        // Ensures that the Docker network exists before any operation
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
     * Verifies that the Docker network {@code networkName} exists and creates it if necessary.
     * The network is created in “bridge” mode (isolated network local to the host).
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
     * Downloads a Docker image from the registry if it is not already present locally.
     *
     * <p>The wait is limited to FIVE minutes. If the thread is interrupted during the pull,
     * the interrupt flag is restored.
     *
     * @param imageName Full name of the image (e.g. {@code itzg/minecraft-server:latest}).
     */
    public void pullImageIfAbsent(String imageName) {
        requireOpen();
        requireNonBlank(imageName, "imageName");
        try {
            // Checks if the image already exists locally via a filter on the reference
            boolean imageExists = dockerClient.listImagesCmd()
                    .withFilter("reference", List.of(imageName))
                    .exec()
                    .stream()
                    .findFirst()
                    .isPresent();

            if (!imageExists) {
                // Blocking download with a maximum delay of 5 minutes
                boolean completed = dockerClient.pullImageCmd(imageName)
                        .start()
                        .awaitCompletion(5, TimeUnit.MINUTES);
                if (!completed) {
                    throw new IllegalStateException("Délai dépassé lors du téléchargement de l'image " + imageName);
                }
            }
        } catch (InterruptedException e) {
            // Restores the interrupt flag according to Java best practices
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Téléchargement de l'image interrompu : " + imageName, e);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Impossible de préparer l'image Docker " + imageName, e);
        }
    }

    /**
     * Allocates a free Minecraft port from the pool.
     * Synchronized method to avoid race conditions during concurrent allocations.
     *
     * @return The allocated port number, or {@code -1} if no port is available.
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
     * Allocates a free RCON port from the pool.
     * Synchronized method to avoid race conditions during concurrent allocations.
     *
     * @return The allocated RCON port number, or {@code -1} if no port is available.
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

    /** Reserves ports for a restored instance after a proxy restart. */
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

    /** Indicates whether a container still exists and is currently running. */
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
     * Resolves a volume path by replacing relative sources with an absolute path
     * built from {@code basePath}.
     *
     * <p>Example: {@code "./data:/data"} with {@code basePath="/opt/servers"} becomes
     * {@code "/opt/servers/data:/data"}.
     *
     * @param volume Volume declaration in {@code source:destination[:mode]} format.
     * @return The volume declaration with the source resolved to absolute path if necessary.
     */
    private String resolveVolume(String volume) {
        int firstColon = volume.indexOf(':');
        if (firstColon < 0) return volume; // Pas de séparateur : retourné tel quel

        String source = volume.substring(0, firstColon);
        String rest = volume.substring(firstColon); // Inclut ":" + destination [+ ":mode"]

        // If the source is not an absolute path and a basePath is defined, we prefix it
        if (!source.startsWith("/") && !basePath.isEmpty()) {
            source = basePath + "/" + source.replaceFirst("^\\./", ""); // Supprime le "./" initial si présent
        }
        return source + rest;
    }

    /**
     * Creates and starts a Minecraft container from a template.
     *
     * <p>The steps are as follows:
     * <ol>
     * <li>Allocation of a free Minecraft port (and an RCON port if enabled).</li>
     * <li>Construction of environment variables (EULA, memory, RCON, etc.).</li>
     * <li>Configuration of port bindings and volumes.</li>
     * <li>Creation then start of the container.</li>
     * <li>Retrieving the internal IP address on the Docker network.</li>
     * </ol>
     *
     * <p>The entries of {@code extraEnv} are injected <em>after</em> the template variables,
     * which allows them to overload them before producing a list without duplicates.
     *
     * @param template Server model describing image, memory, volumes, etc.
     * @param instanceId Unique identifier of the instance (UUID).
     * @param serverName Readable name of the server (used in the container name).
     * @param whitelisted Server whitelist status
     * @param extraEnv Additional environment variables to inject (can overwrite those in the template).
     * @return The {@link ServerInstance} instance representing the server created and being started.
     * @throws IllegalStateException if no port (Minecraft or RCON) is available.
     */
    public ServerInstance createServer(ServerTemplate template, String instanceId, String serverName, boolean whitelisted,
                                       Map<String, String> extraEnv) {
        requireOpen();
        Objects.requireNonNull(template, "template").validate();
        requireNonBlank(instanceId, "instanceId");
        requireNonBlank(serverName, "serverName");
        Map<String, String> effectiveExtraEnv = Objects.requireNonNullElse(extraEnv, Map.of());
        // Try to allocate an available Minecraft port
        int port = allocatePort(template.getMinPort(), template.getMaxPort());
        if (port == -1) {
            throw new IllegalStateException("Aucun port disponible dans la plage " + portRangeStart + "-" + portRangeEnd);
        }

        // RCON is enabled only if the RCON pool is non-empty AND a password is set
        boolean rconEnabled = !rconPortPool.isEmpty() && !rconPassword.isEmpty();
        int rconPort = rconEnabled ? allocateRconPort() : 0;
        if (rconEnabled && rconPort == -1) {
            // Free the already allocated Minecraft port before throwing the exception
            releasePort(port);
            throw new IllegalStateException("Aucun port RCON disponible dans la plage " + rconPortRangeStart + "-" + rconPortRangeEnd);
        }

        // Container name: prefix + standardized server name + first 8 characters of the UUID
        String containerName = buildContainerName(serverName, instanceId);

        // Creating the ServerInstance object with basic metadata
        ServerInstance instance = new ServerInstance(instanceId, template.getId(), serverName, port, whitelisted);
        instance.setContainerName(containerName);
        instance.setMaxPlayers(template.getMaxPlayers());
        instance.setServerType(template.getServerType());
        instance.setStatus(ServerInstance.Status.CREATING);
        if (rconEnabled) instance.setRconPort(rconPort);

        String createdContainerId = null;
        String dataVolumeName = null;
        try {
            // --- Construction of environment variables ---
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
            // Template variables first, then extraEnv to allow overloading
            putEnvironment(environment, template.getEnvironmentVariables());
            putEnvironment(environment, effectiveExtraEnv);
            List<String> envVars = environment.entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList();

            // --- Configuring port bindings ---
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

            // ---Resolving and configuring volumes ---
            List<Bind> binds = new ArrayList<>();
            for (String volume : template.getVolumes()) {
                if (volume == null || volume.isBlank()) throw new IllegalArgumentException("Volume Docker vide");
                Bind bind = Bind.parse(resolveVolume(volume));
                if (DATA_VOLUME_PATH.equals(bind.getVolume().getPath())) {
                    throw new IllegalArgumentException("Le chemin Docker " + DATA_VOLUME_PATH
                            + " est réservé au volume éphémère de l'instance");
                }
                binds.add(bind);
            }
            dataVolumeName = createDynamicDataVolume(instanceId, template.getId());
            binds.add(new Bind(dataVolumeName, new Volume(DATA_VOLUME_PATH)));

            // --- Host configuration (resources, network, reboot) ---
            HostConfig hostConfig = HostConfig.newHostConfig()
                    .withPortBindings(portBindings)
                    .withNetworkMode(networkName)
                    .withMemory((long) template.getMaxRam() * 1024 * 1024)              // Limite mémoire dure (en octets)
                    .withMemoryReservation((long) template.getMinRam() * 1024 * 1024)   // Réservation mémoire souple (en octets)
                    .withRestartPolicy(RestartPolicy.noRestart())                        // Pas de redémarrage automatique
                    .withSecurityOpts(List.of("no-new-privileges:true"))
                    .withBinds(binds);

            // --- Creation of the container ---
            CreateContainerResponse container = dockerClient.createContainerCmd(template.getDockerImage())
                    .withName(containerName)
                    .withEnv(envVars)
                    .withExposedPorts(exposedPorts.toArray(new ExposedPort[0]))
                    .withHostConfig(hostConfig)
                    .withLabels(Map.of(
                            DYNAMIC_LABEL, "true",
                            INSTANCE_ID_LABEL, instanceId,
                            TEMPLATE_ID_LABEL, template.getId()))
                    .exec();

            createdContainerId = container.getId();
            instance.setContainerId(createdContainerId);

            // --- Starting the container ---
            dockerClient.startContainerCmd(createdContainerId).exec();
            instance.setStatus(ServerInstance.Status.STARTING);
            instance.setStartedAt(Instant.now().getEpochSecond()); // Horodatage de démarrage

            // --- Retrieving internal IP from Docker network ---
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
            if (dataVolumeName != null) {
                try {
                    removeVolume(dataVolumeName);
                } catch (Exception cleanupFailure) {
                    e.addSuppressed(cleanupFailure);
                }
            }
            // On error, frees the allocated ports and marks the instance in error
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

    static String buildDataVolumeName(String containerPrefix, String instanceId) {
        String prefix = sanitizeName(requireNonBlank(containerPrefix, "containerPrefix"));
        String instance = sanitizeName(requireNonBlank(instanceId, "instanceId"));
        String suffix = "-data-" + instance;
        int maxPrefixLength = MAX_VOLUME_NAME_LENGTH - suffix.length();
        if (maxPrefixLength < 1) {
            throw new IllegalArgumentException("Identifiant d'instance trop long pour un volume Docker");
        }
        if (prefix.length() > maxPrefixLength) prefix = prefix.substring(0, maxPrefixLength);
        return prefix + suffix;
    }

    private String createDynamicDataVolume(String instanceId, String templateId) {
        String volumeName = buildDataVolumeName(containerPrefix, instanceId);
        dockerClient.createVolumeCmd()
                .withName(volumeName)
                .withLabels(Map.of(
                        DYNAMIC_LABEL, "true",
                        INSTANCE_ID_LABEL, instanceId,
                        TEMPLATE_ID_LABEL, templateId))
                .exec();
        return volumeName;
    }

    private void removeDynamicDataVolume(String instanceId) {
        removeVolume(buildDataVolumeName(containerPrefix, instanceId));
    }

    private void removeVolume(String volumeName) {
        try {
            dockerClient.removeVolumeCmd(volumeName).exec();
        } catch (NotFoundException _) {
            // Volume already absent: cleanup is idempotent.
        }
    }

    /**
     * Monitors the container logs and completes the returned {@link CompletableFuture}
     * as soon as the Minecraft server is ready to accept connections.
     *
     * <p>Detection is based on the {@code "Done (Xs)!"} message sent by Paper/Spigot
     * in standard output. A timeout is applied: if the server does not start
     * within the time limit, the future fails with a {@link java.util.concurrent.TimeoutException}.
     *
     * @param containerId Docker identifier of the container to monitor.
     * @param timeoutSeconds Maximum time to wait in seconds before failure.
     * @return A {@code CompletableFuture<Void>} completed when the server is ready,
     * or failed in case of timeout or log error.
     */
    public CompletableFuture<Void> waitForServerReadyViaLogs(String containerId, long timeoutSeconds) {
        requireOpen();
        requireNonBlank(containerId, "containerId");
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("timeoutSeconds doit être strictement positif");
        CompletableFuture<Void> future = new CompletableFuture<>();

        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                // If the future is already resolved, we ignore the following frames
                if (future.isDone()) return;
                String line = new String(frame.getPayload(), StandardCharsets.UTF_8).trim();
                // Detection of Paper/Spigot end of startup message: “Done (Xs)!”
                if (line.contains("Done (") && line.contains("s)!")) {
                    future.complete(null);
                    try { close(); } catch (IOException ignored) {}
                }
            }

            @Override
            public void onError(Throwable t) {
                // Propagate the error to the future if it is not yet resolved
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
            // Start real-time log tracking (stdout + stderr)
            dockerClient.logContainerCmd(containerId)
                    .withFollowStream(true)
                    .withStdOut(true)
                    .withStdErr(true)
                    .exec(callback);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        // Apply the timeout; closes the log stream in all cases (success, timeout or error)
        CompletableFuture<Void> result = future.orTimeout(timeoutSeconds, TimeUnit.SECONDS);
        result.whenComplete((_, _) -> {
            try { callback.close(); } catch (IOException ignored) {}
        });
        return result;
    }

    /**
     * Frees a previously allocated Minecraft port, making it available in the pool again.
     *
     * @param port The port number to release.
     */
    private synchronized void releasePort(int port) {
        if (portPool.containsKey(port)) portPool.put(port, false);
    }

    /**
     * Frees a previously allocated RCON port, making it available in the pool again.
     *
     * @param port The RCON port number to release.
     */
    private synchronized void releaseRconPort(int port) {
        if (rconPortPool.containsKey(port)) rconPortPool.put(port, false);
    }

    /**
     * Properly stops a container by sending a stop signal and waiting
     * up to 30 seconds for the process to complete.
     *
     * <p>Allocated ports are freed after shutdown.
     *
     * @param instance The server instance to stop.
     * @return {@code true} if the shutdown took place without exception, {@code false} otherwise.
     */
    public boolean stopServer(ServerInstance instance) {
        requireOpen();
        requireContainer(instance);
        try {
            instance.setStatus(ServerInstance.Status.STOPPING);
            DockerStopOperation.Result result = DockerStopOperation.execute(
                    () -> dockerClient.stopContainerCmd(instance.getContainerId())
                            .withTimeout(30) // Délai d'attente avant SIGKILL (en secondes)
                            .exec(),
                    () -> isContainerRunning(instance.getContainerId()));
            if (result != DockerStopOperation.Result.COMPLETED) {
                LOGGER.log(System.Logger.Level.INFO,
                        "Arrêt Docker réconcilié pour " + instance.getContainerId() + " : " + result);
            }
            instance.setStatus(ServerInstance.Status.STOPPED);
            releasePort(instance.getPort());
            if (instance.getRconPort() != 0) releaseRconPort(instance.getRconPort());
            return true;
        } catch (Exception e) {
            instance.setStatus(ServerInstance.Status.ERROR);
            LOGGER.log(System.Logger.Level.WARNING, "Impossible d'arrêter le conteneur " + instance.getContainerId(), e);
            return false;
        }
    }

    /**
     * Forces the immediate shutdown of a container by sending a SIGKILL signal.
     *
     * <p>Unlike {@link #stopServer}, this method does not leave the process
     * time to stop properly. The ports are released immediately afterwards.
     *
     * @param instance The server instance to kill.
     * @return {@code true} if the operation was successful or if the container was already missing,
     *         {@code false} for any other error.
     */
    public boolean killServer(ServerInstance instance) {
        requireOpen();
        requireContainer(instance);
        instance.setStatus(ServerInstance.Status.STOPPING);
        try {
            dockerClient.killContainerCmd(instance.getContainerId()).exec();
        } catch (NotFoundException _) {
            // Container already absent: we still consider the kill as successful
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
     * Permanently delete a Docker container.
     *
     * <p>The container is forcefully deleted (even if it is still running)
     * and its legacy anonymous volumes are also deleted. The managed ephemeral
     * {@code /data} volume is deleted explicitly, including when the container
     * has already disappeared.
     * If the container is not found (already deleted or crashed), the operation is silently ignored.
     *
     * @param instance The server instance to delete.
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
            // Container already missing (e.g. crash before explicit deletion) — considered a success
            removed = true;
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de supprimer le conteneur " + instance.getContainerId(), e);
        } finally {
            if (removed) {
                try {
                    removeDynamicDataVolume(instance.getInstanceId());
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Impossible de supprimer le volume de l'instance " + instance.getInstanceId(), e);
                }
                releasePorts(instance);
            }
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
     * Force delete all Docker containers marked {@code fr.tropicube.dynamic=true}.
     *
     * <p>This method is called during application shutdown to ensure that no
     * dynamic container does not survive, including those still being created and therefore
     * absent from the instance cache.
     */
    public void removeAllDynamicContainers() {
        requireOpen();
        try {
            // Lists all dynamic containers, whether running or stopped
            List<Container> containers = dockerClient.listContainersCmd()
                    .withLabelFilter(List.of(DYNAMIC_LABEL + "=true"))
                    .withShowAll(true)
                    .exec();
            for (Container container : containers) {
                try {
                    dockerClient.removeContainerCmd(container.getId())
                            .withForce(true)
                            .withRemoveVolumes(true)
                            .exec();
                    removeDynamicDataVolume(container.getLabels().get(INSTANCE_ID_LABEL));
                } catch (Exception e) {
                    LOGGER.log(System.Logger.Level.WARNING,
                            "Impossible de supprimer le conteneur dynamique " + container.getId(), e);
                }
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de lister les conteneurs dynamiques", e);
        }
        cleanupDynamicDataVolumes(Set.of());
    }

    /**
     * Cleans up orphaned containers left over from a previous crash.
     *
     * <p> Scans all containers marked {@code fr.tropicube.dynamic=true} and deletes
     * those whose identifier is not in the {@code knownContainerIds} set provided.
     * This method is typically called when the application starts.
     *
     * @param knownContainerIds Set of currently known and managed container IDs.
     */
    public void cleanupOrphanContainers(Set<String> knownContainerIds) {
        requireOpen();
        Set<String> knownIds = Set.copyOf(Objects.requireNonNull(knownContainerIds, "knownContainerIds"));
        try {
            // List all dynamic containers, including stopped ones
            List<Container> candidates = dockerClient.listContainersCmd()
                    .withLabelFilter(List.of(DYNAMIC_LABEL + "=true"))
                    .withShowAll(true)
                    .exec();
            Set<String> retainedInstanceIds = new HashSet<>();
            for (Container container : candidates) {
                String instanceId = container.getLabels().get(INSTANCE_ID_LABEL);
                if (knownIds.contains(container.getId())) {
                    if (instanceId != null && !instanceId.isBlank()) retainedInstanceIds.add(instanceId);
                } else {
                    try {
                        dockerClient.removeContainerCmd(container.getId())
                                .withForce(true)
                                .withRemoveVolumes(true)
                                .exec();
                        if (instanceId != null && !instanceId.isBlank()) removeDynamicDataVolume(instanceId);
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Impossible de supprimer le conteneur orphelin " + container.getId(), e);
                    }
                }
            }
            cleanupDynamicDataVolumes(retainedInstanceIds);
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de nettoyer les conteneurs orphelins", e);
        }
    }

    private void cleanupDynamicDataVolumes(Set<String> retainedInstanceIds) {
        try {
            var response = dockerClient.listVolumesCmd()
                    .withFilter("label", List.of(DYNAMIC_LABEL + "=true"))
                    .exec();
            if (response.getVolumes() == null) return;
            for (var volume : response.getVolumes()) {
                Map<String, String> labels = volume.getLabels();
                String instanceId = labels != null ? labels.get(INSTANCE_ID_LABEL) : null;
                if (instanceId != null && !retainedInstanceIds.contains(instanceId)) {
                    try {
                        removeVolume(volume.getName());
                    } catch (Exception e) {
                        LOGGER.log(System.Logger.Level.WARNING,
                                "Impossible de supprimer le volume dynamique orphelin " + volume.getName(), e);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(System.Logger.Level.WARNING, "Impossible de nettoyer les volumes dynamiques orphelins", e);
        }
    }

    /**
     * Close the Docker client and release the associated resources (HTTP connections, threads, etc.).
     * Implemented {@link Closeable} to allow use in a try-with-resources.
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
