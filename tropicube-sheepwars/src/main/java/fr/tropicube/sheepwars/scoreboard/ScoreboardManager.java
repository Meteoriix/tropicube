package fr.tropicube.sheepwars.scoreboard;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameState;
import fr.tropicube.sheepwars.game.GameTeam;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.player.PlayerClass;
import fr.tropicube.sheepwars.util.LangHelper;
import fr.tropicube.sheepwars.util.PlayerDisplayName;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Maintains the contextual scoreboard of each participant and viewer. */
public class ScoreboardManager {

    private final TropicubeSheepwars plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public ScoreboardManager(TropicubeSheepwars plugin) {
        this.plugin = plugin;
    }

    public void updateAll() {
        for (GamePlayer gp : plugin.getGameManager().getPlayers()) {
            Player p = gp.getBukkitPlayer();
            if (p != null) update(p);
        }
    }

    public void update(Player player) {
        GamePlayer gp = plugin.getGameManager().getPlayer(player);
        if (gp == null) return;

        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(),
                _ -> Bukkit.getScoreboardManager().getNewScoreboard());

        Objective old = board.getObjective("sheepwars");
        if (old != null) old.unregister();

        Objective objective = board.registerNewObjective(
                "sheepwars",
                Criteria.DUMMY,
                LangHelper.component(player, "sw.sb-title")
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        GameState state = plugin.getGameManager().getState();
        String mapName = plugin.getGameManager().getSelectedMap() == null
                ? LangHelper.get(player, "sw.sb-map-unknown")
                : plugin.getGameManager().getSelectedMap().getName();
        int line = 15;

        setLine(objective, line--, LangHelper.component(player, "sw.sb-separator"));
        switch (state) {
            case WAITING -> {
                setLine(objective, line--, LangHelper.component(player, "sw.sb-waiting"));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-players",
                        plugin.getGameManager().getPlayers().size(), plugin.getGameManager().getMaxPlayers()));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-min-players",
                        plugin.getGameManager().getMinPlayers()));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-map", mapName));
                line = addPersonalSection(objective, line, player, gp, false);
            }
            case STARTING -> {
                setLine(objective, line--, LangHelper.component(player, "sw.sb-starting",
                        plugin.getGameManager().getCountdown()));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-players",
                        plugin.getGameManager().getPlayers().size(), plugin.getGameManager().getMaxPlayers()));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-map", mapName));
                line = addPersonalSection(objective, line, player, gp, false);
            }
            case PLAYING -> {
                int red  = plugin.getGameManager().getAliveTeamPlayers(GameTeam.RED).size();
                int blue = plugin.getGameManager().getAliveTeamPlayers(GameTeam.BLUE).size();
                setLine(objective, line--, LangHelper.component(player, "sw.sb-time",
                        formatTime(plugin.getGameManager().getGameTime())));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-map", mapName));
                setLine(objective, line--, Component.empty());
                setLine(objective, line--, LangHelper.component(player, "sw.sb-red", red));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-blue", blue));
                line = addPersonalSection(objective, line, player, gp, true);
            }
            case ENDING, ENDED -> {
                setLine(objective, line--, LangHelper.component(player, "sw.sb-ending"));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-map", mapName));
                setLine(objective, line--, Component.empty());
                line = addPersonalSection(objective, line, player, gp, true);
            }
            default -> setLine(objective, line--, LangHelper.component(player, "sw.sb-waiting"));
        }
        setLine(objective, line, LangHelper.component(player, "sw.sb-separator"));

        // ── Team glow / color setup ───────────────────────────────────────────
        setupTeamBoards(board, state);

        player.setScoreboard(board);

        // ── Tablist header / footer (all states) ─────────────────────────────
        updateTablist(player, gp, state);
    }

    private void setupTeamBoards(Scoreboard board, GameState state) {
        // Remove old team entries
        for (Team t : board.getTeams()) t.unregister();

        if (state != GameState.PLAYING && state != GameState.ENDING) return;
        Team redTeam = board.registerNewTeam("sw_red");
        configureTeam(redTeam, GameTeam.RED.getColor());

        Team blueTeam = board.registerNewTeam("sw_blue");
        configureTeam(blueTeam, GameTeam.BLUE.getColor());

        Team spectatorTeam = board.registerNewTeam("sw_spectator");
        configureTeam(spectatorTeam, NamedTextColor.GRAY);

        for (GamePlayer other : plugin.getGameManager().getPlayers()) {
            Player otherP = other.getBukkitPlayer();
            if (otherP == null) continue;
            Team targetTeam = !other.isAlive() || other.getTeam() == null
                    ? spectatorTeam
                    : other.getTeam() == GameTeam.RED ? redTeam : blueTeam;
            targetTeam.addEntry(visibleProfileName(otherP));
        }
    }

    private void configureTeam(Team team, NamedTextColor color) {
        team.color(color);
        team.prefix(Component.text("❤ ", color));
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(true);
        team.setAllowFriendlyFire(false);
    }

    private void updateTablist(Player player, GamePlayer gp, GameState state) {
        switch (state) {
            case WAITING -> player.sendPlayerListHeaderAndFooter(
                    LangHelper.component(player, "sw.tab-header"),
                    LangHelper.component(player, "sw.tab-footer-waiting",
                            plugin.getGameManager().getPlayers().size()));
            case STARTING -> player.sendPlayerListHeaderAndFooter(
                    LangHelper.component(player, "sw.tab-header"),
                    LangHelper.component(player, "sw.tab-footer-starting",
                            plugin.getGameManager().getCountdown()));
            case PLAYING -> {
                int red  = plugin.getGameManager().getAliveTeamPlayers(GameTeam.RED).size();
                int blue = plugin.getGameManager().getAliveTeamPlayers(GameTeam.BLUE).size();
                if (gp.getTeam() == null || !gp.isAlive()) {
                    player.sendPlayerListHeaderAndFooter(
                            LangHelper.component(player, "sw.tab-header"),
                            LangHelper.component(player, "sw.tab-footer-spectator", red, blue,
                                    formatTime(plugin.getGameManager().getGameTime())));
                } else {
                    String teamName = localizedTeamName(player, gp.getTeam());
                    player.sendPlayerListHeaderAndFooter(
                            LangHelper.component(player, "sw.tab-header"),
                            LangHelper.component(player, "sw.tab-footer",
                                    teamName, red, blue,
                                    formatTime(plugin.getGameManager().getGameTime())));
                }
            }
            case ENDING, ENDED -> player.sendPlayerListHeaderAndFooter(
                    LangHelper.component(player, "sw.tab-header"),
                    LangHelper.component(player, "sw.tab-footer-ending"));
            default -> player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        }

        applyPlayerListName(player, gp);
    }

    private String localizedTeamName(Player player, GameTeam team) {
        return LangHelper.get(player, team == GameTeam.RED ? "sw.sb-team-red" : "sw.sb-team-blue");
    }

    private int addPersonalSection(
            Objective objective,
            int line,
            Player player,
            GamePlayer gamePlayer,
            boolean includeStatistics
    ) {
        setLine(objective, line--, LangHelper.component(player, "sw.sb-separator"));
        if (gamePlayer.getTeam() == null || !gamePlayer.isAlive()) {
            setLine(objective, line--, LangHelper.component(player, "sw.sb-spectator"));
        } else {
            setLine(objective, line--, LangHelper.component(player, "sw.sb-your-team",
                    localizedTeamName(player, gamePlayer.getTeam())));
        }
        setLine(objective, line--, LangHelper.component(player, "sw.sb-class",
                localizedClassName(player, gamePlayer.getPlayerClass())));
        if (includeStatistics) {
            setLine(objective, line--, LangHelper.component(player, "sw.sb-kills", gamePlayer.getKills()));
            setLine(objective, line--, LangHelper.component(player, "sw.sb-sheep-thrown",
                    gamePlayer.getSheepThrown()));
        }
        return line;
    }

    private String localizedClassName(Player player, PlayerClass playerClass) {
        return LangHelper.get(player, "sw.sb-class-" + playerClass.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Reasserts the SheepWars-owned tablist identity after Core changes a nick
     * profile or display grade. Scoreboard teams are rebuilt because their
     * entries depend on the profile name sent to the client.
     */
    public void refreshIdentity(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) return;
        GamePlayer gamePlayer = plugin.getGameManager().getPlayer(player);
        if (gamePlayer == null) return;

        applyPlayerListName(player, gamePlayer);
        GameState state = plugin.getGameManager().getState();
        boards.values().forEach(board -> setupTeamBoards(board, state));
    }

    private void applyPlayerListName(Player player, GamePlayer gamePlayer) {
        player.playerListName(teamColoredName(
                PlayerDisplayName.resolve(player), playerListColor(gamePlayer)));
    }

    static NamedTextColor playerListColor(GamePlayer gamePlayer) {
        return gamePlayer.getTeam() == null || !gamePlayer.isAlive()
                ? NamedTextColor.GRAY : gamePlayer.getTeam().getColor();
    }

    static Component teamColoredName(String visibleName, NamedTextColor color) {
        return Component.text(visibleName, color);
    }

    public void clear(Player player) {
        boards.remove(player.getUniqueId());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        player.playerListName(Component.text(PlayerDisplayName.resolve(player)));
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    public void clearAll() {
        for (UUID uuid : List.copyOf(boards.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) clear(player);
            else boards.remove(uuid);
        }
    }

    private void setLine(Objective objective, int lineNum, Component display) {
        Score score = objective.getScore("sw_" + lineNum);
        score.setScore(lineNum);
        score.customName(display);
    }

    private String formatTime(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format("%02d:%02d", min, sec);
    }

    private String visibleProfileName(Player player) {
        String profileName = player.getPlayerProfile().getName();
        return profileName == null || profileName.isBlank() ? player.getName() : profileName;
    }
}
