package fr.tropicube.fallenkingdoms.hud;

import fr.tropicube.core.TropicubeCore;
import fr.tropicube.core.ui.ScoreboardTemplate;
import fr.tropicube.core.ui.TablistTemplate;
import fr.tropicube.core.ui.UiReloadParticipant;
import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import fr.tropicube.fallenkingdoms.config.HeartAlertSettings;
import fr.tropicube.fallenkingdoms.game.*;
import fr.tropicube.language.PlaceholderValues;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

import java.util.*;

/** Localized HUD and viewer-owned name-team rendering for Fallen Kingdoms. */
public final class FallenKingdomsHud implements UiReloadParticipant {
    private final TropicubeFallenKingdoms plugin;
    private final GameSession session;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private final Map<UUID, HeartBar> bars = new HashMap<>();
    private final Map<UUID, ActionBarAlert> actionBarAlerts = new HashMap<>();
    private final Map<UUID, HeartAttackAlert> heartAttackAlerts = new HashMap<>();
    private final Map<UUID, Long> lastHeartSounds = new HashMap<>();
    private volatile ScoreboardTemplate scoreboard;
    private volatile TablistTemplate tablist;

    public FallenKingdomsHud(TropicubeFallenKingdoms plugin, GameSession session) {
        this.plugin = plugin;
        this.session = session;
        prepareReload().run();
        Bukkit.getServicesManager().register(UiReloadParticipant.class, this, plugin, ServicePriority.Normal);
    }

    public void updateAll(int elapsed) {
        for (Player player : Bukkit.getOnlinePlayers()) update(player, elapsed);
    }

    public void update(Player player, int elapsed) {
        TropicubeCore core = core();
        if (core == null) return;
        var language = core.getLanguageManager();
        KingdomId kingdom = session.kingdomOf(player);
        PlaceholderValues values = values(player, elapsed, language, kingdom);
        Scoreboard board = boards.computeIfAbsent(player.getUniqueId(), ignored ->
                Bukkit.getScoreboardManager().getNewScoreboard());
        var old = board.getObjective("fallenkingdoms");
        if (old != null) old.unregister();
        var objective = board.registerNewObjective("fallenkingdoms", Criteria.DUMMY,
                language.getComponent(player.getUniqueId(), scoreboard.titleKey()));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        String variant = switch (session.state()) {
            case WAITING -> "waiting";
            case COUNTDOWN -> "countdown";
            case ENDING, ENDED -> "ending";
            default -> "active";
        };
        int score = 15;
        for (var line : scoreboard.lines(variant)) {
            if (!line.blank() && !showLine(line.key(), session.activeKingdoms())) continue;
            var entry = objective.getScore("fk_" + score);
            entry.setScore(score--);
            entry.customName(line.blank() ? Component.empty()
                    : language.getComponent(player.getUniqueId(), line.key(), values));
        }
        setupTeamBoards(board);
        player.setScoreboard(board);
        var tabVariant = tablist.variant(variant);
        player.sendPlayerListHeaderAndFooter(
                language.getComponent(player.getUniqueId(), tabVariant.headerKey(), values),
                language.getComponent(player.getUniqueId(), tabVariant.footerKey(), values));
        applyPlayerListName(player);
        updateActionBar(player, language, kingdom);
    }

    private PlaceholderValues values(Player player, int elapsed,
                                      fr.tropicube.core.managers.LanguageManager language, KingdomId kingdom) {
        Map<KingdomId, Integer> survivors = session.survivorCounts();
        boolean waiting = session.state() == GameState.WAITING || session.state() == GameState.COUNTDOWN;
        KingdomId displayedTeam = kingdom == null && waiting ? session.preferredKingdom(player.getUniqueId()) : kingdom;
        String teamKey = displayedTeam == null ? (waiting ? "fk.choice-none" : "fk.team-spectator")
                : "fk.team-" + displayedTeam.name().toLowerCase(Locale.ROOT);
        String mapKey = waiting ? session.waitingMapDisplayNameKey(player.getUniqueId()) : session.mapDisplayNameKey();
        return PlaceholderValues.builder()
                .putComponent("phase", language.getComponent(player.getUniqueId(),
                        "fk.phase-" + session.state().name().toLowerCase(Locale.ROOT)))
                .putComponent("next_phase", language.getComponent(player.getUniqueId(),
                        "fk.phase-" + session.nextPhase().name().toLowerCase(Locale.ROOT)))
                .putComponent("team", language.getComponent(player.getUniqueId(), teamKey))
                .putComponent("kit", language.getComponent(player.getUniqueId(), "fk.kit-" + session.preferredKit(player.getUniqueId())))
                .put("time", session.remainingTime(elapsed)).put("day", GameDay.at(elapsed))
                .putComponent("map", language.getComponent(player.getUniqueId(), mapKey))
                .put("players", session.participantCount())
                .put("max_players", session.maximumPlayerCapacity())
                .put("blue_players", survivors.getOrDefault(KingdomId.BLUE, 0))
                .put("red_players", survivors.getOrDefault(KingdomId.RED, 0))
                .put("green_players", survivors.getOrDefault(KingdomId.GREEN, 0))
                .put("yellow_players", survivors.getOrDefault(KingdomId.YELLOW, 0))
                .put("orange_players", survivors.getOrDefault(KingdomId.ORANGE, 0)).build();
    }

    private void setupTeamBoards(Scoreboard board) {
        for (Team existing : Set.copyOf(board.getTeams())) existing.unregister();
        Map<KingdomId, Team> kingdomTeams = new EnumMap<>(KingdomId.class);
        for (KingdomId kingdom : KingdomId.values()) {
            Team team = board.registerNewTeam("fk_" + kingdom.name().toLowerCase(Locale.ROOT));
            configureTeam(team, color(kingdom));
            kingdomTeams.put(kingdom, team);
        }
        Team spectators = board.registerNewTeam("fk_spectator");
        configureTeam(spectators, NamedTextColor.GRAY);
        for (Player other : Bukkit.getOnlinePlayers()) {
            KingdomId kingdom = session.kingdomOf(other);
            PlayerLifeState life = session.playerLifeState(other.getUniqueId());
            Team target = kingdom == null || life == PlayerLifeState.ELIMINATED || life == PlayerLifeState.SPECTATOR
                    ? spectators : kingdomTeams.get(kingdom);
            target.addEntry(profileName(other));
        }
    }

    private static void configureTeam(Team team, NamedTextColor color) {
        team.color(color);
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(true);
        team.setAllowFriendlyFire(false);
    }

    public void showEnemyHeart(Player player, Heart heart) {
        TropicubeCore core = core();
        if (core == null) return;
        removeBar(player);
        String teamKey = "fk.team-" + heart.owner().name().toLowerCase(Locale.ROOT);
        PlaceholderValues values = PlaceholderValues.builder()
                .putComponent("team", core.getLanguageManager().getComponent(player.getUniqueId(), teamKey))
                .put("heart_health", (int) Math.ceil(heart.health())).build();
        float progress = (float) Math.max(0, Math.min(1, heart.health() / heart.maximumHealth()));
        BossBar bar = BossBar.bossBar(core.getLanguageManager().getComponent(player.getUniqueId(),
                "fk.bossbar-enemy-heart", values), progress, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            HeartBar current = bars.get(player.getUniqueId());
            if (current != null && current.bar() == bar) removeBar(player);
        }, 100L);
        bars.put(player.getUniqueId(), new HeartBar(bar, task));
        player.showBossBar(bar);
    }

    public void showActionBarAlert(Player player, String key, PlaceholderValues values, long durationTicks) {
        long now = System.currentTimeMillis();
        AlertPriority priority = key.equals("fk.respawn-countdown") ? AlertPriority.RESPAWN : AlertPriority.TERRITORY;
        ActionBarAlert current = actionBarAlerts.get(player.getUniqueId());
        if (current != null && current.expiresAtMillis() > now && current.priority().ordinal() > priority.ordinal()) return;
        actionBarAlerts.put(player.getUniqueId(), new ActionBarAlert(key, values,
                now + durationTicks * 50L, priority));
    }

    public void showHeartAttack(Player player, Heart heart, HeartAlertSettings settings) {
        long now = System.currentTimeMillis();
        heartAttackAlerts.put(player.getUniqueId(), new HeartAttackAlert(
                now + settings.durationTicks() * 50L, settings.flashIntervalTicks()));
        long lastSound = lastHeartSounds.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastSound >= settings.soundCooldownTicks() * 50L) {
            player.playSound(player.getLocation(), settings.sound(), settings.volume(), settings.pitch());
            lastHeartSounds.put(player.getUniqueId(), now);
        }
    }

    public void remove(Player player) {
        removeBar(player);
        actionBarAlerts.remove(player.getUniqueId());
        heartAttackAlerts.remove(player.getUniqueId());
        lastHeartSounds.remove(player.getUniqueId());
        boards.remove(player.getUniqueId());
        player.sendActionBar(Component.empty());
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
    }

    public void clear() {
        Bukkit.getServicesManager().unregister(UiReloadParticipant.class, this);
        for (Player player : Bukkit.getOnlinePlayers()) {
            remove(player);
            player.playerListName(Component.text(visibleName(player)));
        }
        boards.clear();
    }

    private void removeBar(Player player) {
        HeartBar previous = bars.remove(player.getUniqueId());
        if (previous != null) {
            previous.task().cancel();
            player.hideBossBar(previous.bar());
        }
    }

    private void updateActionBar(Player player, fr.tropicube.core.managers.LanguageManager language, KingdomId kingdom) {
        long now = System.currentTimeMillis();
        ActionBarAlert alert = actionBarAlerts.get(player.getUniqueId());
        if (alert != null && alert.expiresAtMillis() > now) {
            player.sendActionBar(language.getComponent(player.getUniqueId(), alert.key(), alert.values()));
            return;
        }
        if (alert != null) actionBarAlerts.remove(player.getUniqueId());
        Heart heart = kingdom == null ? null : session.heart(kingdom);
        HeartAttackAlert attack = heartAttackAlerts.get(player.getUniqueId());
        if (attack != null && attack.expiresAtMillis() > now && heart != null) {
            boolean red = Bukkit.getCurrentTick() / attack.flashIntervalTicks() % 2 == 0;
            PlaceholderValues values = heartValues(heart);
            player.sendActionBar(language.getComponent(player.getUniqueId(),
                    red ? "fk.actionbar-own-heart-alert-red" : "fk.actionbar-own-heart-alert-white",
                    values));
            return;
        }
        if (attack != null) heartAttackAlerts.remove(player.getUniqueId());
        if (!showsOwnHeart(session.state(), kingdom, heart)) {
            player.sendActionBar(Component.empty());
            return;
        }
        PlaceholderValues values = heartValues(heart);
        player.sendActionBar(language.getComponent(player.getUniqueId(), "fk.actionbar-own-heart", values));
    }

    private static PlaceholderValues heartValues(Heart heart) {
        return PlaceholderValues.builder().put("heart_health", (int) Math.ceil(heart.health()))
                .put("heart_max_health", (int) Math.ceil(heart.maximumHealth())).build();
    }

    static boolean showsOwnHeart(GameState state, KingdomId team, Heart heart) {
        return state.active() && team != null && heart != null;
    }

    private void applyPlayerListName(Player player) {
        boolean waiting = session.state() == GameState.WAITING || session.state() == GameState.COUNTDOWN;
        PlayerLifeState life = session.playerLifeState(player.getUniqueId());
        KingdomId team = waiting ? session.preferredKingdom(player.getUniqueId())
                : (life == PlayerLifeState.ELIMINATED || life == PlayerLifeState.SPECTATOR
                ? null : session.kingdomOf(player));
        player.playerListName(Component.text(visibleName(player), color(team)));
    }

    private static String profileName(Player player) {
        String value = player.getPlayerProfile().getName();
        return value == null || value.isBlank() ? player.getName() : value;
    }

    private static String visibleName(Player player) {
        String value = PlainTextComponentSerializer.plainText().serialize(player.displayName());
        return value.isBlank() ? player.getName() : value;
    }

    private static NamedTextColor color(KingdomId team) {
        if (team == null) return NamedTextColor.GRAY;
        return switch (team) {
            case BLUE -> NamedTextColor.BLUE;
            case RED -> NamedTextColor.RED;
            case GREEN -> NamedTextColor.GREEN;
            case YELLOW -> NamedTextColor.YELLOW;
            case ORANGE -> NamedTextColor.GOLD;
        };
    }

    static boolean showLine(String key, Set<KingdomId> active) {
        return switch (key) {
            case "fk.sb-blue" -> active.contains(KingdomId.BLUE);
            case "fk.sb-red" -> active.contains(KingdomId.RED);
            case "fk.sb-green" -> active.contains(KingdomId.GREEN);
            case "fk.sb-yellow" -> active.contains(KingdomId.YELLOW);
            case "fk.sb-orange" -> active.contains(KingdomId.ORANGE);
            default -> true;
        };
    }

    @Override public Runnable prepareReload() {
        ScoreboardTemplate next = ScoreboardTemplate.load(plugin, "scoreboards.yml", "fallenkingdoms");
        TablistTemplate nextTab = TablistTemplate.load(plugin, "tablists.yml", "fallenkingdoms");
        return () -> { scoreboard = next; tablist = nextTab; };
    }

    @Override public void refreshViewers() { updateAll(session.elapsedSeconds()); }

    private TropicubeCore core() {
        return (TropicubeCore) Bukkit.getPluginManager().getPlugin("TropicubeCore");
    }

    private record HeartBar(BossBar bar, BukkitTask task) { }
    private enum AlertPriority { TERRITORY, RESPAWN }
    private record ActionBarAlert(String key, PlaceholderValues values, long expiresAtMillis,
                                  AlertPriority priority) { }
    private record HeartAttackAlert(long expiresAtMillis, int flashIntervalTicks) { }
}
