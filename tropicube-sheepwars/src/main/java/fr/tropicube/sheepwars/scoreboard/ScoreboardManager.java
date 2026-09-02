package fr.tropicube.sheepwars.scoreboard;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameState;
import fr.tropicube.sheepwars.game.GameTeam;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.player.PlayerClass;
import fr.tropicube.sheepwars.util.LangHelper;
import fr.tropicube.sheepwars.util.PlayerDisplayName;
import fr.tropicube.core.ui.ScoreboardTemplate;
import fr.tropicube.core.ui.UiReloadParticipant;
import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.language.PlaceholderValues;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.*;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Maintains the contextual scoreboard of each participant and viewer. */
public class ScoreboardManager implements UiReloadParticipant {

    private final TropicubeSheepwars plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private volatile ScoreboardTemplate template;

    public ScoreboardManager(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        prepareReload().run();
        Bukkit.getServicesManager().register(UiReloadParticipant.class, this, plugin, ServicePriority.Normal);
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
                LangHelper.component(player, template.titleKey())
        );
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        GameState state = plugin.getGameManager().getState();
        String mapName = plugin.getGameManager().getSelectedMap() == null
                ? LangHelper.get(player, "sw.sb-map-unknown")
                : plugin.getGameManager().getSelectedMap().getName();
        boolean spectator = gp.getTeam() == null || !gp.isAlive();
        int red = plugin.getGameManager().getAliveTeamPlayers(GameTeam.RED).size();
        int blue = plugin.getGameManager().getAliveTeamPlayers(GameTeam.BLUE).size();
        PlaceholderValues.Builder placeholders = PlaceholderValues.builder()
                .put("current_players", plugin.getGameManager().getPlayers().size())
                .put("max_players", plugin.getGameManager().getMaxPlayers())
                .put("min_players", plugin.getGameManager().getMinPlayers())
                .put("map", mapName)
                .put("countdown", plugin.getGameManager().getCountdown())
                .put("time", formatTime(plugin.getGameManager().getGameTime()))
                .put("red_players", red)
                .put("blue_players", blue)
                .putComponent("player_class", MessageStyle.component(localizedClassName(player, gp.getPlayerClass())))
                .put("kills", gp.getKills())
                .put("sheep_thrown", gp.getSheepThrown());
        if (!spectator) {
            placeholders.putComponent("team", MessageStyle.component(localizedTeamName(player, gp.getTeam())));
        }
        String phase = switch (state) {
            case WAITING -> "waiting";
            case STARTING -> "starting";
            case PLAYING -> "playing";
            case ENDING, ENDED -> "ending";
            default -> "waiting";
        };
        PlaceholderValues values = placeholders.build();
        int line = 15;
        for (ScoreboardTemplate.Line definition : template.lines(phase + (spectator ? "_spectator" : "_alive"))) {
            Component display = definition.blank() ? Component.empty()
                    : LangHelper.component(player, definition.key(), values);
            setLine(objective, line--, display);
        }

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
                            LangHelper.component(player, "sw.tab-footer-spectator", PlaceholderValues.builder()
                                    .put("red_players", red)
                                    .put("blue_players", blue)
                                    .put("time", formatTime(plugin.getGameManager().getGameTime()))
                                    .build()));
                } else {
                    String teamName = localizedTeamName(player, gp.getTeam());
                    player.sendPlayerListHeaderAndFooter(
                            LangHelper.component(player, "sw.tab-header"),
                            LangHelper.component(player, "sw.tab-footer", PlaceholderValues.builder()
                                    .putComponent("team", MessageStyle.component(teamName))
                                    .put("red_players", red)
                                    .put("blue_players", blue)
                                    .put("time", formatTime(plugin.getGameManager().getGameTime()))
                                    .build()));
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

    @Override
    public Runnable prepareReload() {
        ScoreboardTemplate prepared = ScoreboardTemplate.load(plugin, "scoreboards.yml", "sheepwars");
        return () -> template = prepared;
    }

    @Override
    public void refreshViewers() {
        updateAll();
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
