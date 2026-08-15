package fr.tropicube.lobby.managers;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Builds and updates the personal scoreboard displayed in the lobby. */
public class LobbyScoreboardManager {

    private final TropicubeLobby plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, String> balances = new ConcurrentHashMap<>();
    private final Set<UUID> balanceRefreshes = ConcurrentHashMap.newKeySet();

    public LobbyScoreboardManager(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    public void setup(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                _ -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective old = board.getObjective("lobby");
        if (old != null) old.unregister();

        Objective obj = board.registerNewObjective("lobby", Criteria.DUMMY,
                LangHelper.component(player, "lobby.sb-title"));
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

        int line = 15;
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-separator"));
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-profile"));
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-profile-value", formattedName));
        setLine(obj, line--, Component.empty());
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-balance", balance));
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-network-online", networkPlayers));
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-games", visibleGames));
        setLine(obj, line--, Component.empty());
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-server-label"));
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-server-value"));
        setLine(obj, line, LangHelper.component(player, "lobby.sb-separator"));

        player.setScoreboard(board);

        // Tablist
        updateTablist(player);
        if (!balances.containsKey(playerId)) refreshBalance(playerId);
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
        }
        updateAll();
    }

    public void clear(Player player) {
        UUID playerId = player.getUniqueId();
        boards.remove(playerId);
        balances.remove(playerId);
        balanceRefreshes.remove(playerId);
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
}
