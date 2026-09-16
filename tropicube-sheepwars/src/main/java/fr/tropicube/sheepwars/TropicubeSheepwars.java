package fr.tropicube.sheepwars;

import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.core.util.ConfigUpdater;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.sheepwars.game.GameManager;
import fr.tropicube.sheepwars.config.GameplayBalance;
import fr.tropicube.sheepwars.config.SheepWarsMenuConfigMigration;
import fr.tropicube.sheepwars.competitive.SheepWarsProgressionService;
import fr.tropicube.sheepwars.competitive.KitMasteryCatalog;
import fr.tropicube.sheepwars.competitive.SeasonRewardCatalog;
import fr.tropicube.sheepwars.command.SheepWarsCommand;
import fr.tropicube.sheepwars.listener.PlayerListener;
import fr.tropicube.sheepwars.listener.PlayerIdentityListener;
import fr.tropicube.sheepwars.listener.ProtectionListener;
import fr.tropicube.sheepwars.listener.SheepListener;
import fr.tropicube.sheepwars.menu.ClassKitSelectionMenu;
import fr.tropicube.sheepwars.menu.GameSettingsMenu;
import fr.tropicube.sheepwars.menu.MapSelectionMenu;
import fr.tropicube.sheepwars.menu.KitMasteryMenu;
import fr.tropicube.sheepwars.menu.TeamSelectionMenu;
import fr.tropicube.sheepwars.menu.WhitelistMenu;
import fr.tropicube.sheepwars.player.PlayerDataManager;
import fr.tropicube.sheepwars.powerup.TeamPowerUpSettings;
import fr.tropicube.sheepwars.scoreboard.ScoreboardManager;
import fr.tropicube.sheepwars.sheep.SheepManager;
import fr.tropicube.core.ui.MenuTemplateRegistry;
import fr.tropicube.core.ui.UiReloadParticipant;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;
import java.util.logging.Level;

/** Initializes a Paper SheepWars instance and coordinates its game cycle. */
public final class TropicubeSheepwars extends JavaPlugin {
    private TropicubeCore corePlugin;
    private RedisManager redisManager;

    private GameManager gameManager;

    private PlayerDataManager playerDataManager;

    private SheepManager sheepManager;

    private ScoreboardManager scoreboardManager;

    private ClassKitSelectionMenu classKitMenu;
    private TeamSelectionMenu teamMenu;
    private MapSelectionMenu mapSelectionMenu;
    private GameSettingsMenu gameSettingsMenu;
    private WhitelistMenu whitelistMenu;
    private GameplayBalance gameplayBalance;
    private TeamPowerUpSettings teamPowerUpSettings;
    private SheepWarsProgressionService progressionService;
    private KitMasteryMenu kitMasteryMenu;
    private KitMasteryCatalog kitMasteryCatalog;
    private SeasonRewardCatalog seasonRewardCatalog;
    private volatile boolean shuttingDown;
    private MenuTemplateRegistry menuTemplates;

    @Override
    public void onEnable() {
        TropicubeCore corePlugin = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        if (corePlugin == null) { getServer().getPluginManager().disablePlugin(this); return; }
        this.corePlugin = corePlugin;
        saveDefaultConfig();
        String host = getConfig().getString("redis.host", "localhost");
        int port = getConfig().getInt("redis.port", 6379);
        String password = System.getenv("TROPICUBE_REDIS_PASSWORD");
        if (password == null || password.isBlank()) password = getConfig().getString("redis.password", "");
        redisManager = new RedisManager(host, port, password);
        corePlugin.initializeBackend(this, redisManager::initialize, this::finishStartup);
    }

    private void finishStartup() {
        saveDefaultConfig();

        try {
            ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
        } catch (Exception e) {
            getLogger().warning(MessageStyle.log("sw", "CONFIG", "<yellow>config.yml: " + e.getMessage()));
        }

        try {
            gameplayBalance = GameplayBalance.load(getConfig());
            teamPowerUpSettings = TeamPowerUpSettings.load(getConfig());
        } catch (IllegalArgumentException exception) {
            getLogger().severe(MessageStyle.log("sw", "SYSTEM", "<red>" + exception.getMessage()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        File masteryFile = new File(getDataFolder(), "kit-mastery.yml");
        if (!masteryFile.exists()) saveResource("kit-mastery.yml", false);
        try {
            ConfigUpdater.update(this, "kit-mastery.yml", masteryFile);
            kitMasteryCatalog = KitMasteryCatalog.load(masteryFile);
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, MessageStyle.log("sw", "CONFIG",
                    "<red>Catalogue de maîtrise invalide : " + exception.getMessage()), exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        File seasonRewardsFile = new File(getDataFolder(), "season-rewards.yml");
        if (!seasonRewardsFile.exists()) saveResource("season-rewards.yml", false);
        try {
            ConfigUpdater.update(this, "season-rewards.yml", seasonRewardsFile);
            seasonRewardCatalog = SeasonRewardCatalog.load(seasonRewardsFile);
        } catch (Exception exception) {
            getLogger().log(Level.SEVERE, MessageStyle.log("sw", "CONFIG",
                    "<red>Catalogue de récompenses saisonnières invalide : " + exception.getMessage()), exception);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        if (core == null) {
            getLogger().severe(MessageStyle.log("sw", "SYSTEM", "<red>TropicubeCore introuvable — SheepWars ne peut pas démarrer."));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DatabaseManager databaseManager = core.getDatabaseManager();

        this.gameManager = new GameManager(this);

        this.playerDataManager = new PlayerDataManager(this, databaseManager);
        this.progressionService = new SheepWarsProgressionService(
                databaseManager, core, kitMasteryCatalog, seasonRewardCatalog);

        this.sheepManager = new SheepManager(this);

        this.scoreboardManager = new ScoreboardManager(this);
        File menusFile = new File(getDataFolder(), "menus.yml");
        if (menusFile.exists()) {
            try (InputStream defaults = getResource("menus.yml")) {
                if (defaults == null) throw new IllegalStateException("Ressource menus.yml introuvable");
                if (SheepWarsMenuConfigMigration.migrate(menusFile, defaults)) {
                    getLogger().info(MessageStyle.log("sw", "CONFIG",
                            "<gray>Ancienne disposition des menus de cartes mise à niveau."));
                }
            } catch (Exception exception) {
                getLogger().log(Level.SEVERE, MessageStyle.log("sw", "CONFIG",
                        "<red>Migration de menus.yml impossible : " + exception.getMessage()), exception);
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
        }
        this.menuTemplates = new MenuTemplateRegistry(this);
        getServer().getServicesManager().register(UiReloadParticipant.class, menuTemplates, this,
                org.bukkit.plugin.ServicePriority.Normal);

        this.classKitMenu = new ClassKitSelectionMenu(this);
        this.teamMenu = new TeamSelectionMenu(this);
        this.mapSelectionMenu = new MapSelectionMenu(this);
        this.gameSettingsMenu = new GameSettingsMenu(this);
        this.whitelistMenu = new WhitelistMenu(this);
        this.kitMasteryMenu = new KitMasteryMenu(this);
        this.sheepManager.buildWeightCache();

        redisManager.subscribeToPlayerEvents(this::handlePlayerIdentityEvent);
        redisManager.subscribeToCommands(whitelistMenu::handleProxyCommand);

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerIdentityListener(this), this);
        getServer().getPluginManager().registerEvents(new SheepListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(mapSelectionMenu, this);
        getServer().getPluginManager().registerEvents(teamMenu, this);
        getServer().getPluginManager().registerEvents(classKitMenu, this);
        getServer().getPluginManager().registerEvents(gameSettingsMenu, this);
        getServer().getPluginManager().registerEvents(whitelistMenu, this);
        getServer().getPluginManager().registerEvents(kitMasteryMenu, this);
        java.util.Objects.requireNonNull(getCommand("sheepwars"), "Commande sheepwars absente de plugin.yml")
                .setExecutor(new SheepWarsCommand(this));

        gameManager.loadGame();
    }

    public MenuTemplateRegistry getMenuTemplates() { return menuTemplates; }

    private void handlePlayerIdentityEvent(String message) {
        if (!message.startsWith("GRADE_LOADED:")
                && !message.startsWith("GRADE_CHANGED:")) return;

        String payload = message.substring(message.indexOf(':') + 1);
        try {
            UUID playerId = UUID.fromString(payload);
            getServer().getScheduler().runTaskLater(this,
                    () -> scoreboardManager.refreshIdentity(playerId), 2L);
        } catch (IllegalArgumentException exception) {
            getLogger().warning(MessageStyle.log("sw", "SYSTEM", "<yellow>Événement d'identité avec UUID invalide : " + payload));
        }
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getServer().getAsyncScheduler().cancelTasks(this);
        shuttingDown = true;
        if (scoreboardManager != null) scoreboardManager.clearAll();
        if (gameManager != null) gameManager.shutdown();
        if (playerDataManager != null) playerDataManager.close();
        if (corePlugin != null) corePlugin.backendStopped();
        if (redisManager != null) Thread.ofPlatform().daemon(false).name("tropicube-backend-close").start(redisManager::close);
    }

    public RedisManager getRedisManager() { return redisManager; }
    public TropicubeCore getCorePlugin() { return corePlugin; }
    public GameManager getGameManager() { return gameManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public SheepManager getSheepManager() { return sheepManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public ClassKitSelectionMenu getClassKitMenu() { return classKitMenu; }
    public TeamSelectionMenu getTeamMenu() { return teamMenu; }
    public GameSettingsMenu getGameSettingsMenu() { return gameSettingsMenu; }
    public MapSelectionMenu getMapVoteMenu() { return mapSelectionMenu; }
    public WhitelistMenu getWhitelistMenu() { return whitelistMenu; }
    public GameplayBalance getGameplayBalance() { return gameplayBalance; }
    public TeamPowerUpSettings getTeamPowerUpSettings() { return teamPowerUpSettings; }
    public SheepWarsProgressionService getProgressionService() { return progressionService; }
    public KitMasteryMenu getKitMasteryMenu() { return kitMasteryMenu; }
    public KitMasteryCatalog getKitMasteryCatalog() { return kitMasteryCatalog; }
    public boolean isShuttingDown() { return shuttingDown; }
}
