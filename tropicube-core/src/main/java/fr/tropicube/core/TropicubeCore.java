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
import fr.tropicube.core.network.ModerationService;
import fr.tropicube.core.network.NetworkCommunicationService;
import fr.tropicube.core.network.NotificationService;
import fr.tropicube.core.network.PlayerPreferenceService;
import fr.tropicube.core.network.StaffSecurityService;
import fr.tropicube.core.network.ProfileService;
import fr.tropicube.core.progression.MissionCatalog;
import fr.tropicube.core.progression.MissionService;
import fr.tropicube.core.progression.NetworkProgressionService;
import fr.tropicube.core.guild.GuildService;
import fr.tropicube.core.ui.RuntimeUiBundle;
import fr.tropicube.core.ui.MenuTemplateRegistry;
import org.bukkit.GameRules;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private volatile boolean stopping;
    private volatile boolean backendReady;
    private io.papermc.paper.threadedregions.scheduler.ScheduledTask healthTelemetryTask;
    private final java.util.concurrent.CompletableFuture<Void> ready = new java.util.concurrent.CompletableFuture<>();
    private java.util.concurrent.CompletableFuture<Void> bootstrap;
    private final java.util.concurrent.ExecutorService lifecycleExecutor = java.util.concurrent.Executors.newThreadPerTaskExecutor(
            Thread.ofPlatform().daemon(false).name("tropicube-lifecycle-", 0).factory());

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
    private PlayerPreferenceService playerPreferenceService;
    private NotificationService notificationService;
    private fr.tropicube.core.menu.PlayerCenterMenu playerCenterMenu;
    private fr.tropicube.core.network.ContextualHelpService contextualHelpService;
    private fr.tropicube.core.network.PrivacyService privacyService;
    private ModerationService moderationService;
    private NetworkCommunicationService communicationService;
    private StaffSecurityService staffSecurityService;
    private final Set<UUID> staffModePlayers = ConcurrentHashMap.newKeySet();
    private NetworkProgressionService networkProgressionService;
    private MissionService missionService;
    private fr.tropicube.core.cosmetic.CosmeticCatalog cosmeticCatalog;
    private fr.tropicube.core.cosmetic.CosmeticService cosmeticService;
    private ProfileService profileService;
    private GuildService guildService;
    private fr.tropicube.core.guild.GuildInvitations guildInvitations;
    private final fr.tropicube.core.network.PrivateChatInput privateChatInput = new fr.tropicube.core.network.PrivateChatInput(this);
    private RuntimeUiBundle runtimeUiBundle;
    private MenuTemplateRegistry menuTemplates;

    /**
     * Called by Paper when activating the plugin.
     * Captures configuration, prepares remote services on lifecycle workers, then initializes Paper adapters.
     */
    @Override
    public void onEnable() {
        // Create the plugin data folder if it does not exist
        //noinspection ResultOfMethodCallIgnored
        getDataFolder().mkdirs();

        // Copy config.yml and default language files if missing
        saveDefaultConfig();
        saveDefaultLanguages();
        File missionFile = new File(getDataFolder(), "missions.yml");
        if (!missionFile.exists()) saveResource("missions.yml", false);

        // Updates existing configuration files with new keys
        updateConfigs();

        // Register the admission guard before network initialization; TCP/Paper startup is not readiness.
        getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void preLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent event) {
                if (!backendReady || stopping) event.disallow(
                        org.bukkit.event.player.AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        net.kyori.adventure.text.Component.translatable("multiplayer.disconnect.server_shutdown"));
            }
        }, this);
        databaseManager = new DatabaseManager(this);
        redisManager = new RedisManager(getConfiguredString("TROPICUBE_REDIS_HOST", "redis.host", "localhost"),
                getConfiguredInt("TROPICUBE_REDIS_PORT", "redis.port", 6379),
                getConfiguredString("TROPICUBE_REDIS_PASSWORD", "redis.password", ""),
                fr.tropicube.docker.client.RedisOptions.read((key, fallback) -> getConfig().getInt("redis." + key, fallback)));
        permissionManager = new PermissionManager(this, databaseManager);
        runtimeUiBundle = new RuntimeUiBundle(this);
        bootstrap = java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                databaseManager.initialize();
                redisManager.initialize();
                runtimeUiBundle.restore();
                File cosmeticsFile = new File(getDataFolder(), "cosmetics.yml");
                if (!cosmeticsFile.exists()) saveResource("cosmetics.yml", false);
                try (var input = java.nio.file.Files.newInputStream(cosmeticsFile.toPath())) {
                    cosmeticCatalog = fr.tropicube.core.cosmetic.CosmeticCatalog.load(input);
                }
                permissionManager.initialize();
            } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }, lifecycleExecutor);
        bootstrap.whenComplete((ignored, failure) -> onServerThread(this, () -> {
            if (failure != null) { failStartup(this, failure); return; }
            finishStartup();
        }));
    }

    private void finishStartup() {
        // Initializes all business managers
        if (!initManagers()) return;

        // Registers commands and event listeners
        if (!registerCommands()) return;

        if (!registerListeners()) return;

        // Disables the locator bar on all loaded worlds
        getServer().getWorlds().forEach(w -> w.setGameRule(GameRules.LOCATOR_BAR, false));
        healthTelemetryTask = getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
            if (stopping) return;
            long started = System.nanoTime();
            try {
                redisManager.get("health:probe");
                String instanceId = System.getenv("INSTANCE_ID");
                if (instanceId != null) redisManager.set("health:backend:" + instanceId,
                        "{\"active_sql\":" + databaseManager.activeOperations() + ",\"queued_sql\":"
                        + databaseManager.queuedOperations() + ",\"ready\":" + backendReady + "}", 180);
                getLogger().info("event=runtime_health active_sql=" + databaseManager.activeOperations()
                        + " queued_sql=" + databaseManager.queuedOperations()
                        + " redis_millis=" + (System.nanoTime() - started) / 1_000_000);
            } catch (Exception error) { getLogger().warning("event=runtime_health redis=unavailable"); }
        }, 1, 60, java.util.concurrent.TimeUnit.SECONDS);
        ready.complete(null);
    }

    /**
     * Called by Paper when deactivating the plugin.
     * Backs up player data and properly closes connections.
     */
    @Override
    public void onDisable() {
        stopping = true;
        backendReady = false;
        ready.completeExceptionally(new IllegalStateException("Core stopping"));
        if (healthTelemetryTask != null) healthTelemetryTask.cancel();
        privateChatInput.close();
        getServer().getScheduler().cancelTasks(this);
        getServer().getAsyncScheduler().cancelTasks(this);
        // Non-daemon worker keeps the JVM alive for the bounded database drain, without blocking Paper.
        java.util.concurrent.CompletableFuture<Void> initialization = bootstrap == null
                ? java.util.concurrent.CompletableFuture.completedFuture(null) : bootstrap;
        initialization.handleAsync((ignored, failure) -> {
            try {
                if (playerDataManager != null) databaseManager.supplyAsync(() -> { playerDataManager.saveAll(); return null; })
                        .exceptionally(error -> { getLogger().log(Level.SEVERE, "Final player save failed", error); return null; });
            } finally {
                if (databaseManager != null) databaseManager.close();
                if (redisManager != null) {
                    try {
                        String instanceId = System.getenv("INSTANCE_ID");
                        if (instanceId != null) redisManager.delete("health:backend:" + instanceId);
                    } catch (Exception ignoredError) { /* TTL removes unavailable shutdown telemetry. */ }
                    redisManager.close();
                }
            }
            return null;
        }, lifecycleExecutor).whenComplete((ignored, failure) -> {
            if (failure != null) getLogger().log(Level.SEVERE, "Shutdown persistence failed", failure);
            lifecycleExecutor.shutdown();
        });
    }

    /** Initializes a dependent backend's network off-thread, then its Paper objects on the server thread. */
    public void initializeBackend(JavaPlugin owner, Runnable networkInitialization, Runnable serverInitialization) {
        ready.thenRunAsync(() -> {
            if (stopping) throw new IllegalStateException("Core stopping");
            networkInitialization.run();
        }, lifecycleExecutor).whenComplete((ignored, failure) -> onServerThread(owner, () -> {
            if (failure != null) { failStartup(owner, failure); return; }
            try {
                serverInitialization.run();
                if (owner.isEnabled() && !stopping) {
                    backendReady = true;
                    owner.getLogger().info("TROPICUBE_BACKEND_READY");
                }
            } catch (Exception error) { failStartup(owner, error); }
        }));
    }

    /** Closes admission immediately when the specialized plugin stops independently of Core. */
    public void backendStopped() { backendReady = false; }

    private void failStartup(JavaPlugin owner, Throwable failure) {
        backendReady = false;
        ready.completeExceptionally(failure);
        owner.getLogger().log(Level.SEVERE, "Backend initialization failed; admission remains closed", failure);
        getServer().getPluginManager().disablePlugin(owner);
    }

    private void onServerThread(JavaPlugin owner, Runnable action) {
        if (stopping) return;
        try {
            getServer().getScheduler().runTask(owner, () -> {
                if (!stopping && owner.isEnabled()) action.run();
            });
        } catch (org.bukkit.plugin.IllegalPluginAccessException ignored) {
            // Disable raced the completion; no callback may mutate a stopped plugin.
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

            menuTemplates = new MenuTemplateRegistry(this);
            getServer().getServicesManager().register(fr.tropicube.core.ui.UiReloadParticipant.class,
                    menuTemplates, this, org.bukkit.plugin.ServicePriority.Normal);

            // The grade catalog was loaded on the lifecycle worker before this phase.

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

            playerPreferenceService = new PlayerPreferenceService(databaseManager);
            notificationService = new NotificationService(databaseManager);
            playerCenterMenu = new fr.tropicube.core.menu.PlayerCenterMenu(this);
            contextualHelpService = new fr.tropicube.core.network.ContextualHelpService(
                    this, databaseManager, playerPreferenceService);
            privacyService = new fr.tropicube.core.network.PrivacyService(this, databaseManager);
            networkProgressionService = new NetworkProgressionService(this, databaseManager);
            profileService = new ProfileService(databaseManager, playerPreferenceService);
            {
                cosmeticCatalog.validateNames((language, key) -> languageManager.getLanguages().get(language).isString(key));
                cosmeticService = new fr.tropicube.core.cosmetic.CosmeticService(databaseManager, cosmeticCatalog, playerId -> {
                    economyManager.invalidateCache(playerId);
                    try { redisManager.publishPlayerEvent("ECONOMY_INVALIDATE", playerId.toString()); }
                    catch (RuntimeException error) { getLogger().log(java.util.logging.Level.WARNING, "Cosmetic balance invalidation failed for " + playerId, error); }
                });
            }
            try (var input = java.nio.file.Files.newInputStream(
                    new File(getDataFolder(), "missions.yml").toPath())) {
            missionService = new MissionService(this, databaseManager, MissionCatalog.load(input));
            }
            guildService = new GuildService(databaseManager,
                    positiveConfig("guilds.max-members", 50, 2),
                    positiveConfig("guilds.max-officers", 5, 1),
                    positiveConfig("guilds.weekly-contribution-cap", 5000, 1));
            guildInvitations = new fr.tropicube.core.guild.GuildInvitations(guildService, notificationService);
            getServer().getAsyncScheduler().runAtFixedRate(this, task -> guildService.applySuccession(),
                    1, 24, java.util.concurrent.TimeUnit.HOURS);
            getServer().getAsyncScheduler().runAtFixedRate(this,
                    task -> permissionManager.purgeAudit(positiveConfig("access.audit-retention-days", 365, 1)),
                    1, 24, java.util.concurrent.TimeUnit.HOURS);
            moderationService = new ModerationService(databaseManager, redisManager);
            staffSecurityService = new StaffSecurityService(databaseManager, redisManager,
                    System.getenv("TROPICUBE_TOTP_MASTER_KEY"));
            if (!staffSecurityService.available()) getLogger().warning(
                    "TROPICUBE_TOTP_MASTER_KEY absente : les sessions staff sécurisées restent verrouillées.");
            communicationService = new NetworkCommunicationService(this, databaseManager, playerPreferenceService);
            getServer().getAsyncScheduler().runAtFixedRate(this, task -> {
                notificationService.purgeExpired();
                moderationService.purgeExpiredEvidence();
                privacyService.processDue();
                privacyService.purgeExports();
            }, 1, 1, java.util.concurrent.TimeUnit.HOURS);

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

            // --- Cumulative access levels ---
            var levelCommand = new LevelCommand(this);
            Objects.requireNonNull(getCommand("level")).setExecutor(levelCommand);
            Objects.requireNonNull(getCommand("level")).setTabCompleter(levelCommand);

        // --- Language ---
            Objects.requireNonNull(getCommand("lang")).setExecutor(new LanguageCommand(this));

            // --- General administration ---
            Objects.requireNonNull(getCommand("coreadmin")).setExecutor(new TropicubeAdminPaperCommand(this));
            Objects.requireNonNull(getCommand("languageeditorreload")).setExecutor(new LanguageEditorReloadCommand(this));
            var helpCommand = new HelpCommand(this);
            Objects.requireNonNull(getCommand("help")).setExecutor(helpCommand);
            Objects.requireNonNull(getCommand("help")).setTabCompleter(helpCommand);

            // --- Moderation ---
            Objects.requireNonNull(getCommand("mute")).setExecutor(new MuteCommand(this));
            Objects.requireNonNull(getCommand("unmute")).setExecutor(new MuteCommand(this)); // même handler
            Objects.requireNonNull(getCommand("kick")).setExecutor(new KickCommand(this));
            Objects.requireNonNull(getCommand("warn")).setExecutor(new WarnCommand(this));
            Objects.requireNonNull(getCommand("history")).setExecutor(new HistoryCommand(this));
            var banCommand = new BanCommand(this);
            Objects.requireNonNull(getCommand("ban")).setExecutor(banCommand);
            Objects.requireNonNull(getCommand("tempban")).setExecutor(banCommand);
            Objects.requireNonNull(getCommand("unban")).setExecutor(banCommand);
            var reportCommand = new ReportCommand(this);
            Objects.requireNonNull(getCommand("report")).setExecutor(reportCommand);
            Objects.requireNonNull(getCommand("reports")).setExecutor(reportCommand);

            var messageCommand = new MessageCommand(this);
            Objects.requireNonNull(getCommand("msg")).setExecutor(messageCommand);
            Objects.requireNonNull(getCommand("reply")).setExecutor(messageCommand);
            Objects.requireNonNull(getCommand("ignore")).setExecutor(messageCommand);
            Objects.requireNonNull(getCommand("globalchat")).setExecutor(new GlobalChatCommand(this));
            var twoFactorCommand = new TwoFactorCommand(this);
            Objects.requireNonNull(getCommand("2fa")).setExecutor(twoFactorCommand);
            Objects.requireNonNull(getCommand("2fa")).setTabCompleter(twoFactorCommand);
            var staffCommand = new StaffCommand(this);
            Objects.requireNonNull(getCommand("staff")).setExecutor(staffCommand);
            Objects.requireNonNull(getCommand("staffchat")).setExecutor(staffCommand);
            var playerCenter = new PlayerCenterCommand(this);
            Objects.requireNonNull(getCommand("profile")).setExecutor(playerCenter);
            Objects.requireNonNull(getCommand("settings")).setExecutor(playerCenter);
            Objects.requireNonNull(getCommand("missions")).setExecutor(playerCenter);
            Objects.requireNonNull(getCommand("notifications")).setExecutor(playerCenter);
            Objects.requireNonNull(getCommand("center")).setExecutor(playerCenter);
            Objects.requireNonNull(getCommand("privacy")).setExecutor(new fr.tropicube.core.commands.PrivacyCommand(this));
            Objects.requireNonNull(getCommand("guild")).setExecutor(new GuildCommand(this));

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
            getServer().getPluginManager().registerEvents(privateChatInput, this);

            // Removes some unwanted system notifications
            getServer().getPluginManager().registerEvents(new SuppressNotificationsListener(), this);

            getServer().getPluginManager().registerEvents(new NetworkProtectionListener(this), this);
            getServer().getPluginManager().registerEvents(new StaffModeListener(this), this);
            getServer().getPluginManager().registerEvents(playerCenterMenu, this);

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
            ConfigUpdater.update(this, "missions.yml", new File(getDataFolder(), "missions.yml"));

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
    public RuntimeUiBundle getRuntimeUiBundle()     { return runtimeUiBundle; }
    public MenuTemplateRegistry getMenuTemplates()  { return menuTemplates; }
    public EconomyManager getEconomyManager()       { return economyManager; }
    public PermissionManager getPermissionManager() { return permissionManager; }
    public LanguageManager getLanguageManager()     { return languageManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public SocialService getSocialService()         { return socialService; }
    public PlayerPreferenceService getPlayerPreferenceService() { return playerPreferenceService; }
    public NotificationService getNotificationService() { return notificationService; }
    public fr.tropicube.core.menu.PlayerCenterMenu getPlayerCenterMenu() { return playerCenterMenu; }
    public fr.tropicube.core.network.ContextualHelpService getContextualHelpService() { return contextualHelpService; }
    public fr.tropicube.core.network.PrivacyService getPrivacyService() { return privacyService; }
    public ModerationService getModerationService() { return moderationService; }
    public NetworkCommunicationService getCommunicationService() { return communicationService; }
    public StaffSecurityService getStaffSecurityService() { return staffSecurityService; }
    public NetworkProgressionService getNetworkProgressionService() { return networkProgressionService; }
    public fr.tropicube.core.cosmetic.CosmeticCatalog getCosmeticCatalog() { return cosmeticCatalog; }
    public fr.tropicube.core.cosmetic.CosmeticService getCosmeticService() { return cosmeticService; }
    public MissionService getMissionService() { return missionService; }
    public ProfileService getProfileService() { return profileService; }
    /** Shared private input boundary consumed before network chat publication. */
    public fr.tropicube.core.network.PrivateChatInput getPrivateChatInput() { return privateChatInput; }
    /** Notification delivery shared by guild command and menu adapters. */
    public fr.tropicube.core.guild.GuildInvitations getGuildInvitations() { return guildInvitations; }
    public GuildService getGuildService() { return guildService; }
    public boolean isStaffMode(UUID playerId) { return staffModePlayers.contains(playerId); }
    public void setStaffMode(UUID playerId, boolean active) {
        if (active) staffModePlayers.add(playerId); else staffModePlayers.remove(playerId);
    }
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
