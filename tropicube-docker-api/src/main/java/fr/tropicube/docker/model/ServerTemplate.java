package fr.tropicube.docker.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a Minecraft server template used to create Docker containers.
 * <p>
 * A template is the "recipe" from which one or more {@code ServerInstance}
 * are instantiated. It defines the Docker image, the allocated resources, the rules of
 * auto start/stop and limits on the number of concurrent instances.
 * <p>
 * Templates are typically loaded from a database or file.
 * configuration, then stored in Redis to be accessible to all services.
 */
public class ServerTemplate {

    /**
     * Unique identifier of the template (e.g. "lobby", "survival-1").
     */
    private String id;

    /**
     * Readable name of the template, displayed in the administration interfaces.
     */
    private String name;

    /**
     * Docker image used to create the containers (e.g. "tropicube/paper:1.21").
     */
    private String dockerImage;

    /**
     * Server category, used for routing and display.
     * Typical values: LOBBY, SURVIVAL, MINIGAME, CREATIVE, PROXY…
     */
    private String serverType;

    /**
     *
     */
    private int minPort;

    private int maxPort;

    /**
     * Maximum number of players accepted per instance.
     */
    private int maxPlayers;

    /** Extra connection slots reserved for spectators joining an ongoing game. */
    private int spectatorSlots;

    /**
     * Minimum RAM memory allocated to the container (in MB, corresponds to -Xms of the JVM).
     */
    private int minRam;

    /**
     * Maximum RAM memory allocated to the container (in MB, corresponds to -Xmx of the JVM).
     */
    private int maxRam;

    /**
     * If true, an instance is automatically created and started
     * as soon as the number of active instances drops below {@link #minInstances}.
     */
    private boolean autoStart;

    /**
     * If true, an empty instance is automatically stopped
     * after {@link #autoStopDelay} seconds of inactivity.
     */
    private boolean autoStop;

    /**
     * Delay in seconds before automatically shutting down an empty instance.
     * Only has effect if {@link #autoStop} is enabled.
     * Default: 120 seconds.
     */
    private int autoStopDelay;

    /**
     * Environment variables injected into the Docker container on startup.
     * Key = variable name, Value = variable value.
     * Example: {"SERVER_NAME" → "Lobby #1", "EULA" → "true"}
     */
    private Map<String, String> environmentVariables;

    /**
     * List of Docker volume mounts in "source:destination" format.
     * Ex. : ["/data/maps:/minecraft/maps", "/data/plugins:/minecraft/plugins"]
     */
    private List<String> volumes;

    /**
     * Minimum number of active instances to maintain at all times.
     * Auto-start relies on this value to create instances in advance.
     * Default value: 0.
     */
    private int minInstances;

    /**
     * Maximum number of simultaneous instances allowed for this template.
     * Any creation request beyond this limit will be refused.
     * Default value: 10.
     */
    private int maxInstances;

    /**
     * If true, the server is under maintenance: players cannot join it.
     * However, administrators can access it based on business logic.
     */
    private boolean maintenanceMode;

    /**
     * If false, the template is disabled: no new instances can be created.
     * Allows you to remove a template from rotation without deleting it.
     * Default value: true.
     */
    private boolean enabled = true;

    /**
     * Default constructor.
     * Initializes the collections and applies the default values:
     * <ul>
     *   <li>{@code autoStopDelay} = 120 secondes</li>
     *   <li>{@code minInstances} = 0</li>
     *   <li>{@code maxInstances} = 10</li>
     * </ul>
     */
    public ServerTemplate() {
        this.environmentVariables = new HashMap<>();
        this.volumes = new ArrayList<>();
        this.autoStopDelay = 120;
        this.minInstances = 0;
        this.maxInstances = 10;
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDockerImage() {
        return dockerImage;
    }

    public void setDockerImage(String dockerImage) {
        this.dockerImage = dockerImage;
    }

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = serverType;
    }

    public int getMinPort() {
        return minPort;
    }

    public void setMinPort(int minPort) {
        this.minPort = minPort;
    }

    public int getMaxPort() {
        return maxPort;
    }

    public void setMaxPort(int maxPort) {
        this.maxPort = maxPort;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getSpectatorSlots() {
        return spectatorSlots;
    }

    public void setSpectatorSlots(int spectatorSlots) {
        this.spectatorSlots = spectatorSlots;
    }

    /** Maximum number of backend connections, active players and spectators combined. */
    public int getConnectionCapacity() {
        return Math.addExact(maxPlayers, spectatorSlots);
    }

    public int getMinRam() {
        return minRam;
    }

    public void setMinRam(int minRam) {
        this.minRam = minRam;
    }

    public int getMaxRam() {
        return maxRam;
    }

    public void setMaxRam(int maxRam) {
        this.maxRam = maxRam;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public boolean isAutoStop() {
        return autoStop;
    }

    public void setAutoStop(boolean autoStop) {
        this.autoStop = autoStop;
    }

    public int getAutoStopDelay() {
        return autoStopDelay;
    }

    public void setAutoStopDelay(int autoStopDelay) {
        this.autoStopDelay = autoStopDelay;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = new HashMap<>(Objects.requireNonNullElse(environmentVariables, Map.of()));
    }

    public List<String> getVolumes() {
        return volumes;
    }

    public void setVolumes(List<String> volumes) {
        this.volumes = new ArrayList<>(Objects.requireNonNullElse(volumes, List.of()));
    }

    public int getMinInstances() {
        return minInstances;
    }

    public void setMinInstances(int minInstances) {
        this.minInstances = minInstances;
    }

    public int getMaxInstances() {
        return maxInstances;
    }

    public void setMaxInstances(int maxInstances) {
        this.maxInstances = maxInstances;
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(boolean maintenanceMode) {
        this.maintenanceMode = maintenanceMode;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Verifies that the template can be used to create a container.
     *
     * @throws IllegalStateException if a mandatory value or limit is invalid
     */
    public void validate() {
        requireNonBlank(id, "id");
        requireNonBlank(name, "name");
        requireNonBlank(dockerImage, "dockerImage");
        requireNonBlank(serverType, "serverType");
        if (minPort != 0 || maxPort != 0) {
            requirePort(minPort, "minPort");
            requirePort(maxPort, "maxPort");
            if (minPort > maxPort) {
                throw new IllegalStateException("minPort doit être inférieur ou égal à maxPort");
            }
        }
        if (maxPlayers <= 0) throw new IllegalStateException("maxPlayers doit être strictement positif");
        if (spectatorSlots < 0) throw new IllegalStateException("spectatorSlots ne peut pas être négatif");
        if (minRam <= 0) throw new IllegalStateException("minRam doit être strictement positif");
        if (maxRam < minRam) throw new IllegalStateException("maxRam doit être supérieur ou égal à minRam");
        if (autoStopDelay < 0) throw new IllegalStateException("autoStopDelay ne peut pas être négatif");
        if (minInstances < 0) throw new IllegalStateException("minInstances ne peut pas être négatif");
        if (maxInstances < 1) throw new IllegalStateException("maxInstances doit être strictement positif");
        if (minInstances > maxInstances) {
            throw new IllegalStateException("minInstances doit être inférieur ou égal à maxInstances");
        }
        environmentVariables = new HashMap<>(Objects.requireNonNullElse(environmentVariables, Map.of()));
        volumes = new ArrayList<>(Objects.requireNonNullElse(volumes, List.of()));
        if (environmentVariables.entrySet().stream()
                .anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)) {
            throw new IllegalStateException("Les variables d'environnement doivent avoir un nom et une valeur valides");
        }
        if (volumes.stream().anyMatch(volume -> volume == null || volume.isBlank())) {
            throw new IllegalStateException("La liste des volumes contient une entrée vide");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " est obligatoire");
        }
    }

    private static void requirePort(int value, String field) {
        if (value < 1 || value > 65_535) {
            throw new IllegalStateException(field + " doit être compris entre 1 et 65535");
        }
    }

    /**
     * Concise textual representation of the template, useful for logs.
     * Example: {@code ServerTemplate{id='lobby', name='Lobby', type='LOBBY', image='tropicube/paper:1.21'}}
     */
    @Override
    public String toString() {
        return "ServerTemplate{id='" + id + "', name='" + name + "', type='" + serverType + "', image='" + dockerImage + "'}";
    }
}
