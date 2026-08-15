package fr.tropicube.lobby;

import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.core.util.ConfigUpdater;
import fr.tropicube.docker.client.RedisManager;
import fr.tropicube.lobby.commands.FlyModeCommand;
import fr.tropicube.lobby.commands.HubCommand;
import fr.tropicube.lobby.commands.LangCommand;
import fr.tropicube.lobby.commands.PlayNextCommand;
import fr.tropicube.lobby.commands.ReplayConfirmCommand;
import fr.tropicube.lobby.commands.ServersCommand;
import fr.tropicube.lobby.commands.SheepwarsRejoinCommand;
import fr.tropicube.lobby.commands.VipCommand;
import fr.tropicube.lobby.gui.GuiManager;
import fr.tropicube.lobby.listeners.GuiClickListener;
import fr.tropicube.lobby.listeners.LobbyProtectionListener;
import fr.tropicube.lobby.listeners.PlayerLobbyListener;
import fr.tropicube.lobby.managers.LobbyScoreboardManager;
import fr.tropicube.lobby.managers.LobbyServerManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Lobby Paper entry point. It configures the welcome experience,
 * selection menus and communication with dynamic instances.
 */
public class TropicubeLobby extends JavaPlugin {

    private static TropicubeLobby instance;

    private RedisManager redisManager;
    private LobbyServerManager lobbyServerManager;
    private GuiManager guiManager;
    private PlayerLobbyListener playerLobbyListener;
    private LobbyScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        try {
            ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
            reloadConfig();
        } catch (Exception e) {
            getLogger().warning(MessageStyle.log("tc", "CONFIG", "<yellow>config.yml: " + e.getMessage()));
        }

        // Init Redis
        String redisHost = getConfig().getString("redis.host", "localhost");
        int redisPort = getConfig().getInt("redis.port", 6379);
        String redisPassword = environmentOrConfig("TROPICUBE_REDIS_PASSWORD", "redis.password");
        try {
            redisManager = new RedisManager(redisHost, redisPort, redisPassword);
            redisManager.initialize();
            getLogger().info(MessageStyle.log("tc", "LOBBY", "<gray>Connexion Redis établie."));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, MessageStyle.log("tc", "LOBBY", "<red>Impossible de se connecter à Redis !"), e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Managers
        lobbyServerManager = new LobbyServerManager(this, redisManager);
        guiManager = new GuiManager(this);
        scoreboardManager = new LobbyScoreboardManager(this);

        // Listeners
        playerLobbyListener = new PlayerLobbyListener(this);
        Bukkit.getPluginManager().registerEvents(playerLobbyListener, this);
        Bukkit.getPluginManager().registerEvents(new LobbyProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GuiClickListener(this), this);

        // Commandes
        registerCommands();

        // Refreshing server data every 5 s + updating open GUIs.
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            lobbyServerManager.refreshServerList();
            Bukkit.getScheduler().runTask(this, () -> {
                guiManager.refreshOpenServerGuis();
                scoreboardManager.refreshAll();
            });
        }, 0L, 100L);

        // Listening to proxy responses (failed to create/stop custom game).
        redisManager.subscribeToCommands(message -> {
            // Format: "LOBBY:CREATE_HOST_FAILED:<uuid>"
            if (message.startsWith("LOBBY:CREATE_HOST_FAILED:")) {
                String uuidStr = message.substring("LOBBY:CREATE_HOST_FAILED:".length());
                notifyPlayer(uuidStr, "lobby.custom-game-failed");
                return;
            }
            // Format: "LOBBY:STOP_HOST_FAILED:<uuid>"
            if (message.startsWith("LOBBY:STOP_HOST_FAILED:")) {
                String uuidStr = message.substring("LOBBY:STOP_HOST_FAILED:".length());
                notifyPlayer(uuidStr, "lobby.host-stop-failed");
                return;
            }
            // Format: "LOBBY:CREATE_HOST_EXISTS:<uuid>"
            if (message.startsWith("LOBBY:CREATE_HOST_EXISTS:")) {
                String uuidStr = message.substring("LOBBY:CREATE_HOST_EXISTS:".length());
                notifyPlayer(uuidStr, "lobby.host-already-exists");
                return;
            }
            // Format: "LOBBY:GAME_START_FAILED:<uuid>"
            if (message.startsWith("LOBBY:GAME_START_FAILED:")) {
                String uuidStr = message.substring("LOBBY:GAME_START_FAILED:".length());
                notifyPlayer(uuidStr, "lobby.game-start-failed");
            }
        });

        // Synchronizes localized elements when a language changes from the
        // lobby menu, /lang or another network instance.
        redisManager.subscribeToPlayerEvents(message -> {
            if (message.startsWith("NICK_APPLY:") || message.startsWith("NICK_RESET:")
                    || message.startsWith("NICK_CLEAR:") || message.startsWith("GRADE_LOADED:")
                    || message.startsWith("GRADE_CHANGED:")) {
                String payload = message.substring(message.indexOf(':') + 1);
                try {
                    java.util.UUID playerId = java.util.UUID.fromString(payload);
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            scoreboardManager.updateTablist(player);
                            playerLobbyListener.setupHotbar(player);
                        }
                    }, 2L);
                } catch (IllegalArgumentException e) {
                    getLogger().warning(MessageStyle.log("tc", "LOBBY", "<yellow>Événement d'identité avec UUID invalide : " + payload));
                }
                return;
            }
            if (!message.startsWith("LANG_CHANGED:")) return;
            String payload = message.substring("LANG_CHANGED:".length());
            int separator = payload.indexOf(':');
            if (separator < 0) return;
            try {
                java.util.UUID playerId = java.util.UUID.fromString(payload.substring(0, separator));
                Bukkit.getScheduler().runTask(this, () -> {
                    org.bukkit.entity.Player player = Bukkit.getPlayer(playerId);
                    if (player == null) return;
                    playerLobbyListener.setupHotbar(player);
                    scoreboardManager.setup(player);
                });
            } catch (IllegalArgumentException e) {
                getLogger().warning(MessageStyle.log("tc", "LOBBY", "<yellow>Événement LANG_CHANGED avec UUID invalide : " + payload));
            }
        });

        getLogger().info(MessageStyle.log("tc", "LOBBY", "<gray>Tropicube Lobby activé !"));
    }

    @Override
    public void onDisable() {
        if (scoreboardManager != null) scoreboardManager.clearAll();
        if (guiManager != null) guiManager.clearAll();
        if (redisManager != null) redisManager.close();
        instance = null;
        getLogger().info(MessageStyle.log("tc", "LOBBY", "<gray>Tropicube Lobby désactivé."));
    }

    private void notifyPlayer(String uuidStr, String langKey) {
        try {
            java.util.UUID uuid = java.util.UUID.fromString(uuidStr);
            Bukkit.getScheduler().runTask(this, () -> {
                org.bukkit.entity.Player player = Bukkit.getPlayer(uuid);
                if (player != null)
                    player.sendMessage(fr.tropicube.lobby.utils.LangHelper.component(player, langKey));
            });
        } catch (IllegalArgumentException e) {
            getLogger().warning(MessageStyle.log("tc", "LOBBY", "<yellow>Réponse proxy avec UUID invalide : " + uuidStr));
        }
    }

    private void registerCommands() {
        Objects.requireNonNull(getCommand("spawn"), "Commande spawn absente de plugin.yml").setExecutor(new HubCommand(this));
        Objects.requireNonNull(getCommand("play"), "Commande play absente de plugin.yml").setExecutor(new ServersCommand(this));
        Objects.requireNonNull(getCommand("languages"), "Commande languages absente de plugin.yml").setExecutor(new LangCommand(this));
        Objects.requireNonNull(getCommand("vip"), "Commande vip absente de plugin.yml").setExecutor(new VipCommand(this));
        Objects.requireNonNull(getCommand("fly"), "Commande fly absente de plugin.yml").setExecutor(new FlyModeCommand(this));
        Objects.requireNonNull(getCommand("replay"), "Commande replay absente de plugin.yml").setExecutor(new PlayNextCommand(this));
        Objects.requireNonNull(getCommand("replayconfirm"), "Commande replayconfirm absente de plugin.yml")
                .setExecutor(new ReplayConfirmCommand(this));
        var rejoinCommand = new SheepwarsRejoinCommand(this);
        Objects.requireNonNull(getCommand("rejoin"), "Commande rejoin absente de plugin.yml").setExecutor(rejoinCommand);
    }

    public static TropicubeLobby getInstance() { return instance; }
    public RedisManager getRedisManager() { return redisManager; }
    private String environmentOrConfig(String environmentName, String configPath) {
        String value = System.getenv(environmentName);
        return value == null || value.isBlank() ? getConfig().getString(configPath, "") : value;
    }
    public LobbyServerManager getLobbyServerManager() { return lobbyServerManager; }
    public GuiManager getGuiManager() { return guiManager; }
    public PlayerLobbyListener getPlayerLobbyListener() { return playerLobbyListener; }
    public LobbyScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public int getAutoReplayBatchSize() {
        return Math.max(1, Math.min(100, getConfig().getInt("auto-replay.batch-size", 5)));
    }
}
