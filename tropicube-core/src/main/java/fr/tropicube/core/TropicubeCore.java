package fr.tropicube.core;

import fr.tropicube.core.util.MessageStyle;
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
import fr.tropicube.core.social.FriendshipRepository;
import fr.tropicube.core.social.SocialService;
import org.bukkit.GameRules;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Main class of the Tropicube Core plugin.
 * Handles the initialization and shutdown of all subsystems:
 *  - Monnaie (economy)
 * - Permissions / VIP Ranks & Moderation
 * - Language system
 * - Player data (MySQL + Redis)
 */
public class TropicubeCore extends JavaPlugin {
    // Redis manager for cache and inter-server communication
    private RedisManager redisManager;

    // MySQL Database Manager
    private DatabaseManager databaseManager;

    // Language manager (multi-language)
    private LanguageManager languageManager;

    // Permissions and Ranks Manager
    private PermissionManager permissionManager;

    // Player data manager (loading/saving)
    private PlayerDataManager playerDataManager;

    // Economy Manager (player currency)
    private EconomyManager economyManager;

    // Custom Head Manager (HeadDatabase)
    private HeadDatabaseManager headDatabaseManager;
    private SocialService socialService;

    /**
     * Called by Paper when activating the plugin.
     * Initializes in order: config, database, Redis, managers, commands and listeners.
     */
    @Override
    public void onEnable() {
        // Create the plugin data folder if it does not exist
        //noinspection ResultOfMethodCallIgnored
        getDataFolder().mkdirs();

        // Copy config.yml and default language files if missing
        saveDefaultConfig();
        saveDefaultLanguages();

        // Updates existing configuration files with new keys
        updateConfigs();

        // Database initialization; stop the plugin on failure
        if (!initDatabase()) return;

        // Initializing Redis; stop the plugin on failure
        if (!initRedis()) return;

        // Initializes all business managers
        if (!initManagers()) return;

        // Registers commands and event listeners
        if (!registerCommands()) return;

        if (!registerListeners()) return;

        // Disables the locator bar on all loaded worlds
        getServer().getWorlds().forEach(w -> w.setGameRule(GameRules.LOCATOR_BAR, false));
    }

    /**
     * Called by Paper when deactivating the plugin.
     * Backs up player data and properly closes connections.
     */
    @Override
    public void onDisable() {
        // Saves data for all players still connected
        if (playerDataManager != null) playerDataManager.saveAll();

        // Close the connection to the MySQL database
        if (databaseManager != null) databaseManager.close();

        // Close the Redis connection
        if (redisManager != null) redisManager.close();
    }

    /**
     * Initializes the connection to the MySQL database.
     *
     * @return true if the connection is established, false on error (disables the plugin)
     */
    private boolean initDatabase() {
        try {
            databaseManager = new DatabaseManager(this);
            databaseManager.initialize();
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, MessageStyle.log("tc", "CORE", "<red>Impossible d'initialiser la base de données."), e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Initializes the Redis connection from the plugin configuration.
     *
     * @return true if the connection is established, false on error (disables the plugin)
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
            getLogger().log(Level.SEVERE, MessageStyle.log("tc", "CORE", "<red>Impossible d'initialiser Redis."), e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Instantiates and initializes all managers in the order of their dependencies.
     * The order is important: some managers depend on others (e.g. PlayerDataManager
     * depends on PermissionManager, EconomyManager and LanguageManager).
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

            int maximumFriends = positiveConfig("social.friends.max-count", 100, 1);
            int requestExpiryDays = positiveConfig("social.friends.request-expiry-days", 30, 1);
            int maximumPartySize = positiveConfig("social.party.max-size", 8, 2);
            int invitationSeconds = positiveConfig("social.party.invite-expiry-seconds", 60, 1);
            socialService = new SocialService(this, new FriendshipRepository(databaseManager),
                    maximumFriends, maximumPartySize, invitationSeconds);
            socialService.expireRequests(requestExpiryDays);

            headDatabaseManager = new HeadDatabaseManager();

            // Start Redis subscription for nickname synchronization (Nick)
            // Note: this is not a Bukkit listener, but a Redis subscriber
            new NickApplyManager(this);
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, MessageStyle.log("tc", "CORE", "<red>Impossible d'initialiser les gestionnaires du plugin."), e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Logs all plugin commands and their TabCompleters.
     * Commands must be declared in plugin.yml.
     */
    private boolean registerCommands() {
        try {
            // --- Economy ---
            Objects.requireNonNull(getCommand("money")).setExecutor(new BalanceCommand(this));
            Objects.requireNonNull(getCommand("eco")).setExecutor(new EcoAdminCommand(this));

            // --- Grades ---
            var rankCmd = new GradeCommand(this);
            Objects.requireNonNull(getCommand("rank")).setExecutor(rankCmd);
            Objects.requireNonNull(getCommand("rank")).setTabCompleter(rankCmd); // auto-complétion

            // --- Permissions ---
            var permCmd = new PermissionCommand(this);
            Objects.requireNonNull(getCommand("permissions")).setExecutor(permCmd);
            Objects.requireNonNull(getCommand("permissions")).setTabCompleter(permCmd);

        // --- Language ---
            Objects.requireNonNull(getCommand("lang")).setExecutor(new LanguageCommand(this));

            // --- General administration ---
            Objects.requireNonNull(getCommand("coreadmin")).setExecutor(new TropicubeAdminPaperCommand(this));
            var helpCommand = new HelpCommand(this);
            Objects.requireNonNull(getCommand("help")).setExecutor(helpCommand);
            Objects.requireNonNull(getCommand("help")).setTabCompleter(helpCommand);

            // --- Moderation ---
            Objects.requireNonNull(getCommand("mute")).setExecutor(new MuteCommand(this));
            Objects.requireNonNull(getCommand("unmute")).setExecutor(new MuteCommand(this)); // même handler
            Objects.requireNonNull(getCommand("kick")).setExecutor(new KickCommand(this));
            Objects.requireNonNull(getCommand("warn")).setExecutor(new WarnCommand(this));
            Objects.requireNonNull(getCommand("history")).setExecutor(new HistoryCommand(this));

            var friendCommand = new FriendCommand(this);
            Objects.requireNonNull(getCommand("friend")).setExecutor(friendCommand);
            Objects.requireNonNull(getCommand("friend")).setTabCompleter(friendCommand);
            var partyCommand = new PartyCommand(this);
            Objects.requireNonNull(getCommand("party")).setExecutor(partyCommand);
            Objects.requireNonNull(getCommand("party")).setTabCompleter(partyCommand);
            Objects.requireNonNull(getCommand("pc")).setExecutor(partyCommand);

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, MessageStyle.log("tc", "CORE", "<red>Impossible d'enregistrer les commandes."), e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Registers all Bukkit event listeners of the plugin.
     */
    private boolean registerListeners() {
        try {
            // Connecting/disconnecting players (loading and saving data)
            getServer().getPluginManager().registerEvents(new PlayerJoinQuitListener(this), this);

            // Chat: message formatting, mute management, etc.
            getServer().getPluginManager().registerEvents(new PlayerChatListener(this), this);

            // Removes some unwanted system notifications
            getServer().getPluginManager().registerEvents(new SuppressNotificationsListener(), this);

            getServer().getPluginManager().registerEvents(new NetworkProtectionListener(), this);

            // Custom Head Manager Listener (HeadDatabase)
            getServer().getPluginManager().registerEvents(headDatabaseManager, this);

            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, MessageStyle.log("tc", "CORE", "<red>Impossible d'enregistrer les listeners."), e);
            getServer().getPluginManager().disablePlugin(this);
            return false;
        }
    }

    /**
     * Copies the default language files (fr, en, es, de) to the folder
     * plugin's "languages/" directory if they do not exist yet.
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
     * Updates configuration files on disk with new keys
     * present in the plugin's embedded resources (without overwriting the values
     * existing ones defined by the administrator).
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
            getLogger().warning(MessageStyle.log("tc", "CONFIG", "<yellow>config.yml: " + e.getMessage()));
        }
    }

    // --- Accesseurs publics (getters) ---
    // Allow other plugin classes to access managers via the plugin instance

    public DatabaseManager getDatabaseManager()     { return databaseManager; }
    public RedisManager getRedisManager()           { return redisManager; }
    public EconomyManager getEconomyManager()       { return economyManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public LanguageManager getLanguageManager()     { return languageManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public SocialService getSocialService()         { return socialService; }
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

    private int positiveConfig(String path, int defaultValue, int minimum) {
        int value = getConfig().getInt(path, defaultValue);
        if (value < minimum) throw new IllegalArgumentException(path + " doit être supérieur ou égal à " + minimum);
        return value;
    }
}
