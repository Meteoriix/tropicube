package fr.tropicube.sheepwars.scoreboard;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameState;
import fr.tropicube.sheepwars.game.GameTeam;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.util.LangHelper;
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

/** Maintient le scoreboard contextuel de chaque participant et spectateur. */
public class ScoreboardManager {

    private final TropicubeSheepwars plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    private static final Component SEPARATOR = Component.text("─────────────")
            .color(NamedTextColor.DARK_GRAY)
            .decorate(TextDecoration.STRIKETHROUGH);

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
                Component.text("SheepWars", NamedTextColor.AQUA)
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        GameState state = plugin.getGameManager().getState();
        int line = 15;

        setLine(objective, line--, SEPARATOR);

        switch (state) {
            case WAITING -> {
                setLine(objective, line--, LangHelper.component(player, "sw.sb-waiting"));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-players",
                        plugin.getGameManager().getPlayers().size()));
            }
            case STARTING -> {
                setLine(objective, line--, LangHelper.component(player, "sw.sb-starting",
                        plugin.getGameManager().getCountdown()));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-players",
                        plugin.getGameManager().getPlayers().size()));
            }
            case PLAYING -> {
                int red  = plugin.getGameManager().getAliveTeamPlayers(GameTeam.RED).size();
                int blue = plugin.getGameManager().getAliveTeamPlayers(GameTeam.BLUE).size();
                setLine(objective, line--, LangHelper.component(player, "sw.sb-red", red));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-blue", blue));
                setLine(objective, line--, Component.empty());
                String teamName = LangHelper.get(player,
                        gp.getTeam() == GameTeam.RED ? "sw.sb-team-red" : "sw.sb-team-blue");
                setLine(objective, line--, LangHelper.component(player, "sw.sb-your-team", teamName));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-kills", gp.getKills()));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-time",
                        formatTime(plugin.getGameManager().getGameTime())));
            }
            case ENDING, ENDED -> {
                setLine(objective, line--, LangHelper.component(player, "sw.sb-ending"));
                setLine(objective, line--, Component.empty());
                if (gp.getTeam() != null) {
                    String teamName = LangHelper.get(player,
                            gp.getTeam() == GameTeam.RED ? "sw.sb-team-red" : "sw.sb-team-blue");
                    setLine(objective, line--, LangHelper.component(player, "sw.sb-your-team", teamName));
                }
                setLine(objective, line--, LangHelper.component(player, "sw.sb-kills", gp.getKills()));
                setLine(objective, line--, LangHelper.component(player, "sw.sb-sheep-thrown", gp.getSheepThrown()));
            }
            default -> setLine(objective, line--, LangHelper.component(player, "sw.sb-waiting"));
        }

        setLine(objective, line, SEPARATOR);

        // ── Team glow / color setup ───────────────────────────────────────────
        setupTeamBoards(board, gp, player, state);

        player.setScoreboard(board);

        // ── Tablist header / footer (all states) ─────────────────────────────
        updateTablist(player, gp, state);
    }

    private void setupTeamBoards(Scoreboard board, GamePlayer gp, Player player, GameState state) {
        // Remove old team entries
        for (Team t : board.getTeams()) t.unregister();

        if (state != GameState.PLAYING && state != GameState.ENDING) return;
        if (gp.getTeam() == null) return;

        // Affiche les alliés avec le préfixe et la lueur de leur équipe.
        Team allyTeam = board.registerNewTeam("sw_red");
        allyTeam.color(GameTeam.RED.getColor());
        allyTeam.prefix(Component.text("❤ ", GameTeam.RED.getColor()));
        allyTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        allyTeam.setCanSeeFriendlyInvisibles(true);
        allyTeam.setAllowFriendlyFire(false);

        Team blueTeam = board.registerNewTeam("sw_blue");
        blueTeam.color(GameTeam.BLUE.getColor());
        blueTeam.prefix(Component.text("❤ ", GameTeam.BLUE.getColor()));
        blueTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        blueTeam.setCanSeeFriendlyInvisibles(true);
        blueTeam.setAllowFriendlyFire(false);

        for (GamePlayer other : plugin.getGameManager().getPlayers()) {
            Player otherP = other.getBukkitPlayer();
            if (otherP == null || other.getTeam() == null) continue;
            Team t = other.getTeam() == GameTeam.RED ? allyTeam : blueTeam;
            t.addPlayer(otherP);
        }

        // Ajoute aussi le joueur à sa propre équipe pour rendre sa lueur visible.
        Team selfTeam = board.registerNewTeam("sw_self");
        selfTeam.color(gp.getTeam().getColor());
        selfTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        selfTeam.addPlayer(player);
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
                if (gp.getTeam() == null) break;
                int red  = plugin.getGameManager().getAliveTeamPlayers(GameTeam.RED).size();
                int blue = plugin.getGameManager().getAliveTeamPlayers(GameTeam.BLUE).size();
                player.sendPlayerListHeaderAndFooter(
                        LangHelper.component(player, "sw.tab-header"),
                        LangHelper.component(player, "sw.tab-footer",
                                gp.getTeam().getDisplayName(), red, blue,
                                formatTime(plugin.getGameManager().getGameTime())));
            }
            case ENDING, ENDED -> player.sendPlayerListHeaderAndFooter(
                    LangHelper.component(player, "sw.tab-header"),
                    LangHelper.component(player, "sw.tab-footer-ending"));
            default -> player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
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
}
