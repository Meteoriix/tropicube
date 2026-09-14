package fr.tropicube.fallenkingdoms.game;

import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import fr.tropicube.fallenkingdoms.config.FallenKingdomsSettings;
import fr.tropicube.fallenkingdoms.map.BaseDefinition;
import fr.tropicube.fallenkingdoms.map.MapDefinition;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.language.PlaceholderValues;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;

/** Paper adapter for one session on any validated map definition. */
public final class GameSession {
    private final UUID sessionId = UUID.randomUUID();
    private final TropicubeFallenKingdoms plugin;
    private final GameStateMachine state;
    private final PhaseTimeline timeline;
    private final FallenKingdomsSettings settings;
    private final MapDefinition map;
    private final ProtectionRules protections = new ProtectionRules();
    private final TaskRegistry tasks = new TaskRegistry();
    private final KitCatalog kits;
    private final RuinService ruins;
    private final Map<UUID, PlayerSession> players = new HashMap<>();
    private final Map<UUID, KingdomId> kingdomPreferences = new HashMap<>();
    private final Map<UUID, String> kitPreferences = new HashMap<>();
    private final Map<KingdomId, Heart> hearts = new EnumMap<>(KingdomId.class);
    private final Map<UUID, EnderCrystal> crystals = new HashMap<>();
    private final Map<UUID, KingdomId> crystalOwners = new HashMap<>();
    private final Set<KingdomId> eliminatedKingdoms = new HashSet<>();
    private BukkitTask countdownTask;
    private long startedAt;
    private GameResult result;

    public GameSession(TropicubeFallenKingdoms plugin, GameStateMachine state, MapDefinition map,
                       FallenKingdomsSettings settings) {
        this.plugin = plugin;
        this.state = state;
        this.settings = settings;
        this.timeline = settings.timeline();
        this.map = map;
        this.kits = KitCatalog.load(plugin.getConfig());
        this.ruins = new RuinService(plugin, tasks, settings);
    }

    public boolean startCountdown() {
        if (!validRosterSize()) return false;
        int kingdoms = KingdomAllocator.kingdomCount(Bukkit.getOnlinePlayers().size());
        if (!map.layouts().containsKey(kingdoms)) return false;
        if (!state.transitionTo(GameState.COUNTDOWN)) return false;
        countdownTask = tasks.register(Bukkit.getScheduler().runTaskLater(plugin, this::start,
                settings.countdownSeconds() * 20L));
        plugin.updateInstanceStatus(ServerInstance.Status.GAME_STARTING);
        return true;
    }

    public boolean cancelCountdown() {
        if (state.state() != GameState.COUNTDOWN) return false;
        if (countdownTask != null) countdownTask.cancel();
        boolean changed = state.transitionTo(GameState.WAITING);
        if (changed) plugin.updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
        return changed;
    }

    public void reevaluateCountdown() {
        if (state.state() == GameState.COUNTDOWN && !validRosterSize()) cancelCountdown();
    }

    private boolean validRosterSize() {
        int count = Bukkit.getOnlinePlayers().size();
        return count >= settings.minPlayersPerKingdom() * 2
                && count <= settings.maxPlayersPerKingdom() * settings.maxKingdoms();
    }

    private void start() {
        countdownTask = null;
        if (!validRosterSize()) {
            state.transitionTo(GameState.WAITING);
            plugin.updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
            return;
        }
        var roster = Bukkit.getOnlinePlayers().stream()
                .map(player -> new KingdomAllocator.PlayerPreference(player.getUniqueId(), kingdomPreferences.get(player.getUniqueId()))).toList();
        int kingdomCount = KingdomAllocator.kingdomCount(roster.size());
        var layout = map.layouts().get(kingdomCount);
        if (layout == null) {
            plugin.getLogger().severe("Carte " + map.id() + ": aucun agencement pour " + kingdomCount + " royaumes");
            state.transitionTo(GameState.WAITING);
            plugin.updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
            return;
        }
        if (!state.transitionTo(GameState.PREPARATION)) {
            state.transitionTo(GameState.WAITING);
            plugin.updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
            return;
        }
        Map<UUID, KingdomId> allocations = new KingdomAllocator().allocate(roster, layout);
        allocations.forEach((id, kingdom) -> {
            PlayerSession runtime = new PlayerSession(id, kingdom);
            runtime.kitId(kitPreferences.getOrDefault(id, kits.defaultKit()));
            players.put(id, runtime);
        });
        Set<KingdomId> activeKingdoms = new HashSet<>(allocations.values());
        activeKingdoms.forEach(this::spawnHeart);
        World world = world();
        WorldBorder border = world.getWorldBorder();
        border.setCenter(map.borderX(), map.borderZ());
        border.setSize(map.initialBorderSize());
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession runtime = players.get(player.getUniqueId());
            if (runtime != null) prepareParticipant(player, runtime.kingdom());
        }
        startedAt = System.currentTimeMillis();
        plugin.updateInstanceStatus(ServerInstance.Status.GAME_PLAYING);
        tasks.register(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L));
    }

    private void prepareParticipant(Player player, KingdomId kingdom) {
        player.getInventory().clear();
        player.setHealth(player.getMaxHealth());
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);
        kits.give(player, players.get(player.getUniqueId()).kitId());
        player.teleport(base(kingdom).spawn().in(world()));
    }

    private void tick() {
        if (!state.state().active()) return;
        int seconds = (int) ((System.currentTimeMillis() - startedAt) / 1000L);
        updateHud(seconds);
        advancePhases(seconds);
        if (seconds >= timeline.forceEndAt()) finishAtTimeLimit();
    }

    private void advancePhases(int seconds) {
        if (state.state() == GameState.PREPARATION && seconds >= timeline.pvpAt()) state.transitionTo(GameState.PVP);
        if (state.state() == GameState.PVP && seconds >= timeline.assaultAt() && state.transitionTo(GameState.ASSAULT))
            hearts.values().forEach(Heart::makeVulnerable);
        if (state.state() == GameState.ASSAULT && seconds >= timeline.suddenDeathAt()
                && state.transitionTo(GameState.SUDDEN_DEATH)) beginSuddenDeath(seconds);
    }

    private void beginSuddenDeath(int elapsedSeconds) {
        hearts.values().forEach(Heart::destroy);
        crystals.values().forEach(EnderCrystal::remove);
        players.values().stream().filter(player -> player.state() == PlayerLifeState.RESPAWNING)
                .forEach(player -> player.state(PlayerLifeState.ELIMINATED));
        players.values().stream().filter(player -> player.state() == PlayerLifeState.ACTIVE)
                .forEach(player -> player.state(PlayerLifeState.LAST_LIFE));
        int remaining = Math.max(1, timeline.forceEndAt() - elapsedSeconds);
        world().getWorldBorder().changeSize(settings.finalBorderSize(), remaining * 20L);
        checkWinner();
    }

    private void spawnHeart(KingdomId kingdom) {
        Location location = base(kingdom).heart().in(world()).add(.5, 0, .5);
        EnderCrystal crystal = world().spawn(location, EnderCrystal.class, entity -> entity.setShowingBottom(false));
        hearts.put(kingdom, new Heart(kingdom, settings.heartHealth()));
        crystals.put(crystal.getUniqueId(), crystal);
        crystalOwners.put(crystal.getUniqueId(), kingdom);
    }

    public void damageHeart(EntityDamageByEntityEvent event) {
        UUID crystalId = event.getEntity().getUniqueId();
        EnderCrystal crystal = crystals.get(crystalId);
        if (crystal == null) return;
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player attacker)) return;
        PlayerSession attackingPlayer = players.get(attacker.getUniqueId());
        Heart heart = hearts.get(crystalOwners.get(crystalId));
        if (attackingPlayer == null || heart == null || !protections.allowsHeartDamage(state.state())) return;
        HeartState before = heart.state();
        heart.damage(attackingPlayer.kingdom(), event.getFinalDamage(), false);
        if (before != HeartState.DESTROYED && heart.state() == HeartState.DESTROYED) {
            attackingPlayer.recordObjective();
            crystal.remove();
            markLastLives(heart.owner());
        }
    }

    private void markLastLives(KingdomId kingdom) {
        players.values().stream().filter(player -> player.kingdom() == kingdom).forEach(player -> {
            if (player.state() == PlayerLifeState.RESPAWNING || player.state() == PlayerLifeState.OFFLINE) {
                player.state(PlayerLifeState.ELIMINATED);
            } else if (player.state() == PlayerLifeState.ACTIVE) {
                player.state(PlayerLifeState.LAST_LIFE);
            }
        });
        checkWinner();
    }

    public void playerDied(Player player, Player killer) {
        PlayerSession runtime = players.get(player.getUniqueId());
        if (!state.state().active() || runtime == null || !runtime.surviving()) return;
        runtime.recordDeath();
        PlayerSession killerRuntime = killer == null ? null : players.get(killer.getUniqueId());
        if (killerRuntime != null && killerRuntime.kingdom() != runtime.kingdom()) killerRuntime.recordElimination();
        Heart heart = hearts.get(runtime.kingdom());
        boolean canRespawn = state.state() != GameState.SUDDEN_DEATH && heart != null && heart.state() != HeartState.DESTROYED;
        runtime.state(canRespawn ? PlayerLifeState.RESPAWNING : PlayerLifeState.ELIMINATED);
        checkWinner();
    }

    public void respawn(Player player) {
        PlayerSession runtime = players.get(player.getUniqueId());
        player.teleport(map.spectator().in(world()));
        player.setGameMode(GameMode.SPECTATOR);
        if (runtime == null || runtime.state() != PlayerLifeState.RESPAWNING) return;
        tasks.register(Bukkit.getScheduler().runTaskLater(plugin, () -> completeRespawn(player.getUniqueId()),
                settings.respawnDelaySeconds() * 20L));
    }

    private void completeRespawn(UUID playerId) {
        PlayerSession runtime = players.get(playerId);
        if (runtime == null || runtime.state() != PlayerLifeState.RESPAWNING || !state.state().active()) return;
        Heart heart = hearts.get(runtime.kingdom());
        Player player = Bukkit.getPlayer(playerId);
        if (heart == null || heart.state() == HeartState.DESTROYED || player == null) {
            runtime.state(player == null ? PlayerLifeState.OFFLINE : PlayerLifeState.ELIMINATED);
            checkWinner();
            return;
        }
        runtime.state(PlayerLifeState.ACTIVE);
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(base(runtime.kingdom()).spawn().in(world()));
    }

    public void join(Player player) {
        if (state.state() == GameState.WAITING || state.state() == GameState.COUNTDOWN) {
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(map.lobby().in(world()));
            if (state.state() == GameState.WAITING && settings.autoStart() && validRosterSize()) startCountdown();
            return;
        }
        PlayerSession runtime = players.get(player.getUniqueId());
        if (runtime == null || runtime.state() == PlayerLifeState.ELIMINATED) {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(map.spectator().in(world()));
        } else if (runtime.state() == PlayerLifeState.OFFLINE) {
            runtime.state(PlayerLifeState.RESPAWNING);
            respawn(player);
        }
    }

    public void quit(Player player) {
        if (state.state() == GameState.COUNTDOWN && Bukkit.getOnlinePlayers().size() - 1 < settings.minPlayersPerKingdom() * 2)
            cancelCountdown();
        PlayerSession runtime = players.get(player.getUniqueId());
        if (!state.state().active() || runtime == null || !runtime.surviving()) return;
        for (var item : player.getInventory().getContents()) if (item != null && !item.getType().isAir())
            player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
        player.getInventory().clear();
        runtime.recordDeath();
        Heart heart = hearts.get(runtime.kingdom());
        runtime.state(heart != null && heart.state() != HeartState.DESTROYED
                && state.state() != GameState.SUDDEN_DEATH ? PlayerLifeState.OFFLINE : PlayerLifeState.ELIMINATED);
        checkWinner();
    }

    public boolean allowsPvp(Player attacker, Player victim) {
        PlayerSession left = players.get(attacker.getUniqueId());
        PlayerSession right = players.get(victim.getUniqueId());
        if (left == null || right == null || !left.surviving() || !right.surviving()) return false;
        return protections.allowsPvp(state.state(), territoryAt(victim.getLocation(), left.kingdom()), left.kingdom() == right.kingdom());
    }

    public ProtectionRules.Territory territoryAt(Location location, KingdomId perspective) {
        if (!map.playableRegion().contains(location)) return ProtectionRules.Territory.OUTSIDE;
        for (var entry : map.bases().entrySet()) if (entry.getValue().region().contains(location))
            return entry.getKey() == perspective ? ProtectionRules.Territory.OWN_BASE : ProtectionRules.Territory.ENEMY_BASE;
        return ProtectionRules.Territory.COMMON;
    }

    public boolean mayBuild(Player player, Location location) {
        return mayChangeBlock(player, location, null, false);
    }

    public boolean mayChangeBlock(Player player, Location location, Material material, boolean placing) {
        PlayerSession runtime = players.get(player.getUniqueId());
        if (!state.state().active() || runtime == null || !runtime.surviving()) return false;
        ProtectionRules.Territory territory = territoryAt(location, runtime.kingdom());
        if (!protections.allowsManualBuild(territory, territory == ProtectionRules.Territory.OWN_BASE)) return false;
        if (!placing) return territory == ProtectionRules.Territory.OWN_BASE || territory == ProtectionRules.Territory.COMMON;
        if (territory == ProtectionRules.Territory.OWN_BASE)
            return material != null && !settings.forbiddenBaseMaterials().contains(material);
        return territory == ProtectionRules.Territory.COMMON && material != null
                && settings.commonPlacementMaterials().contains(material);
    }

    public boolean mayEnter(Player player, Location destination) {
        PlayerSession runtime = players.get(player.getUniqueId());
        if (!map.playableRegion().contains(destination)) return false;
        if (runtime == null || runtime.state() == PlayerLifeState.SPECTATOR || runtime.state() == PlayerLifeState.ELIMINATED)
            return true;
        ProtectionRules.Territory territory = territoryAt(destination, runtime.kingdom());
        return territory != ProtectionRules.Territory.ENEMY_BASE || protections.allowsEnemyBaseEntry(state.state());
    }

    public boolean mayExplosionChange(Location location) {
        if (!state.state().active() || !map.playableRegion().contains(location)) return false;
        boolean inBase = map.bases().values().stream().anyMatch(base -> base.region().contains(location));
        return !inBase || state.state() == GameState.ASSAULT || state.state() == GameState.SUDDEN_DEATH;
    }

    private void checkWinner() {
        if (!state.state().active()) return;
        Map<KingdomId, Integer> survivors = survivors();
        survivors.forEach((kingdom, count) -> {
            if (count == 0 && eliminatedKingdoms.add(kingdom))
                ruins.ruin(world(), base(kingdom), sessionId.getMostSignificantBits() ^ kingdom.ordinal());
        });
        long alive = survivors.values().stream().filter(value -> value > 0).count();
        if (alive <= 1) finish(new VictoryRules().lastKingdom(sessionId, survivors));
    }

    private void finishAtTimeLimit() { finish(new VictoryRules().timeLimit(sessionId, survivors())); }

    public void abort() {
        finish(new GameResult(sessionId, EndCause.ADMIN_ABORT, Set.of(), survivors(), Instant.now()));
    }

    private void finish(GameResult result) {
        if (this.result != null || !state.transitionTo(GameState.ENDING)) return;
        this.result = result;
        plugin.updateInstanceStatus(ServerInstance.Status.GAME_ENDING);
        announceResult(result);
        tasks.cancelAll();
        crystals.values().forEach(EnderCrystal::remove);
        tasks.register(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            state.transitionTo(GameState.ENDED);
            plugin.finishInstance(result, players.values());
        }, settings.resultDisplaySeconds() * 20L));
    }

    public void shutdown() { tasks.cancelAll(); crystals.values().forEach(EnderCrystal::remove); }
    public GameState state() { return state.state(); }
    public String mapId() { return map.id(); }
    public String mapDisplayNameKey() { return map.displayNameKey(); }
    public int participantCount() { return state.state() == GameState.WAITING || state.state() == GameState.COUNTDOWN
            ? Bukkit.getOnlinePlayers().size() : players.size(); }
    public GameResult result() { return result; }
    public Map<String, KitDefinition> availableKits() { return kits.definitions(); }
    public Set<KingdomId> availableKingdoms() { return map.bases().keySet(); }
    public boolean chooseKit(UUID playerId, String kitId) {
        if ((state.state() != GameState.WAITING && state.state() != GameState.COUNTDOWN) || !kits.definitions().containsKey(kitId)) return false;
        kitPreferences.put(playerId, kitId); return true;
    }
    public boolean chooseKingdom(UUID playerId, KingdomId kingdom) {
        if ((state.state() != GameState.WAITING && state.state() != GameState.COUNTDOWN) || !map.bases().containsKey(kingdom)) return false;
        kingdomPreferences.put(playerId, kingdom); return true;
    }
    public KingdomId kingdomOf(Player player) { PlayerSession runtime = players.get(player.getUniqueId()); return runtime == null ? null : runtime.kingdom(); }
    public boolean isParticipant(Player player) { return players.containsKey(player.getUniqueId()); }

    private Map<KingdomId, Integer> survivors() {
        Map<KingdomId, Integer> result = new EnumMap<>(KingdomId.class);
        hearts.keySet().forEach(kingdom -> result.put(kingdom, 0));
        players.values().stream().filter(PlayerSession::surviving)
                .forEach(player -> result.merge(player.kingdom(), 1, Integer::sum));
        return result;
    }

    private void updateHud(int elapsedSeconds) {
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return;
        int nextAt = switch (state.state()) {
            case PREPARATION -> timeline.pvpAt();
            case PVP -> timeline.assaultAt();
            case ASSAULT -> timeline.suddenDeathAt();
            case SUDDEN_DEATH -> timeline.forceEndAt();
            default -> elapsedSeconds;
        };
        String remaining = formatDuration(Math.max(0, nextAt - elapsedSeconds));
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerSession runtime = players.get(player.getUniqueId());
            String teamKey = runtime == null ? "fk.team-spectator" : "fk.team-" + runtime.kingdom().name().toLowerCase(java.util.Locale.ROOT);
            var language = core.getLanguageManager();
            PlaceholderValues values = PlaceholderValues.builder()
                    .putComponent("phase", language.getComponent(player.getUniqueId(), "fk.phase-" + state.state().name().toLowerCase(java.util.Locale.ROOT)))
                    .putComponent("team", language.getComponent(player.getUniqueId(), teamKey))
                    .put("time", remaining)
                    .build();
            player.sendActionBar(language.getComponent(player.getUniqueId(), "fk.hud", values));
        }
    }

    private void announceResult(GameResult result) {
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String key = result.cause() == EndCause.ADMIN_ABORT ? "fk.result-abort"
                    : result.cause() == EndCause.DRAW ? "fk.result-draw" : "fk.result-win";
            var winners = result.winners().stream().map(kingdom -> core.getLanguageManager().getComponent(player.getUniqueId(),
                    "fk.team-" + kingdom.name().toLowerCase(java.util.Locale.ROOT))).toList();
            Component joined = winners.isEmpty() ? Component.empty()
                    : Component.join(JoinConfiguration.separator(Component.text(", ")), winners);
            player.sendMessage(core.getLanguageManager().getComponent(player.getUniqueId(), key,
                    PlaceholderValues.builder().putComponent("winners", joined).build()));
        }
    }

    private static String formatDuration(int seconds) {
        return "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    private BaseDefinition base(KingdomId kingdom) {
        BaseDefinition base = map.bases().get(kingdom);
        if (base == null) throw new IllegalStateException("Base absente pour " + kingdom + " sur " + map.id());
        return base;
    }

    private World world() {
        World world = Bukkit.getWorld(map.world());
        if (world == null) throw new IllegalStateException("Monde introuvable: " + map.world());
        return world;
    }
}
