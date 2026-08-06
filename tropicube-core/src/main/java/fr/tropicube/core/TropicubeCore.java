package fr.tropicube.core;

import fr.tropicube.core.commands.*;
import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.core.managers.EconomyManager;
import fr.tropicube.core.managers.LanguageManager;
import fr.tropicube.core.listeners.*;
import fr.tropicube.core.managers.HeadDatabaseManager;
import fr.tropicube.core.managers.PlayerDataManager;
import fr.tropicube.core.managers.PermissionManager;
import fr.tropicube.core.listeners.NickApplyManager;
import fr.tropicube.core.util.ConfigUpdater;
import fr.tropicube.docker.client.RedisManager;
import org.bukkit.GameRules;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Classe principale du plugin Tropicube Core.
 * Gère l'initialisation et l'arrêt de tous les sous-systèmes :
 *  - Monnaie (economy)
 *  - Permissions / Grades VIP & Modération
 *  - Système de langues
 *  - Données joueurs (MySQL + Redis)
 */
public class TropicubeCore extends JavaPlugin {
    // Gestionnaire Redis pour le cache et la communication inter-serveurs
    private RedisManager redisManager;

    // Gestionnaire de base de données MySQL
    private DatabaseManager databaseManager;

    // Gestionnaire des langues (multi-langue)
    private LanguageManager languageManager;

    // Gestionnaire des permissions et grades
    private PermissionManager permissionManager;

    // Gestionnaire des données joueurs (chargement / sauvegarde)
    private PlayerDataManager playerDataManager;

    // Gestionnaire de l'économie (monnaie des joueurs)
    private EconomyManager economyManager;

    // Gestionnaire des têtes personnalisées (HeadDatabase)
    private HeadDatabaseManager headDatabaseManager;

    /**
     * Appelé par Paper lors de l'activation du plugin.
     * Initialise dans l'ordre : config, base de données, Redis, managers, commandes et listeners.
     */
    @Override
    public void onEnable() {
        // Crée le dossier de données du plugin s'il n'existe pas
        //noinspection ResultOfMethodCallIgnored
        getDataFolder().mkdirs();

        // Copie config.yml et les fichiers de langue par défaut si absents
        saveDefaultConfig();
        saveDefaultLanguages();

        // Met à jour les fichiers de configuration existants avec les nouvelles clés
        updateConfigs();

        // Initialisation de la base de données ; arrête le plugin en cas d'échec
        if (!initDatabase()) return;

        // Initialisation de Redis ; arrête le plugin en cas d'échec
        if (!initRedis()) return;

        // Initialise tous les managers métier
        if (!initManagers()) return;

        // Enregistre les commandes et les écouteurs d'événements
        if (!registerCommands()) return;

        if (!registerListeners()) return;

        // Désactive la barre de localisation (locator bar) sur tous les mondes chargés
        getServer().getWorlds().forEach(w -> w.setGameRule(GameRules.LOCATOR_BAR, false));
    }

    /**
     * Appelé par Paper lors de la désactivation du plugin.
     * Sauvegarde les données joueurs et ferme proprement les connexions.
     */
    @Override
    public void onDisable() {
        // Sauvegarde les données de tous les joueurs encore connectés
        if (playerDataManager != null) playerDataManager.saveAll();

        // Ferme la connexion à la base de données MySQL
        if (databaseManager != null) databaseManager.close();

        // Ferme la connexion Redis
        if (redisManager != null) redisManager.close();
    }

    /**
     * Initialise la connexion à la base de données MySQL.
     *
     * @return true si la connexion est établie, false en cas d'erreur (désactive le plugin)
     */
    private boolean initDatabase() {
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible d'initialiser la base de données.", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Initialise la connexion Redis à partir de la configuration du plugin.
     *
     * @return true si la connexion est établie, false en cas d'erreur (désactive le plugin)
     */
    private boolean initRedis() {
        try {
            String host     = getConfiguredString("TROPICUBE_REDIS_HOST", "redis.host", "localhost");
            int    port     = getConfiguredInt("TROPICUBE_REDIS_PORT", "redis.port", 6379);
            String password = getConfiguredString("TROPICUBE_REDIS_PASSWORD", "redis.password", "");

            redisManager = new RedisManager(host, port, password);
            redisManager.initialize();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible d'initialiser Redis.", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Instancie et initialise tous les managers dans l'ordre de leurs dépendances.
     * L'ordre est important : certains managers dépendent d'autres (ex. PlayerDataManager
     * dépend de PermissionManager, EconomyManager et LanguageManager).
     */
    private boolean initManagers() {
        try {
            languageManager  = new LanguageManager(this);
            languageManager.initialize();

            permissionManager = new PermissionManager(this, databaseManager);
            permissionManager.initialize();

            economyManager = new EconomyManager(this, databaseManager, redisManager);

            playerDataManager = new PlayerDataManager(this, databaseManager, permissionManager, economyManager, languageManager);
            playerDataManager.initialize();

            headDatabaseManager = new HeadDatabaseManager();

            // Démarre l'abonnement Redis pour la synchronisation des pseudos (Nick)
            // Note : ce n'est pas un listener Bukkit, mais un abonné Redis
            new NickApplyManager(this);
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible d'initialiser les gestionnaires du plugin.", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Enregistre toutes les commandes du plugin et leurs TabCompleters.
     * Les commandes doivent être déclarées dans plugin.yml.
     */
    private boolean registerCommands() {
        try {
            // --- Économie ---
            Objects.requireNonNull(getCommand("money")).setExecutor(new BalanceCommand(this));
            Objects.requireNonNull(getCommand("eco")).setExecutor(new EcoAdminCommand(this));

            // --- Grades ---
            var rankCmd = new GradeCommand(this);
            Objects.requireNonNull(getCommand("rank")).setExecutor(rankCmd);
            Objects.requireNonNull(getCommand("rank")).setTabCompleter(rankCmd); // auto-complétion

            // --- Permissions ---
            var permCmd = new PermissionCommand(this);
            Objects.requireNonNull(getCommand("tropiperm")).setExecutor(permCmd);
            Objects.requireNonNull(getCommand("tropiperm")).setTabCompleter(permCmd);

            // --- Langue ---
            Objects.requireNonNull(getCommand("lang")).setExecutor(new LanguageCommand(this));

            // --- Administration générale ---
            Objects.requireNonNull(getCommand("tropiadmin")).setExecutor(new TropicubeAdminPaperCommand(this));

            // --- Modération ---
            Objects.requireNonNull(getCommand("mute")).setExecutor(new MuteCommand(this));
            Objects.requireNonNull(getCommand("unmute")).setExecutor(new MuteCommand(this)); // même handler
            Objects.requireNonNull(getCommand("kick")).setExecutor(new KickCommand(this));
            Objects.requireNonNull(getCommand("warn")).setExecutor(new WarnCommand(this));
            Objects.requireNonNull(getCommand("history")).setExecutor(new HistoryCommand(this));

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible d'enregistrer les commandes.", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Enregistre tous les listeners d'événements Bukkit du plugin.
     */
    private boolean registerListeners() {
        try {
            // Connexion / déconnexion des joueurs (chargement et sauvegarde des données)
            getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);

            // Chat : formatage des messages, gestion des mutes, etc.
            getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);

            // Supprime certaines notifications système indésirables
            getServer().getPluginManager().registerEvents(new SuppressNotificationsListener(), this);

            // Listener du gestionnaire de têtes personnalisées (HeadDatabase)
            getServer().getPluginManager().registerEvents(headDatabaseManager, this);

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible d'enregistrer les listeners.", e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Copie les fichiers de langue par défaut (fr, en, es, de) dans le dossier
     * "languages/" du plugin s'ils n'existent pas encore.
     */
    private void saveDefaultLanguages() {
        //noinspection ResultOfMethodCallIgnored
        new File(getDataFolder(), "languages").mkdirs();
        String[] langs = {"fr", "en", "es", "de"};
        for (String lang : langs) {
            File f = new File(getDataFolder(), "languages/" + lang + ".yml");
            if (!f.exists()) {
                saveResource("languages/" + lang + ".yml", false);
            }
        }
    }

    /**
     * Met à jour les fichiers de configuration sur disque avec les nouvelles clés
     * présentes dans les ressources embarquées du plugin (sans écraser les valeurs
     * existantes définies par l'administrateur).
     */
    private void updateConfigs() {
        try {
            ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));

            String[] langs = {"fr", "en", "es", "de"};
            for (String lang : langs) {
                String path = "languages/" + lang + ".yml";
                ConfigUpdater.update(this, path, new File(getDataFolder(), path));
            }
        } catch (Exception e) {
            getLogger().warning("[ConfigUpdater] config.yml: " + e.getMessage());
        }
    }

    // --- Accesseurs publics (getters) ---
    // Permettent aux autres classes du plugin d'accéder aux managers via l'instance du plugin

    public DatabaseManager getDatabaseManager()     { return databaseManager; }
    public RedisManager getRedisManager()           { return redisManager; }
    public EconomyManager getEconomyManager()       { return economyManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public LanguageManager getLanguageManager()     { return languageManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    @SuppressWarnings("unused")
    public HeadDatabaseManager getHeadDatabaseManager() { return headDatabaseManager; }

    public String getConfiguredString(String environmentName, String configPath, String defaultValue) {
        String environmentValue = System.getenv(environmentName);
        return environmentValue == null || environmentValue.isBlank()
                ? getConfig().getString(configPath, defaultValue) : environmentValue;
    }

    public int getConfiguredInt(String environmentName, String configPath, int defaultValue) {
        String environmentValue = System.getenv(environmentName);
        if (environmentValue == null || environmentValue.isBlank())
            return getConfig().getInt(configPath, defaultValue);
        try {
            return Integer.parseInt(environmentValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(environmentName + " doit être un entier", e);
        }
    }
}
