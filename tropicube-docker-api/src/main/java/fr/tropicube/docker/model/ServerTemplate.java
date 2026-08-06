package fr.tropicube.docker.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Représente un template de serveur Minecraft utilisé pour créer des conteneurs Docker.
 * <p>
 * Un template est la "recette" à partir de laquelle une ou plusieurs {@code ServerInstance}
 * sont instanciées. Il définit l'image Docker, les ressources allouées, les règles de
 * démarrage/arrêt automatique et les limites du nombre d'instances simultanées.
 * <p>
 * Les templates sont typiquement chargés depuis une base de données ou un fichier de
 * configuration, puis stockés dans Redis pour être accessibles à tous les services.
 */
public class ServerTemplate {

    /**
     * Identifiant unique du template (ex. "lobby", "survival-1").
     */
    private String id;

    /**
     * Nom lisible du template, affiché dans les interfaces d'administration.
     */
    private String name;

    /**
     * Image Docker utilisée pour créer les conteneurs (ex. "tropicube/paper:1.21").
     */
    private String dockerImage;

    /**
     * Catégorie du serveur, utilisée pour le routage et l'affichage.
     * Valeurs typiques : LOBBY, SURVIVAL, MINIGAME, CREATIVE, PROXY…
     */
    private String serverType;

    /**
     *
     */
    private int minPort;

    private int maxPort;

    /**
     * Nombre maximum de joueurs acceptés par instance.
     */
    private int maxPlayers;

    /**
     * Mémoire RAM minimale allouée au conteneur (en Mo, correspond à -Xms de la JVM).
     */
    private int minRam;

    /**
     * Mémoire RAM maximale allouée au conteneur (en Mo, correspond à -Xmx de la JVM).
     */
    private int maxRam;

    /**
     * Si true, une instance est automatiquement créée et démarrée
     * dès que le nombre d'instances actives passe en dessous de {@link #minInstances}.
     */
    private boolean autoStart;

    /**
     * Si true, une instance vide est automatiquement arrêtée
     * après {@link #autoStopDelay} secondes d'inactivité.
     */
    private boolean autoStop;

    /**
     * Délai en secondes avant l'arrêt automatique d'une instance vide.
     * N'a d'effet que si {@link #autoStop} est activé.
     * Valeur par défaut : 120 secondes.
     */
    private int autoStopDelay;

    /**
     * Variables d'environnement injectées dans le conteneur Docker au démarrage.
     * Clé = nom de la variable, Valeur = valeur de la variable.
     * Ex. : {"SERVER_NAME" → "Lobby #1", "EULA" → "true"}
     */
    private Map<String, String> environmentVariables;

    /**
     * Liste des montages de volumes Docker au format "source:destination".
     * Ex. : ["/data/maps:/minecraft/maps", "/data/plugins:/minecraft/plugins"]
     */
    private List<String> volumes;

    /**
     * Nombre minimum d'instances actives à maintenir en permanence.
     * L'auto-start s'appuie sur cette valeur pour créer des instances à l'avance.
     * Valeur par défaut : 0.
     */
    private int minInstances;

    /**
     * Nombre maximum d'instances simultanées autorisées pour ce template.
     * Toute demande de création au-delà de cette limite sera refusée.
     * Valeur par défaut : 10.
     */
    private int maxInstances;

    /**
     * Si true, le serveur est en maintenance : les joueurs ne peuvent pas le rejoindre.
     * Les administrateurs peuvent toutefois y accéder selon la logique métier.
     */
    private boolean maintenanceMode;

    /**
     * Si false, le template est désactivé : aucune nouvelle instance ne peut être créée.
     * Permet de retirer un template de la rotation sans le supprimer.
     * Valeur par défaut : true.
     */
    private boolean enabled = true;

    /**
     * Constructeur par défaut.
     * Initialise les collections et applique les valeurs par défaut :
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
     * Vérifie que le template peut être utilisé pour créer un conteneur.
     *
     * @throws IllegalStateException si une valeur obligatoire ou une limite est invalide
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
     * Représentation textuelle concise du template, utile pour les logs.
     * Exemple : {@code ServerTemplate{id='lobby', name='Lobby', type='LOBBY', image='tropicube/paper:1.21'}}
     */
    @Override
    public String toString() {
        return "ServerTemplate{id='" + id + "', name='" + name + "', type='" + serverType + "', image='" + dockerImage + "'}";
    }
}
