package fr.tropicube.docker.model;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents a running instance of a Minecraft server in Docker.
 * Contains all the metadata of a container: state, network, players, configuration.
 * Can be serialized/deserialized to JSON for storage in Redis or exchanged
 * between the different infrastructure services.
 */
public class ServerInstance {

    private static final Gson GSON = new Gson();

    /**
     * Lifecycle of a server instance:
     * <p>
     * CREATING → STARTING → GAME_WAITING → GAME_STARTING → GAME_PLAYING → GAME_ENDING → STOPPING → STOPPED
     * ↘ ERROR (at any step)
     */
    public enum Status {
        /**
         * The Docker container is being created.
         */
        CREATING,
        /**
         * The container is started, the Minecraft server is initialized.
         */
        STARTING,
        /**
         * The server is started, fully operational and accepting connections.
         */
        GAME_WAITING,
        /**
         * The server is online, and the mini-game is about to begin
         */
        GAME_STARTING,
        /**
         * The server is online, the mini-game is in progress: players who join will be put as spectators
         */
        GAME_PLAYING,
        /**
         * The server is online, the mini-game is over, it is no longer accepting connections
         */
        GAME_ENDING,
        /**
         * The server is shutting down cleanly.
         */
        STOPPING,
        /**
         * The container is stopped.
         */
        STOPPED,
        /**
         * An unrecoverable error has occurred.
         */
        ERROR
    }

    /**
     * Unique identifier of the instance (UUID).
     */
    private String instanceId;

    /**
     * Full Docker container identifier (SHA-256 hash).
     */
    private String containerId;

    /**
     * Readable name of the Docker container (e.g. "tropicube-lobby-1").
     */
    private String containerName;

    /**
     * Identifier of the template used to create this instance.
     */
    private final String templateId;

    /**
     * Displayed name of the server (visible in the selection menus).
     */
    private final String serverName;

    /**
     * Internal IP address of the Docker container.
     */
    private String host;

    /**
     * Minecraft port on which the server listens.
     */
    private int port;

    /**
     * RCON port exposed on host for remote administration.
     * ZERO means RCON is disabled for this instance.
     */
    private int rconPort;

    /**
     * Number of players currently connected.
     */
    private int onlinePlayers;

    /**
     * Maximum server capacity.
     */
    private int maxPlayers;

    /** Additional backend slots usable once the game has started. */
    private int spectatorSlots;

    /**
     * Current state of the server lifecycle.
     */
    private Status status;

    /**
     * Unix timestamp (seconds) of the actual startup of the Minecraft server.
     */
    private long startedAt;

    /**
     * If true, only players on the whitelist can join the server.
     */
    private final boolean whitelisted;

    /**
     * List of player UUIDs allowed to join the server
     */
    private List<UUID> whitelistedPlayers = new ArrayList<>();

    /**
     * Server type (e.g. "PAPER", "VELOCITY", "MINESTOM"...).
     */
    private String serverType;

    /** Functional matchmaking mode; absent in legacy Redis payloads. */
    private InstanceMode mode;

    /**
     * Main constructor to create a new instance from a template.
     *
     * @param instanceId Unique UUID of this instance
     * @param templateId identifier of the source Docker template
     * @param serverName Server Display Name
     * @param port Minecraft port assigned
     */
    public ServerInstance(String instanceId, String templateId, String serverName, int port, boolean whitelisted) {
        this.whitelisted = whitelisted;
        this.instanceId = requireNonBlank(instanceId, "instanceId");
        this.templateId = requireNonBlank(templateId, "templateId");
        this.serverName = requireNonBlank(serverName, "serverName");
        requirePort(port, "port");
        this.port = port;
        this.status = Status.CREATING;
    }

    /**
     * Indicates whether a player can join this server.
     * The three conditions must be met simultaneously:
     * <ul>
     * <li>The server is in status {@link Status#GAME_WAITING} or {@link Status#GAME_STARTING} or {@link Status#GAME_PLAYING} (for the latter the players will be put as spectators)</li>
     * <li>The whitelist is disabled; use {@link #isJoinable(UUID)} for a specific player</li>
     * <li>Server is not full</li>
     * </ul>
     *
     * @return true if the server is accessible to a new player
     */
    public boolean isJoinable() {
        return hasJoinableStatus() && !whitelisted && hasCapacity();
    }

    /**
     * Indicates whether a specific player can join, including the whitelist.
     *
     * @param playerId UUID of the player to check
     * @return {@code true} if status, capacity and whitelist allow access
     */
    public boolean isJoinable(UUID playerId) {
        return hasJoinableStatus()
                && hasCapacity()
                && (!whitelisted || (playerId != null && whitelistedPlayers.contains(playerId)));
    }

    private boolean hasJoinableStatus() {
        return status == Status.GAME_WAITING || status == Status.GAME_STARTING || status == Status.GAME_PLAYING;
    }

    private boolean hasCapacity() {
        int capacity = status == Status.GAME_PLAYING ? getConnectionCapacity() : maxPlayers;
        return onlinePlayers >= 0 && maxPlayers > 0 && onlinePlayers < capacity;
    }

    /**
     * Shortcut to verify that the server is in status {@link Status#GAME_WAITING}, {@link Status#GAME_STARTING}, {@link Status#GAME_PLAYING} or {@link Status#GAME_ENDING}.
     * Does not take whitelist or capacity into account.
     *
     * @return true if the server is online
     */
    public boolean isOnline() {
        return status == Status.GAME_WAITING || status == Status.GAME_STARTING || status == Status.GAME_PLAYING || status == Status.GAME_ENDING;
    }

    /**
     * Serializes this instance to JSON.
     * Used in particular for persistence in Redis.
     *
     * @return JSON representation of object
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Deserializes an instance from a JSON string.
     * Static factory method, inverse of {@link #toJson()}.
     *
     * @param json JSON string representing a {@code ServerInstance}
     * @return the reconstituted object
     */
    public static ServerInstance fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("Le JSON d'une instance ne peut pas être vide");
        }
        ServerInstance instance = GSON.fromJson(json, ServerInstance.class);
        if (instance == null) {
            throw new IllegalArgumentException("Le JSON ne contient aucune instance");
        }
        instance.validateDeserializedState();
        return instance;
    }

    // ===== Getters & Setters =====

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = requireNonBlank(instanceId, "instanceId");
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public String getContainerName() {
        return containerName;
    }

    public void setContainerName(String containerName) {
        this.containerName = containerName;
    }

    public String getTemplateId() {
        return templateId;
    }

    public String getServerName() {
        return serverName;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        requirePort(port, "port");
        this.port = port;
    }

    public int getRconPort() {
        return rconPort;
    }

    public void setRconPort(int rconPort) {
        if (rconPort != 0) requirePort(rconPort, "rconPort");
        this.rconPort = rconPort;
    }

    public int getOnlinePlayers() {
        return onlinePlayers;
    }

    public void setOnlinePlayers(int onlinePlayers) {
        if (onlinePlayers < 0) throw new IllegalArgumentException("onlinePlayers ne peut pas être négatif");
        this.onlinePlayers = onlinePlayers;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        if (maxPlayers <= 0) throw new IllegalArgumentException("maxPlayers doit être strictement positif");
        this.maxPlayers = maxPlayers;
    }

    public int getSpectatorSlots() {
        return spectatorSlots;
    }

    public void setSpectatorSlots(int spectatorSlots) {
        if (spectatorSlots < 0) throw new IllegalArgumentException("spectatorSlots ne peut pas être négatif");
        this.spectatorSlots = spectatorSlots;
    }

    /** Returns the physical backend capacity including spectator-only slots. */
    public int getConnectionCapacity() {
        return Math.addExact(maxPlayers, spectatorSlots);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public boolean isWhitelisted() {
        return whitelisted;
    }

    public List<UUID> getWhitelistedPlayers() {
        return List.copyOf(whitelistedPlayers);
    }

    public void setWhitelistedPlayers(List<UUID> whitelistedPlayers) {
        this.whitelistedPlayers = copyWhitelist(whitelistedPlayers);
    }

    /** Returns whether the player is explicitly allowed on this private instance. */
    public boolean isWhitelistedPlayer(UUID playerId) {
        return playerId != null && whitelistedPlayers.contains(playerId);
    }

    /** Adds a player once while preserving the stable display order. */
    public boolean addWhitelistedPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (whitelistedPlayers.contains(playerId)) return false;
        whitelistedPlayers.add(playerId);
        return true;
    }

    /** Removes a player from this instance whitelist. */
    public boolean removeWhitelistedPlayer(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return whitelistedPlayers.remove(playerId);
    }

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = requireNonBlank(serverType, "serverType");
    }

    public InstanceMode getMode() {
        return mode == null ? InstanceMode.infer(templateId, whitelisted) : mode;
    }

    public void setMode(InstanceMode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    private void validateDeserializedState() {
        requireNonBlank(instanceId, "instanceId");
        requireNonBlank(templateId, "templateId");
        requireNonBlank(serverName, "serverName");
        requirePort(port, "port");
        Objects.requireNonNull(status, "status");
        requireNonBlank(serverType, "serverType");
        if (mode == null) mode = InstanceMode.infer(templateId, whitelisted);
        if (onlinePlayers < 0) throw new IllegalArgumentException("onlinePlayers ne peut pas être négatif");
        if (maxPlayers <= 0) throw new IllegalArgumentException("maxPlayers doit être strictement positif");
        if (spectatorSlots < 0) throw new IllegalArgumentException("spectatorSlots ne peut pas être négatif");
        if (rconPort != 0) requirePort(rconPort, "rconPort");
        whitelistedPlayers = copyWhitelist(whitelistedPlayers);
    }

    private static List<UUID> copyWhitelist(List<UUID> players) {
        List<UUID> copy = new ArrayList<>(Objects.requireNonNullElse(players, List.of()));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("whitelistedPlayers ne peut pas contenir de valeur nulle");
        }
        return new ArrayList<>(new LinkedHashSet<>(copy));
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " est obligatoire");
        }
        return value;
    }

    private static void requirePort(int value, String field) {
        if (value < 1 || value > 65_535) {
            throw new IllegalArgumentException(field + " doit être compris entre 1 et 65535");
        }
    }

    /**
     * Concise textual representation of the instance, useful for logs.
     * Example: {@code ServerInstance{id='abc-123', name='Lobby', status=GAME_WAITING, port=25565, players=12/50, whitelist=true}}
     */
    @Override
    public String toString() {
        return "ServerInstance{id='" + instanceId + "', name='" + serverName +
                "', status=" + status + ", port=" + port + ", players=" + onlinePlayers + "/" + maxPlayers + ", whitelist=" + whitelisted + "}";
    }
}
