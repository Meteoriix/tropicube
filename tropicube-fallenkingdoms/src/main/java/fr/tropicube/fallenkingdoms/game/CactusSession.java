package fr.tropicube.fallenkingdoms.game;

import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import org.bukkit.*;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/** Paper adapter for one Cactus session. All mutation happens on the server thread. */
public final class CactusSession {
    private final TropicubeFallenKingdoms plugin;
    private final GameStateMachine state;
    private final PhaseTimeline timeline;
    private final Map<UUID, KingdomId> players = new HashMap<>();
    private final Map<KingdomId, Heart> hearts = new EnumMap<>(KingdomId.class);
    private final Map<UUID, EnderCrystal> crystals = new HashMap<>();
    private final Map<UUID, KingdomId> crystalOwners = new HashMap<>();
    private BukkitTask countdownTask, phaseTask;
    private long startedAt;
    public CactusSession(TropicubeFallenKingdoms plugin, GameStateMachine state) {
        this.plugin = plugin; this.state = state; this.timeline = plugin.settings().timeline();
    }
    public boolean startCountdown() {
        if (!state.transitionTo(GameState.COUNTDOWN)) return false;
        if (Bukkit.getOnlinePlayers().size() < 8) { state.transitionTo(GameState.WAITING); return false; }
        countdownTask = Bukkit.getScheduler().runTaskLater(plugin, this::start, plugin.settings().countdownSeconds() * 20L);
        return true;
    }
    public boolean cancelCountdown() { if (countdownTask != null) countdownTask.cancel(); return state.transitionTo(GameState.WAITING); }
    private void start() {
        if (Bukkit.getOnlinePlayers().size() < 8 || !state.transitionTo(GameState.PREPARATION)) { state.transitionTo(GameState.WAITING); return; }
        List<KingdomAllocator.PlayerPreference> roster = Bukkit.getOnlinePlayers().stream()
                .map(player -> new KingdomAllocator.PlayerPreference(player.getUniqueId(), null)).toList();
        players.putAll(new KingdomAllocator().allocate(roster));
        for (KingdomId kingdom : EnumSet.copyOf(players.values())) spawnHeart(kingdom);
        for (Player player : Bukkit.getOnlinePlayers()) {
            KingdomId kingdom = players.get(player.getUniqueId());
            if (kingdom != null) { player.setGameMode(GameMode.SURVIVAL); teleport(player, "locations.maps.cactus.kingdoms." + kingdom.name().toLowerCase(Locale.ROOT) + ".spawn"); }
        }
        startedAt = System.currentTimeMillis();
        phaseTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }
    private void tick() {
        int seconds = (int) ((System.currentTimeMillis() - startedAt) / 1000L);
        GameState target = timeline.targetAt(seconds);
        if (target == GameState.ENDING) { end(); return; }
        if (target != state.state() && state.transitionTo(target) && target == GameState.ASSAULT) hearts.values().forEach(Heart::makeVulnerable);
        if (target == GameState.SUDDEN_DEATH) hearts.values().forEach(Heart::destroy);
    }
    private void spawnHeart(KingdomId kingdom) {
        String root = "locations.maps.cactus.kingdoms." + kingdom.name().toLowerCase(Locale.ROOT) + ".heart";
        World world = Bukkit.getWorld(plugin.getConfig().getString("locations.world", "world")); if (world == null) throw new IllegalStateException("Monde FK introuvable");
        var section = plugin.getConfig().getConfigurationSection(root); if (section == null) throw new IllegalStateException("Cœur manquant: " + root);
        Location location = new Location(world, section.getDouble("x") + .5, section.getDouble("y"), section.getDouble("z") + .5);
        EnderCrystal crystal = world.spawn(location, EnderCrystal.class, entity -> entity.setShowingBottom(false));
        hearts.put(kingdom, new Heart(kingdom, plugin.settings().heartHealth())); crystals.put(crystal.getUniqueId(), crystal); crystalOwners.put(crystal.getUniqueId(), kingdom);
    }
    public void damageHeart(EntityDamageByEntityEvent event) {
        UUID crystalId = event.getEntity().getUniqueId(); EnderCrystal crystal = crystals.get(crystalId); if (crystal == null) return;
        event.setCancelled(true);
        if (!(event.getDamager() instanceof Player attacker)) return;
        KingdomId team = players.get(attacker.getUniqueId());
        Heart heart = hearts.get(crystalOwners.get(crystalId));
        if (heart == null || !state.state().equals(GameState.ASSAULT)) return;
        heart.damage(team, event.getFinalDamage(), false);
        if (heart.state() == HeartState.DESTROYED) { crystal.remove(); checkWinner(); }
    }
    public void playerDied(Player player) { player.setGameMode(GameMode.SPECTATOR); checkWinner(); }
    private void checkWinner() {
        Set<KingdomId> alive = new HashSet<>();
        for (var entry : players.entrySet()) { Player player = Bukkit.getPlayer(entry.getKey()); if (player != null && player.getGameMode() != GameMode.SPECTATOR) alive.add(entry.getValue()); }
        if (state.state().active() && alive.size() <= 1) end();
    }
    public void end() { if (state.state() == GameState.ENDED || state.state() == GameState.ENDING) return; state.transitionTo(GameState.ENDING); if (phaseTask != null) phaseTask.cancel(); crystals.values().forEach(EnderCrystal::remove); Bukkit.getScheduler().runTaskLater(plugin, () -> state.transitionTo(GameState.ENDED), plugin.settings().resultDisplaySeconds() * 20L); }
    public void shutdown() { if (countdownTask != null) countdownTask.cancel(); if (phaseTask != null) phaseTask.cancel(); crystals.values().forEach(EnderCrystal::remove); }
    private void teleport(Player player, String root) { var section = plugin.getConfig().getConfigurationSection(root); if (section == null) return; player.teleport(new Location(player.getWorld(), section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"), (float) section.getDouble("pitch"))); }
}
