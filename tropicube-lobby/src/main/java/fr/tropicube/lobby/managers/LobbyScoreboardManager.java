package fr.tropicube.lobby.managers;

import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Construit et actualise le scoreboard personnel affiché dans le lobby. */
public class LobbyScoreboardManager {

    private final TropicubeLobby plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    private static final Component SEPARATOR = Component.text("─────────────")
            .color(NamedTextColor.DARK_GRAY)
            .decorate(TextDecoration.STRIKETHROUGH);

    public LobbyScoreboardManager(TropicubeLobby plugin) {
        this.plugin = plugin;
    }

    public void setup(Player player) {
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                _ -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective old = board.getObjective("lobby");
        if (old != null) old.unregister();

        Objective obj = board.registerNewObjective("lobby", Criteria.DUMMY,
                Component.text("Tropicube", NamedTextColor.GOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        int line = 15;
        setLine(obj, line--, SEPARATOR);
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-online",
                Bukkit.getOnlinePlayers().size()));
        setLine(obj, line--, Component.empty());
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-server-label"));
        setLine(obj, line--, LangHelper.component(player, "lobby.sb-server-value"));
        setLine(obj, line, SEPARATOR);

        player.setScoreboard(board);

        // Tablist
        updateTablist(player);
    }

    public void updateTablist(Player player) {
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

    public void clear(Player player) {
        boards.remove(player.getUniqueId());
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
}
