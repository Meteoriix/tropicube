package fr.tropicube.fallenkingdoms.game;

import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import fr.tropicube.fallenkingdoms.config.FallenKingdomsSettings;
import fr.tropicube.fallenkingdoms.map.BaseDefinition;
import fr.tropicube.fallenkingdoms.map.MapDefinition;
import fr.tropicube.fallenkingdoms.map.MapCatalog;
import fr.tropicube.fallenkingdoms.persistence.FallenKingdomsPreferenceService;
import fr.tropicube.fallenkingdoms.event.*;
import fr.tropicube.fallenkingdoms.hud.FallenKingdomsHud;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.attribute.Attribute;
import org.bukkit.util.Vector;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import fr.tropicube.docker.model.ServerInstance;
import fr.tropicube.core.TropicubeCore;
import fr.tropicube.language.PlaceholderValues;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.block.Block;
import org.bukkit.block.Container;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.nio.file.Files;
import java.io.IOException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.title.Title;
import java.time.Duration;

/** Paper adapter for one session on any validated map definition. */
public final class GameSession {
    private final UUID sessionId = UUID.randomUUID();
    private final TropicubeFallenKingdoms plugin;
    private final GameStateMachine state;
    private final PhaseTimeline timeline;
    private final FallenKingdomsSettings settings;
    private final MapCatalog maps;
    private MapDefinition map;
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
    private final Set<KingdomId> ruinedKingdoms = new HashSet<>();
    private final Map<UUID,BukkitTask> respawnTasks=new HashMap<>();
    private final MapVote mapVote = new MapVote();
    private final FallenKingdomsPreferenceService preferences;
    private final FallenKingdomsHud hud;
    private BukkitTask countdownTask;
    private int countdownRemaining;
    private long startedAt;
    private GameResult result;
    private boolean winnerCheckScheduled;

    public GameSession(TropicubeFallenKingdoms plugin, GameStateMachine state, MapCatalog maps, String defaultMap,
                       FallenKingdomsSettings settings,FileConfiguration config) {
        this.plugin = plugin;
        this.state = state;
        this.settings = settings;
        this.timeline = settings.timeline();
        this.maps = maps;
        this.map = maps.select(defaultMap);
        this.kits = KitCatalog.load(config);
        this.ruins = new RuinService(plugin, tasks, settings);
        TropicubeCore core = (TropicubeCore) Bukkit.getPluginManager().getPlugin("TropicubeCore");
        this.preferences = new FallenKingdomsPreferenceService(Objects.requireNonNull(core).getDatabaseManager());
        this.hud = new FallenKingdomsHud(plugin, this);
    }

    public boolean startCountdown() {
        if (!validRosterSize()) return false;
        int kingdoms = configuredKingdomCount(Bukkit.getOnlinePlayers().size());
        if (!map.layouts().containsKey(kingdoms)) return false;
        if (!transition(GameState.COUNTDOWN)) return false;
        countdownRemaining = settings.countdownSeconds();
        countdownTask = tasks.register(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!validRosterSize()) { cancelCountdown(); return; }
            hud.updateAll(0);
            if (countdownRemaining-- <= 0) start();
        }, 0L, 20L));
        plugin.updateInstanceStatus(ServerInstance.Status.GAME_STARTING);
        return true;
    }

    public boolean cancelCountdown() {
        if (state.state() != GameState.COUNTDOWN) return false;
        if (countdownTask != null) countdownTask.cancel();
        boolean changed = transition(GameState.WAITING);
        if (changed) plugin.updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
        return changed;
    }

    public void reevaluateCountdown() {
        if (state.state() == GameState.COUNTDOWN && !validRosterSize()) cancelCountdown();
    }

    private boolean validRosterSize() {
        int count = Bukkit.getOnlinePlayers().size();
        try { configuredKingdomCount(count); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    private int configuredKingdomCount(int players) {
        return KingdomAllocator.kingdomCount(players, settings.maxPlayersPerKingdom(), settings.maxKingdoms());
    }

    private void start() {
        if (countdownTask != null) countdownTask.cancel();
        countdownTask = null;
        if (!validRosterSize()) {
            transition(GameState.WAITING);
            plugin.updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
            return;
        }
        map = maps.select(mapVote.winner(map.id()));
        var roster = Bukkit.getOnlinePlayers().stream()
                .map(player -> new KingdomAllocator.PlayerPreference(player.getUniqueId(), kingdomPreferences.get(player.getUniqueId()))).toList();
        int kingdomCount = configuredKingdomCount(roster.size());
        var layout = map.layouts().get(kingdomCount);
        if (layout == null) {
            plugin.getLogger().severe("Carte " + map.id() + ": aucun agencement pour " + kingdomCount + " royaumes");
            transition(GameState.WAITING);
            plugin.updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
            return;
        }
        if (!transition(GameState.PREPARATION)) {
            transition(GameState.WAITING);
            plugin.updateInstanceStatus(ServerInstance.Status.GAME_WAITING);
            return;
        }
        Map<UUID, KingdomId> allocations = new KingdomAllocator().allocate(roster, layout);
        try { Files.writeString(plugin.sessionMarker(),sessionId.toString()); }
        catch(IOException failure){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Impossible de verrouiller le monde FK",failure);abort();return;}
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
        player.setHealth(Objects.requireNonNull(player.getAttribute(Attribute.MAX_HEALTH),
                "player max health attribute").getValue());
        player.setFoodLevel(20);
        player.setGameMode(GameMode.SURVIVAL);
        var attackSpeed=player.getAttribute(Attribute.ATTACK_SPEED);
        if(attackSpeed!=null&&settings.combatProfile()==CombatProfile.LEGACY_1_8)attackSpeed.setBaseValue(1024.0);
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
        if (state.state() == GameState.PREPARATION && seconds >= timeline.pvpAt()) transition(GameState.PVP);
        if (state.state() == GameState.PVP && seconds >= timeline.assaultAt() && transition(GameState.ASSAULT))
            hearts.values().forEach(Heart::makeVulnerable);
        if (state.state() == GameState.ASSAULT && seconds >= timeline.suddenDeathAt()
                && transition(GameState.SUDDEN_DEATH)) beginSuddenDeath(seconds);
    }

    private void beginSuddenDeath(int elapsedSeconds) {
        hearts.forEach((kingdom, heart) -> {
            if (heart.state() != HeartState.DESTROYED) {
                heart.destroy();
                Bukkit.getPluginManager().callEvent(new KingdomHeartDestroyedEvent(sessionId, kingdom,
                        KingdomHeartDestroyedEvent.Cause.FORCED_SUDDEN_DEATH));
            }
        });
        crystals.values().forEach(EnderCrystal::remove);
        players.values().stream().filter(player -> player.state() == PlayerLifeState.RESPAWNING)
                .forEach(player -> player.state(PlayerLifeState.ELIMINATED));
        players.values().stream().filter(player -> player.state() == PlayerLifeState.ACTIVE)
                .forEach(player -> player.state(PlayerLifeState.LAST_LIFE));
        int remaining = Math.max(1, timeline.forceEndAt() - elapsedSeconds);
        world().getWorldBorder().changeSize(settings.finalBorderSize(), remaining * 20L);
        scheduleWinnerCheck();
    }

    private void spawnHeart(KingdomId kingdom) {
        Location location = base(kingdom).heart().in(world()).add(.5, 0, .5);
        EnderCrystal crystal = world().spawn(location, EnderCrystal.class, entity -> {
            entity.setShowingBottom(false);
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin,"session_id"),PersistentDataType.STRING,sessionId.toString());
            entity.getPersistentDataContainer().set(new NamespacedKey(plugin,"heart_owner"),PersistentDataType.STRING,kingdom.name());
        });
        hearts.put(kingdom, new Heart(kingdom, settings.heartHealth()));
        crystals.put(crystal.getUniqueId(), crystal);
        crystalOwners.put(crystal.getUniqueId(), kingdom);
    }

    public void damageHeart(EntityDamageByEntityEvent event) {
        UUID crystalId = event.getEntity().getUniqueId();
        EnderCrystal crystal = crystals.get(crystalId);
        if (crystal == null) return;
        event.setCancelled(true);
        Player attacker = event.getDamager() instanceof Player direct ? direct
                : event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (attacker == null) return;
        PlayerSession attackingPlayer = players.get(attacker.getUniqueId());
        Heart heart = hearts.get(crystalOwners.get(crystalId));
        if (attackingPlayer == null || heart == null || !protections.allowsHeartDamage(state.state())) return;
        HeartState before = heart.state();
        double requestedDamage=event.getFinalDamage();
        if(legacyCombat()&&event.getDamager() instanceof Player)requestedDamage=LegacyCombatRules.attackDamage(
                attacker.getInventory().getItemInMainHand().getType(),requestedDamage);
        KingdomHeartDamageEvent damage = new KingdomHeartDamageEvent(sessionId, heart.owner(), attacker, requestedDamage);
        Bukkit.getPluginManager().callEvent(damage);
        if (damage.isCancelled()) return;
        heart.damage(attackingPlayer.kingdom(), damage.damage(), false);
        if (before != HeartState.DESTROYED && heart.state() == HeartState.DESTROYED) {
            attackingPlayer.recordObjective();
            crystal.remove();
            markLastLives(heart.owner());
            Bukkit.getPluginManager().callEvent(new KingdomHeartDestroyedEvent(sessionId, heart.owner(),
                    KingdomHeartDestroyedEvent.Cause.PLAYER));
        }
    }

    private void markLastLives(KingdomId kingdom) {
        players.values().stream().filter(player -> player.kingdom() == kingdom).forEach(player -> {
            if (player.state() == PlayerLifeState.RESPAWNING || player.state() == PlayerLifeState.OFFLINE) {
                player.state(PlayerLifeState.ELIMINATED);
                BukkitTask pending=respawnTasks.remove(player.playerId());if(pending!=null)pending.cancel();
            } else if (player.state() == PlayerLifeState.ACTIVE) {
                player.state(PlayerLifeState.LAST_LIFE);
            }
        });
        scheduleWinnerCheck();
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
        scheduleWinnerCheck();
    }

    public void respawn(Player player) {
        PlayerSession runtime = players.get(player.getUniqueId());
        player.teleport(map.spectator().in(world()));
        player.setGameMode(GameMode.SPECTATOR);
        if (runtime == null || runtime.state() != PlayerLifeState.RESPAWNING) return;
        UUID playerId=player.getUniqueId();int[] remaining={settings.respawnDelaySeconds()};
        BukkitRunnable countdown=new BukkitRunnable(){@Override public void run(){
            PlayerSession current=players.get(playerId);Player online=Bukkit.getPlayer(playerId);
            if(current==null||current.state()!=PlayerLifeState.RESPAWNING||!state.state().active()){cancel();respawnTasks.remove(playerId);return;}
            if(remaining[0]<=0){cancel();respawnTasks.remove(playerId);completeRespawn(playerId);return;}
            if(online!=null&&Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)online.sendActionBar(core.getLanguageManager().getComponent(playerId,"fk.respawn-countdown",PlaceholderValues.of("seconds",remaining[0])));
            remaining[0]--;
        }};
        BukkitTask task=tasks.register(countdown.runTaskTimer(plugin,0L,20L));respawnTasks.put(playerId,task);
    }

    private void completeRespawn(UUID playerId) {
        PlayerSession runtime = players.get(playerId);
        if (runtime == null || runtime.state() != PlayerLifeState.RESPAWNING || !state.state().active()) return;
        Heart heart = hearts.get(runtime.kingdom());
        Player player = Bukkit.getPlayer(playerId);
        if (heart == null || heart.state() == HeartState.DESTROYED || player == null) {
            runtime.state(player == null ? PlayerLifeState.OFFLINE : PlayerLifeState.ELIMINATED);
            scheduleWinnerCheck();
            return;
        }
        runtime.state(PlayerLifeState.ACTIVE);
        Bukkit.getPluginManager().callEvent(new FallenKingdomsPlayerRespawnEvent(sessionId, player, runtime.kingdom()));
        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        player.teleport(base(runtime.kingdom()).spawn().in(world()));
    }

    public void join(Player player) {
        if (state.state() == GameState.WAITING || state.state() == GameState.COUNTDOWN) {
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(map.lobby().in(world()));
            if (state.state() == GameState.WAITING && settings.autoStart() && validRosterSize()) startCountdown();
            preferences.load(player.getUniqueId()).whenComplete((loaded, failure) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (failure != null || loaded == null || (state.state() != GameState.WAITING && state.state() != GameState.COUNTDOWN)) return;
                if (loaded.kitId() != null && kits.definitions().containsKey(loaded.kitId())) kitPreferences.put(player.getUniqueId(), loaded.kitId());
                if (loaded.kingdom() != null && map.bases().containsKey(loaded.kingdom())) kingdomPreferences.put(player.getUniqueId(), loaded.kingdom());
            }));
            return;
        }
        PlayerSession runtime = players.get(player.getUniqueId());
        if (runtime == null || runtime.state() == PlayerLifeState.ELIMINATED) {
            player.setGameMode(GameMode.SPECTATOR);
            player.teleport(map.spectator().in(world()));
        } else if (runtime.state() == PlayerLifeState.OFFLINE) {
            if(!settings.disconnectCountsAsDeath()){runtime.state(PlayerLifeState.ACTIVE);return;}
            runtime.state(PlayerLifeState.RESPAWNING);
            respawn(player);
        }
    }

    public void quit(Player player) {
        if (state.state() == GameState.COUNTDOWN) Bukkit.getScheduler().runTask(plugin, this::reevaluateCountdown);
        PlayerSession runtime = players.get(player.getUniqueId());
        if (!state.state().active() || runtime == null || !runtime.surviving()) return;
        if(!settings.disconnectCountsAsDeath()){runtime.state(PlayerLifeState.OFFLINE);return;}
        if (settings.dropInventory()) for (var item : player.getInventory().getContents()) if (item != null && !item.getType().isAir())
            player.getWorld().dropItemNaturally(player.getLocation(), item.clone());
        player.getInventory().clear();
        runtime.recordDeath();
        Heart heart = hearts.get(runtime.kingdom());
        runtime.state(heart != null && heart.state() != HeartState.DESTROYED
                && state.state() != GameState.SUDDEN_DEATH ? PlayerLifeState.OFFLINE : PlayerLifeState.ELIMINATED);
        scheduleWinnerCheck();
    }

    public boolean allowsPvp(Player attacker, Player victim) {
        PlayerSession left = players.get(attacker.getUniqueId());
        PlayerSession right = players.get(victim.getUniqueId());
        if (left == null || right == null || !left.surviving() || !right.surviving()) return false;
        return protections.allowsPvp(state.state(), territoryAt(victim.getLocation(), left.kingdom()), left.kingdom() == right.kingdom());
    }
    public boolean sameKingdom(Player leftPlayer,Player rightPlayer){PlayerSession left=players.get(leftPlayer.getUniqueId()),right=players.get(rightPlayer.getUniqueId());return left!=null&&right!=null&&left.kingdom()==right.kingdom();}
    public boolean legacyCombat(){return settings.combatProfile()==CombatProfile.LEGACY_1_8;}
    public void applyCombatProfile(EntityDamageByEntityEvent event,Player attacker,Player victim){
        if(!legacyCombat())return;event.setDamage(LegacyCombatRules.attackDamage(attacker.getInventory().getItemInMainHand().getType(),event.getDamage()));
        Vector direction=victim.getLocation().toVector().subtract(attacker.getLocation().toVector());
        Vector previous=victim.getVelocity();var knockback=LegacyCombatRules.knockback(previous.getX(),previous.getY(),previous.getZ(),direction.getX(),direction.getZ(),attacker.isSprinting());
        Bukkit.getScheduler().runTask(plugin,()->{if(state.state().active()&&victim.isOnline())victim.setVelocity(new Vector(knockback.x(),knockback.y(),knockback.z()));});
    }

    public ProtectionRules.Territory territoryAt(Location location, KingdomId perspective) {
        if (!map.playableRegion().contains(location)) return ProtectionRules.Territory.OUTSIDE;
        for (var entry : map.bases().entrySet()) if (entry.getValue().region().contains(location))
            if (ruinedKingdoms.contains(entry.getKey())) return ProtectionRules.Territory.COMMON;
            else
            return entry.getKey() == perspective ? ProtectionRules.Territory.OWN_BASE : ProtectionRules.Territory.ENEMY_BASE;
        return ProtectionRules.Territory.COMMON;
    }

    public boolean sameProtectionRegion(Location from,Location to){
        if(map.playableRegion().contains(from)!=map.playableRegion().contains(to))return false;
        return Objects.equals(baseOwnerAt(from),baseOwnerAt(to));
    }

    private KingdomId baseOwnerAt(Location location){
        for(var entry:map.bases().entrySet())if(!ruinedKingdoms.contains(entry.getKey())&&entry.getValue().region().contains(location))return entry.getKey();
        return null;
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

    public boolean mayExplosionChange(Block block) {
        Location location=block.getLocation();
        if (!state.state().active() || !map.playableRegion().contains(location)) return false;
        if(settings.preserveContainers()&&block.getState() instanceof Container)return false;
        if(block.getType()==Material.BEDROCK||block.getType()==Material.BARRIER||block.getType()==Material.END_PORTAL_FRAME)return false;
        boolean inBase = map.bases().values().stream().anyMatch(base -> base.region().contains(location));
        return !inBase || settings.tntBreachesEnabled()&&(state.state() == GameState.ASSAULT || state.state() == GameState.SUDDEN_DEATH);
    }

    private void scheduleWinnerCheck(){
        if(winnerCheckScheduled||!state.state().active())return;winnerCheckScheduled=true;UUID expected=sessionId;
        tasks.register(Bukkit.getScheduler().runTask(plugin,()->{winnerCheckScheduled=false;if(sessionId.equals(expected))checkWinner();}));
    }
    private void checkWinner() {
        if (!state.state().active()) return;
        Map<KingdomId, Integer> survivors = survivors();
        survivors.forEach((kingdom, count) -> {
            if (count == 0 && eliminatedKingdoms.add(kingdom)) {
                Bukkit.getPluginManager().callEvent(new KingdomEliminatedEvent(sessionId, kingdom));
                ruins.ruin(world(), base(kingdom), sessionId.getMostSignificantBits() ^ kingdom.ordinal(), () -> {
                    ruinedKingdoms.add(kingdom);
                    Bukkit.getPluginManager().callEvent(new KingdomBaseRuinedEvent(sessionId, kingdom));
                });
            }
        });
        long alive = survivors.values().stream().filter(value -> value > 0).count();
        if (alive <= 1) finish(new VictoryRules().lastKingdom(sessionId, survivors));
    }

    private void finishAtTimeLimit() { finish(new VictoryRules().timeLimit(sessionId, survivors())); }

    public void abort() {
        finish(new GameResult(sessionId, EndCause.ADMIN_ABORT, Set.of(), survivors(), Instant.now()));
    }
    public void abortForShutdown(){
        if(!state.state().active())return;
        GameResult aborted=new GameResult(sessionId,EndCause.ADMIN_ABORT,Set.of(),survivors(),Instant.now());
        if(transition(GameState.ENDING)){result=aborted;plugin.finishInstance(aborted,players.values());}
    }

    private void finish(GameResult result) {
        if (this.result != null || !transition(GameState.ENDING)) return;
        this.result = result;
        Bukkit.getPluginManager().callEvent(new FallenKingdomsGameEndEvent(result));
        plugin.updateInstanceStatus(ServerInstance.Status.GAME_ENDING);
        announceResult(result);
        crystals.values().forEach(EnderCrystal::remove);
        tasks.register(Bukkit.getScheduler().runTaskLater(plugin, () -> {
            tasks.cancelAll();
            transition(GameState.ENDED);
            plugin.finishInstance(result, players.values());
        }, settings.resultDisplaySeconds() * 20L));
    }

    public void shutdown() { tasks.cancelAll(); crystals.values().forEach(EnderCrystal::remove); hud.clear(); }
    public GameState state() { return state.state(); }
    public String mapId() { return map.id(); }
    public String mapDisplayNameKey() { return map.displayNameKey(); }
    public int participantCount() { return state.state() == GameState.WAITING || state.state() == GameState.COUNTDOWN
            ? Bukkit.getOnlinePlayers().size() : players.size(); }
    public GameResult result() { return result; }
    public UUID sessionId(){return sessionId;}
    public Map<String, KitDefinition> availableKits() { return kits.definitions(); }
    public Collection<MapDefinition> availableMaps() { return maps.maps(); }
    public Set<KingdomId> availableKingdoms() { return map.bases().keySet(); }
    public boolean chooseKit(UUID playerId, String kitId) {
        if ((state.state() != GameState.WAITING && state.state() != GameState.COUNTDOWN) || !kits.definitions().containsKey(kitId)) return false;
        kitPreferences.put(playerId, kitId); persistPreference(playerId); return true;
    }
    public boolean chooseKingdom(UUID playerId, KingdomId kingdom) {
        if ((state.state() != GameState.WAITING && state.state() != GameState.COUNTDOWN) || !map.bases().containsKey(kingdom)) return false;
        kingdomPreferences.put(playerId, kingdom); persistPreference(playerId); return true;
    }
    public boolean chooseMap(UUID playerId, String mapId) {
        if (state.state() != GameState.WAITING && state.state() != GameState.COUNTDOWN) return false;
        MapDefinition candidate;
        try { candidate = maps.select(mapId); } catch (IllegalArgumentException ignored) { return false; }
        int count = Bukkit.getOnlinePlayers().size();
        if (count >= 8) {
            try { if (!candidate.layouts().containsKey(configuredKingdomCount(count))) return false; }
            catch (IllegalArgumentException invalidRoster) { return false; }
        }
        mapVote.vote(playerId, candidate.id()); return true;
    }
    public long mapVotes(String mapId) { return mapVote.count(mapId); }
    public KingdomId kingdomOf(Player player) { PlayerSession runtime = players.get(player.getUniqueId()); return runtime == null ? null : runtime.kingdom(); }
    public boolean isParticipant(Player player) { return players.containsKey(player.getUniqueId()); }
    public void refreshHud(Player player){hud.update(player,elapsedSeconds());}
    public Heart heart(KingdomId kingdom) { return hearts.get(kingdom); }
    public Map<KingdomId, Integer> survivorCounts() { return Map.copyOf(survivors()); }
    public int elapsedSeconds() { return startedAt == 0 ? 0 : (int)((System.currentTimeMillis()-startedAt)/1000L); }
    public String remainingTime(int elapsed) {
        if(state.state()==GameState.COUNTDOWN)return formatDuration(Math.max(0,countdownRemaining));
        int next=switch(state.state()){case PREPARATION->timeline.pvpAt();case PVP->timeline.assaultAt();case ASSAULT->timeline.suddenDeathAt();case SUDDEN_DEATH->timeline.forceEndAt();default->elapsed;};
        return formatDuration(Math.max(0,next-elapsed));
    }

    private Map<KingdomId, Integer> survivors() {
        Map<KingdomId, Integer> result = new EnumMap<>(KingdomId.class);
        hearts.keySet().forEach(kingdom -> result.put(kingdom, 0));
        players.values().stream().filter(PlayerSession::surviving)
                .forEach(player -> result.merge(player.kingdom(), 1, Integer::sum));
        return result;
    }

    private void updateHud(int elapsedSeconds) {
        hud.updateAll(elapsedSeconds);
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return;
        String remaining = remainingTime(elapsedSeconds);
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
    private boolean transition(GameState target) {
        GameState previous = state.state();
        if (!state.transitionTo(target)) return false;
        if (previous != target) {
            Bukkit.getPluginManager().callEvent(new FallenKingdomsPhaseChangeEvent(sessionId, previous, target));
            announcePhase(target);
        }
        return true;
    }
    private void announcePhase(GameState target) {
        if (!(Bukkit.getPluginManager().getPlugin("TropicubeCore") instanceof TropicubeCore core)) return;
        for (Player player : Bukkit.getOnlinePlayers()) player.showTitle(Title.title(
                core.getLanguageManager().getComponent(player.getUniqueId(), "fk.phase-" + target.name().toLowerCase(java.util.Locale.ROOT)),
                core.getLanguageManager().getComponent(player.getUniqueId(), "fk.phase-change-subtitle"),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(500))));
    }
    private void persistPreference(UUID playerId) {
        preferences.save(playerId, new FallenKingdomsPreferenceService.Preference(
                kitPreferences.get(playerId), kingdomPreferences.get(playerId))).exceptionally(failure -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Préférence FK non persistée pour " + playerId, failure); return null;
        });
    }
}
