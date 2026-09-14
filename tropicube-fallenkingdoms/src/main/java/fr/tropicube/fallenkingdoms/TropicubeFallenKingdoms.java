package fr.tropicube.fallenkingdoms;

import fr.tropicube.fallenkingdoms.config.FallenKingdomsSettings;
import fr.tropicube.fallenkingdoms.game.GameState;
import fr.tropicube.fallenkingdoms.game.GameStateMachine;
import fr.tropicube.fallenkingdoms.game.GameSession;
import fr.tropicube.fallenkingdoms.listener.GameListener;
import fr.tropicube.fallenkingdoms.listener.ProtectionListener;
import fr.tropicube.fallenkingdoms.listener.LobbyMenuListener;
import fr.tropicube.fallenkingdoms.map.MapCatalog;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.language.PlaceholderValues;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.core.statistics.GameStatisticsService;
import fr.tropicube.core.statistics.NetworkGameResult;
import fr.tropicube.fallenkingdoms.game.GameResult;
import fr.tropicube.fallenkingdoms.game.PlayerSession;
import fr.tropicube.fallenkingdoms.game.EndCause;
import java.util.Collection;
import java.util.stream.Collectors;

/** Boots one ephemeral Fallen Kingdoms Paper instance. */
public final class TropicubeFallenKingdoms extends JavaPlugin {
    private final GameStateMachine stateMachine = new GameStateMachine();
    private FallenKingdomsSettings settings;
    private GameSession session;
    private GameListener gameListener;
    private ProtectionListener protectionListener;
    private LobbyMenuListener lobbyMenuListener;
    @Override public void onEnable() {
        saveDefaultConfig();
        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        if (core == null) { getServer().getPluginManager().disablePlugin(this); return; }
        core.initializeBackend(this, () -> { }, this::finishStartup);
    }
    private void finishStartup() {
        try { loadSession(); updateInstanceStatus(ServerInstance.Status.GAME_WAITING); }
        catch (IllegalArgumentException exception) { getLogger().severe("Configuration Fallen Kingdoms invalide: " + exception.getMessage()); getServer().getPluginManager().disablePlugin(this); }
    }
    private void loadSession() {
        FallenKingdomsSettings loadedSettings = FallenKingdomsSettings.load(getConfig());
        MapCatalog maps = MapCatalog.load(getConfig());
        String selectedMap = System.getenv("MAP_ID");
        if (selectedMap == null || selectedMap.isBlank()) selectedMap = getConfig().getString("game.default-map", "");
        GameSession loadedSession = new GameSession(this, stateMachine, maps.select(selectedMap), loadedSettings);
        if (gameListener != null) HandlerList.unregisterAll(gameListener);
        if (protectionListener != null) HandlerList.unregisterAll(protectionListener);
        if (lobbyMenuListener != null) HandlerList.unregisterAll(lobbyMenuListener);
        if (session != null) session.shutdown();
        settings = loadedSettings;
        session = loadedSession;
        gameListener = new GameListener(session);
        protectionListener = new ProtectionListener(session);
        lobbyMenuListener = new LobbyMenuListener(this, session);
        getServer().getPluginManager().registerEvents(gameListener, this);
        getServer().getPluginManager().registerEvents(protectionListener, this);
        getServer().getPluginManager().registerEvents(lobbyMenuListener, this);
    }
    @Override public void onDisable() {
        if (session != null) session.shutdown();
        if (stateMachine.state().active()) stateMachine.transitionTo(GameState.ENDING);
        if (getServer().getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core) core.backendStopped();
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fallenkingdoms.admin")) { message(sender, "fk.permission-denied"); return true; }
        String action = args.length == 0 ? "status" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("status")) status(sender);
        else if (action.equals("start")) message(sender, session.startCountdown() ? "fk.countdown-started" : "fk.transition-refused");
        else if (action.equals("cancel")) message(sender, session.cancelCountdown() ? "fk.countdown-cancelled" : "fk.transition-refused");
        else if (action.equals("stop")) { session.abort(); message(sender, "fk.stopped"); }
        else if (action.equals("reload") && stateMachine.state() == GameState.WAITING) {
            reloadConfig();
            try { loadSession(); message(sender, "fk.reloaded"); }
            catch (IllegalArgumentException exception) { message(sender, "fk.reload-failed", PlaceholderValues.of("error", exception.getMessage())); }
        }
        else message(sender, "fk.usage");
        return true;
    }
    public FallenKingdomsSettings settings() { return settings; }
    public GameStateMachine stateMachine() { return stateMachine; }
    /** Publishes the terminal lifecycle command without blocking the Paper thread. */
    public void finishInstance(GameResult result, Collection<PlayerSession> players) {
        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        String instanceId = System.getenv("INSTANCE_ID");
        if (core == null) return;
        Runnable transfer = () -> {
            if (instanceId != null && !instanceId.isBlank()) core.getRedisManager().publishCommand("PROXY", "FINISH_GAME:" + instanceId);
        };
        if (result.cause() == EndCause.ADMIN_ABORT) { getServer().getScheduler().runTaskAsynchronously(this, transfer); return; }
        var networkResult = new NetworkGameResult(result.sessionId(), "fallenkingdoms", result.cause().name(),
                result.winners().stream().map(Enum::name).collect(Collectors.toUnmodifiableSet()), result.endedAt(),
                players.stream().map(player -> new NetworkGameResult.PlayerResult(player.playerId(),
                        result.winners().contains(player.kingdom()), result.cause() == EndCause.DRAW,
                        player.eliminations(), player.deaths(), player.objectives())).toList());
        new GameStatisticsService(core.getDatabaseManager(), core.getRedisManager()).persist(networkResult)
                .whenComplete((inserted, failure) -> {
                    if (failure != null) getLogger().log(java.util.logging.Level.SEVERE,
                            "Échec de persistance du résultat " + result.sessionId(), failure);
                    transfer.run();
                });
    }
    public void updateInstanceStatus(ServerInstance.Status status) {
        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        String instanceId = System.getenv("INSTANCE_ID");
        if (core == null || instanceId == null || instanceId.isBlank()) return;
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            ServerInstance instance = core.getRedisManager().getInstance(instanceId);
            if (instance == null) return;
            instance.setStatus(status);
            core.getRedisManager().saveInstance(instance);
        });
    }
    private void message(CommandSender sender, String key, Object... arguments) {
        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        if (core != null) sender.sendMessage(core.getLanguageManager().getComponent(
                sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null, key, arguments));
    }
    private void status(CommandSender sender) {
        TropicubeCore core = (TropicubeCore) getServer().getPluginManager().getPlugin("TropicubeCore");
        if (core != null) sender.sendMessage(core.getLanguageManager().getComponent(
                sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null, "fk.status",
                PlaceholderValues.builder().putComponent("state", core.getLanguageManager().getComponent(
                                sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null,
                                stateDisplayKey(stateMachine.state())))
                        .putComponent("map", core.getLanguageManager().getComponent(
                                sender instanceof org.bukkit.entity.Player player ? player.getUniqueId() : null,
                                session.mapDisplayNameKey()))
                        .put("players", session.participantCount()).build()));
    }
    private static String stateDisplayKey(GameState state) {
        return switch (state) {
            case WAITING -> "fk.state-waiting";
            case COUNTDOWN -> "fk.state-countdown";
            case PREPARATION -> "fk.state-preparation";
            case PVP -> "fk.state-pvp";
            case ASSAULT -> "fk.state-assault";
            case SUDDEN_DEATH -> "fk.state-sudden-death";
            case ENDING -> "fk.state-ending";
            case ENDED -> "fk.state-ended";
        };
    }
}
