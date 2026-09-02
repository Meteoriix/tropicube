package fr.tropicube.lobby.managers;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import fr.tropicube.core.ui.ScoreboardTemplate;
import fr.tropicube.core.ui.UiReloadParticipant;
import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.language.PlaceholderValues;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Builds and updates the personal scoreboard displayed in the lobby. */
public class LobbyScoreboardManager implements UiReloadParticipant {

    private final TropicubeLobby plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, String> balances = new ConcurrentHashMap<>();
    private final Set<UUID> balanceRefreshes = ConcurrentHashMap.newKeySet();
    private final Map<UUID, QueueSnapshot> queueSnapshots = new ConcurrentHashMap<>();
    private final Set<UUID> queueRefreshes = ConcurrentHashMap.newKeySet();
    private volatile ScoreboardTemplate template;

    public LobbyScoreboardManager(TropicubeLobby plugin) {
        this.plugin = plugin;
        prepareReload().run();
        Bukkit.getServicesManager().register(UiReloadParticipant.class, this, plugin, ServicePriority.Normal);
    }

    public void setup(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                _ -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective old = board.getObjective("lobby");
        if (old != null) old.unregister();

        Objective obj = board.registerNewObjective("lobby", Criteria.DUMMY,
                LangHelper.component(player, template.titleKey()));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        UUID playerId = player.getUniqueId();
        String visibleName = LangHelper.getVisibleName(player);
        String formattedName = LangHelper.getDisplayFormattedName(playerId, visibleName);
        String balance = balances.getOrDefault(playerId, LangHelper.get(player, "lobby.sb-loading"));
        int networkPlayers = plugin.getLobbyServerManager().getTotalPlayers();
        long visibleGames = plugin.getLobbyServerManager().getAllServers().stream()
                .filter(server -> !"lobby".equalsIgnoreCase(server.type()))
                .filter(LobbyServerManager.ServerInfo::isListed)
                .filter(server -> server.isVisibleTo(playerId))
                .count();

        QueueSnapshot queue = queueSnapshots.get(playerId);
        boolean queued = queue != null && queue.templateId() != null;
        PlaceholderValues.Builder values = PlaceholderValues.builder()
                .putComponent("profile", MessageStyle.component(formattedName))
                .put("balance", balance)
                .put("online_players", networkPlayers)
                .put("visible_games", visibleGames);
        if (queued) {
            values.putComponent("queue", MessageStyle.component(LangHelper.get(player, queue.labelKey())))
                    .put("reserved_players", queue.reservedPlayers())
                    .put("capacity", queue.capacity())
                    .put("wait_seconds", queue.waitSeconds());
        }
        PlaceholderValues resolvedValues = values.build();
        int line = 15;
        for (ScoreboardTemplate.Line definition : template.lines(queued ? "queued" : "idle")) {
            Component display = definition.blank() ? Component.empty()
                    : LangHelper.component(player, definition.key(), resolvedValues);
            setLine(obj, line--, display);
        }

        player.setScoreboard(board);

        // Tablist
        updateTablist(player);
        if (!balances.containsKey(playerId)) refreshBalance(playerId);
        if (!queueSnapshots.containsKey(playerId)) refreshQueue(playerId);
    }

    public void updateTablist(Player player) {
        player.playerListName(LangHelper.getFormattedNameComponent(player));
        player.sendPlayerListHeaderAndFooter(
                LangHelper.component(player, "lobby.tab-header"),
                LangHelper.component(player, "lobby.tab-footer",
                        Bukkit.getOnlinePlayers().size(),
                        Bukkit.getMaxPlayers()));
    }

    public void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            setup(p);
        }
    }

    /** Refreshes cached economy data and redraws server counters. */
    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refreshBalance(player.getUniqueId());
            refreshQueue(player.getUniqueId());
        }
        updateAll();
    }

    public void clear(Player player) {
        UUID playerId = player.getUniqueId();
        boards.remove(playerId);
        balances.remove(playerId);
        balanceRefreshes.remove(playerId);
        queueSnapshots.remove(playerId);
        queueRefreshes.remove(playerId);
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    public void clearAll() {
        for (UUID uuid : List.copyOf(boards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) clear(player);
            else boards.remove(uuid);
        }
    }

    @Override
    public Runnable prepareReload() {
        ScoreboardTemplate prepared = ScoreboardTemplate.load(plugin, "scoreboards.yml", "lobby");
        return () -> template = prepared;
    }

    @Override
    public void refreshViewers() {
        updateAll();
    }

    private void setLine(Objective obj, int lineNum, Component display) {
        Score score = obj.getScore("lob_" + lineNum);
        score.setScore(lineNum);
        score.customName(display);
    }

    private void refreshBalance(UUID playerId) {
        if (!balanceRefreshes.add(playerId)) return;
        LangHelper.getFormattedBalanceAsync(playerId).whenComplete((formattedBalance, error) -> {
            balanceRefreshes.remove(playerId);
            balances.put(playerId, formattedBalance == null ? "—" : formattedBalance);
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) setup(player);
            });
        });
    }

    private void refreshQueue(UUID playerId) {
        if (!queueRefreshes.add(playerId)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getLobbyServerManager().refreshPlayerMatchmaking(playerId);
                String templateId = plugin.getLobbyServerManager().getActiveMatchmaking(playerId).orElse(null);
                if (templateId == null) {
                    queueSnapshots.put(playerId, QueueSnapshot.none());
                    return;
                }
                var stats = plugin.getLobbyServerManager().getRankedStats(templateId).orElse(null);
                long since = plugin.getLobbyServerManager().getMatchmakingSince(playerId);
                long wait = since <= 0 ? 0 : Math.max(0, (System.currentTimeMillis() - since) / 1000);
                queueSnapshots.put(playerId, new QueueSnapshot(templateId, queueLabelKey(templateId),
                        stats == null ? 0 : stats.reservedPlayers(), stats == null ? 0 : stats.capacity(), wait));
            } finally {
                queueRefreshes.remove(playerId);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player online = Bukkit.getPlayer(playerId);
                    if (online != null) setup(online);
                });
            }
        });
    }

    private static String queueLabelKey(String templateId) {
        String normalized = templateId.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("4v4")) return "lobby.sb-queue-label-ranked-4v4";
        if (normalized.contains("8v8")) return "lobby.sb-queue-label-ranked-8v8";
        return "lobby.sb-queue-label-quick-play";
    }

    private record QueueSnapshot(String templateId, String labelKey, int reservedPlayers, int capacity, long waitSeconds) {
        private static QueueSnapshot none() { return new QueueSnapshot(null, "", 0, 0, 0); }
    }
}
