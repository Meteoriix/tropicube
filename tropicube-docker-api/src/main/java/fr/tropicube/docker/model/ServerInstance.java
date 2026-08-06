package fr.tropicube.docker.model;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Représente une instance en cours d'exécution d'un serveur Minecraft dans Docker.
 * Contient toutes les métadonnées d'un conteneur : état, réseau, joueurs, configuration.
 * Peut être sérialisée/désérialisée en JSON pour être stockée dans Redis ou échangée
 * entre les différents services de l'infrastructure.
 */
public class ServerInstance {

    private static final Gson GSON = new Gson();

    /**
     * Cycle de vie d'une instance serveur :
     * <p>
     * CREATING → STARTING → GAME_WAITING → GAME_STARTING → GAME_PLAYING → GAME_ENDING → STOPPING → STOPPED
     * ↘ ERROR (à n'importe quelle étape)
     */
    public enum Status {
        /**
         * Le conteneur Docker est en cours de création.
         */
        CREATING,
        /**
         * Le conteneur est démarré, le serveur Minecraft s'initialise.
         */
        STARTING,
        /**
         * Le serveur est démarré, pleinement opérationnel et accepte des connexions.
         */
        GAME_WAITING,
        /**
         * Le serveur est en ligne, et le mini-jeu est sur le point de commencer
         */
        GAME_STARTING,
        /**
         * Le serveur est en ligne, le mini-jeu est en cours : les joueurs qui rejoignent seront mis en spectateur
         */
        GAME_PLAYING,
        /**
         * Le serveur est en ligne, le mini-jeu est terminé, il n'accepte plus de connexions
         */
        GAME_ENDING,
        /**
         * Le serveur est en cours d'arrêt propre.
         */
        STOPPING,
        /**
         * Le conteneur est arrêté.
         */
        STOPPED,
        /**
         * Une erreur irrécupérable s'est produite.
         */
        ERROR
    }

    /**
     * Identifiant unique de l'instance (UUID).
     */
    private String instanceId;

    /**
     * Identifiant complet du conteneur Docker (hash SHA-256).
     */
    private String containerId;

    /**
     * Nom lisible du conteneur Docker (ex. "tropicube-lobby-1").
     */
    private String containerName;

    /**
     * Identifiant du template utilisé pour créer cette instance.
     */
    private final String templateId;

    /**
     * Nom affiché du serveur (visible dans les menus de sélection).
     */
    private final String serverName;

    /**
     * Adresse IP interne du conteneur Docker.
     */
    private String host;

    /**
     * Port Minecraft sur lequel le serveur écoute.
     */
    private int port;

    /**
     * Port RCON exposé sur l'hôte pour l'administration à distance.
     * ZÉRO signifie que le RCON est désactivé pour cette instance.
     */
    private int rconPort;

    /**
     * Nombre de joueurs actuellement connectés.
     */
    private int onlinePlayers;

    /**
     * Capacité maximale du serveur.
     */
    private int maxPlayers;

    /**
     * État actuel du cycle de vie du serveur.
     */
    private Status status;

    /**
     * Timestamp Unix (secondes) du démarrage effectif du serveur Minecraft.
     */
    private long startedAt;

    /**
     * Si true, seuls les joueurs sur la whitelist peuvent rejoindre le serveur.
     */
    private final boolean whitelisted;

    /**
     * Liste des UUID de joueurs autorisés à rejoindre le serveur
     */
    private List<UUID> whitelistedPlayers = new ArrayList<>();

    /**
     * Type de serveur (ex. "PAPER", "VELOCITY", "MINESTOM"...).
     */
    private String serverType;

    /**
     * Constructeur principal pour créer une nouvelle instance à partir d'un template.
     *
     * @param instanceId UUID unique de cette instance
     * @param templateId Identifiant du template Docker source
     * @param serverName Nom affiché du serveur
     * @param port       Port Minecraft assigné
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
     * Indique si un joueur peut rejoindre ce serveur.
     * Les trois conditions doivent être réunies simultanément :
     * <ul>
     *   <li>Le serveur est en statut {@link Status#GAME_WAITING} ou {@link Status#GAME_STARTING} ou {@link Status#GAME_PLAYING} (pour ce dernier les joueurs seront mis en spectateur)</li>
     *   <li>La whitelist est désactivée ; utiliser {@link #isJoinable(UUID)} pour un joueur précis</li>
     *   <li>Le serveur n'est pas plein</li>
     * </ul>
     *
     * @return true si le serveur est accessible à un nouveau joueur
     */
    public boolean isJoinable() {
        return hasJoinableStatus() && !whitelisted && hasCapacity();
    }

    /**
     * Indique si un joueur précis peut rejoindre, whitelist comprise.
     *
     * @param playerId UUID du joueur à vérifier
     * @return {@code true} si le statut, la capacité et la whitelist autorisent l'accès
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
        return onlinePlayers >= 0 && maxPlayers > 0 && onlinePlayers < maxPlayers;
    }

    /**
     * Raccourci pour vérifier que le serveur est en statut {@link Status#GAME_WAITING}, {@link Status#GAME_STARTING}, {@link Status#GAME_PLAYING} ou {@link Status#GAME_ENDING}.
     * Ne tient pas compte de la whitelist ni de la capacité.
     *
     * @return true si le serveur est en ligne
     */
    public boolean isOnline() {
        return status == Status.GAME_WAITING || status == Status.GAME_STARTING || status == Status.GAME_PLAYING || status == Status.GAME_ENDING;
    }

    /**
     * Sérialise cette instance en JSON.
     * Utilisé notamment pour la persistance dans Redis.
     *
     * @return représentation JSON de l'objet
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * Désérialise une instance depuis une chaîne JSON.
     * Méthode de fabrique statique, inverse de {@link #toJson()}.
     *
     * @param json chaîne JSON représentant un {@code ServerInstance}
     * @return l'objet reconstitué
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

    public String getServerType() {
        return serverType;
    }

    public void setServerType(String serverType) {
        this.serverType = requireNonBlank(serverType, "serverType");
    }

    private void validateDeserializedState() {
        requireNonBlank(instanceId, "instanceId");
        requireNonBlank(templateId, "templateId");
        requireNonBlank(serverName, "serverName");
        requirePort(port, "port");
        Objects.requireNonNull(status, "status");
        requireNonBlank(serverType, "serverType");
        if (onlinePlayers < 0) throw new IllegalArgumentException("onlinePlayers ne peut pas être négatif");
        if (maxPlayers <= 0) throw new IllegalArgumentException("maxPlayers doit être strictement positif");
        if (rconPort != 0) requirePort(rconPort, "rconPort");
        whitelistedPlayers = copyWhitelist(whitelistedPlayers);
    }

    private static List<UUID> copyWhitelist(List<UUID> players) {
        List<UUID> copy = new ArrayList<>(Objects.requireNonNullElse(players, List.of()));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("whitelistedPlayers ne peut pas contenir de valeur nulle");
        }
        return copy;
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
     * Représentation textuelle concise de l'instance, utile pour les logs.
     * Exemple : {@code ServerInstance{id='abc-123', name='Lobby', status=GAME_WAITING, port=25565, players=12/50, whitelist=true}}
     */
    @Override
    public String toString() {
        return "ServerInstance{id='" + instanceId + "', name='" + serverName +
                "', status=" + status + ", port=" + port + ", players=" + onlinePlayers + "/" + maxPlayers + ", whitelist=" + whitelisted + "}";
    }
}
