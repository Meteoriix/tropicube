package fr.tropicube.sheepwars;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.managers.DatabaseManager;
import fr.tropicube.core.util.ConfigUpdater;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.sheepwars.game.GameManager;
import fr.tropicube.sheepwars.listener.PlayerListener;
import fr.tropicube.sheepwars.listener.ProtectionListener;
import fr.tropicube.sheepwars.listener.SheepListener;
import fr.tropicube.sheepwars.menu.ClassKitSelectionMenu;
import fr.tropicube.sheepwars.menu.GameSettingsMenu;
import fr.tropicube.sheepwars.menu.MapSelectionMenu;
import fr.tropicube.sheepwars.menu.TeamSelectionMenu;
import fr.tropicube.sheepwars.player.PlayerDataManager;
import fr.tropicube.sheepwars.scoreboard.ScoreboardManager;
import fr.tropicube.sheepwars.sheep.SheepManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

/** Initialise une instance Paper SheepWars et coordonne son cycle de partie. */
public final class TropicubeSheepwars extends JavaPlugin {
    private RedisManager redisManager;

    private GameManager gameManager;

    private PlayerDataManager playerDataManager;

    private SheepManager sheepManager;

    private ScoreboardManager scoreboardManager;

    private ClassKitSelectionMenu classKitMenu;
    private TeamSelectionMenu teamMenu;
    private MapSelectionMenu mapSelectionMenu;
    private GameSettingsMenu gameSettingsMenu;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
        } catch (Exception e) {
            getLogger().warning("[ConfigUpdater] config.yml: " + e.getMessage());
        }

        String redisHost = getConfig().getString("redis.host", "localhost");
        int redisPort = getConfig().getInt("redis.port", 6379);
        String redisPassword = System.getenv("TROPICUBE_REDIS_PASSWORD");
        if (redisPassword == null || redisPassword.isBlank())
            redisPassword = getConfig().getString("redis.password", "");
        try {
            redisManager = new RedisManager(redisHost, redisPort, redisPassword);
            redisManager.initialize();
            getLogger().info("Connexion Redis établie.");
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Impossible de se connecter à Redis !", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        if (core == null) {
            getLogger().severe("TropicubeCore introuvable — SheepWars ne peut pas démarrer.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DatabaseManager databaseManager = core.getDatabaseManager();

        this.gameManager = new GameManager(this);

        this.playerDataManager = new PlayerDataManager(this, databaseManager);

        this.sheepManager = new SheepManager(this);

        this.scoreboardManager = new ScoreboardManager(this);

        this.classKitMenu = new ClassKitSelectionMenu(this);
        this.teamMenu = new TeamSelectionMenu(this);
        this.mapSelectionMenu = new MapSelectionMenu(this);
        this.gameSettingsMenu = new GameSettingsMenu(this);
        this.sheepManager.buildWeightCache();

        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        getServer().getPluginManager().registerEvents(new SheepListener(this), this);
        getServer().getPluginManager().registerEvents(new ProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(mapSelectionMenu, this);
        getServer().getPluginManager().registerEvents(teamMenu, this);
        getServer().getPluginManager().registerEvents(classKitMenu, this);
        getServer().getPluginManager().registerEvents(gameSettingsMenu, this);

        gameManager.loadGame();
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null) scoreboardManager.clearAll();
        if (gameManager != null) gameManager.shutdown();
        if (playerDataManager != null) playerDataManager.close();
        if (redisManager != null) redisManager.close();
    }

    public RedisManager getRedisManager() { return redisManager; }
    public GameManager getGameManager() { return gameManager; }
    public PlayerDataManager getPlayerDataManager() { return playerDataManager; }
    public SheepManager getSheepManager() { return sheepManager; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public ClassKitSelectionMenu getClassKitMenu() { return classKitMenu; }
    public TeamSelectionMenu getTeamMenu() { return teamMenu; }
    public GameSettingsMenu getGameSettingsMenu() { return gameSettingsMenu; }
    public MapSelectionMenu getMapVoteMenu() { return mapSelectionMenu; }
}
