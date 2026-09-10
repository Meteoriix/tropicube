package fr.tropicube.lobby.cosmetic;

import fr.tropicube.core.cosmetic.CosmeticCatalog;
import fr.tropicube.core.cosmetic.CosmeticService;
import fr.tropicube.lobby.TropicubeLobby;
import fr.tropicube.lobby.utils.LangHelper;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;

/** Single Paper task renders only nearby visible recipients; SQL is confined to load/refresh callbacks. */
public final class CosmeticEffects implements Listener, AutoCloseable {
    private final TropicubeLobby plugin;
    private final Map<UUID, CosmeticService.Snapshot> snapshots = new HashMap<>();
    private final Map<UUID, Boolean> enabled = new HashMap<>();
    private final Map<UUID, Location> previous = new HashMap<>();
    private final Map<UUID, PreviewWindow> previews = new HashMap<>();
    private final Map<UUID, Long> soundCooldown = new HashMap<>();
    private final Map<String, Particle> particles = new HashMap<>();
    private final Map<String, Sound> sounds = new HashMap<>();
    private final Map<UUID, Object> requests = new HashMap<>();
    private final BukkitTask task;
    private final int interval, count, previewTicks;
    private final double range;
    private long tick;
    private volatile boolean closed;
    private final Map<UUID, Long> preferenceRevisions = new HashMap<>();
    private long renderCalls, renderNanos, maximumRenderNanos, emissions;
    /** Aggregate measurements are read on Paper, without logging or allocating every tick. */
    public record Metrics(long calls, long totalNanos, long maximumNanos, long recipientEmissions) { }
    public Metrics metrics() { return new Metrics(renderCalls, renderNanos, maximumRenderNanos, emissions); }
    public CosmeticEffects(TropicubeLobby plugin) {
        this.plugin = plugin;
        CosmeticRenderSettings settings = CosmeticRenderSettings.load(plugin.getConfig());
        interval = settings.intervalTicks();
        count = settings.particles();
        previewTicks = settings.previewTicks();
        range = settings.rangeBlocks();
        for (var entry : plugin.getCore().getCosmeticService().catalog().entries()) {
            try {
                if (entry.category() == CosmeticCatalog.Category.TRAIL) {
                    Particle particle = Particle.valueOf(entry.effect());
                    if (particle.getDataType() != Void.class) throw new IllegalArgumentException("Particle requires data");
                    particles.put(entry.id(), particle);
                } else {
                    Sound sound = Registry.SOUNDS.get(NamespacedKey.fromString(entry.effect()));
                    if (sound == null) throw new IllegalArgumentException("Unknown sound");
                    sounds.put(entry.id(), sound);
                }
            } catch (RuntimeException error) { throw new IllegalArgumentException("cosmetics.entries." + entry.id()+".effect="+entry.effect(), error); }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::render, interval, interval);
        Bukkit.getOnlinePlayers().forEach(this::refresh);
    }
    /** Loads a fresh authoritative selection after arrival or mutation; older requests cannot win. */
    public void refresh(Player player) {
        UUID id = player.getUniqueId();
        Object request = new Object();
        requests.put(id, request);
        long revision = preferenceRevisions.getOrDefault(id, 0L);
        plugin.getCore().getCosmeticService().snapshot(id).thenCombine(
                plugin.getCore().getPlayerPreferenceService().load(id), (snapshot, preferences) -> new Loaded(snapshot, preferences.lobbyEffectsEnabled()))
                .whenComplete((loaded, error) -> onServer(() -> {
                    if (!player.isOnline() || Bukkit.getPlayer(id) != player || requests.get(id) != request) return;
                    if (error != null) {
                        snapshots.remove(id);
                        plugin.getLogger().log(java.util.logging.Level.WARNING, "Cosmetic load failed for " + id, error);
                        return;
                    }
                    snapshots.put(id, loaded.snapshot());
                    if (preferenceRevisions.getOrDefault(id, 0L) == revision) enabled.put(id, loaded.enabled());
                }));
    }
    private record Loaded(CosmeticService.Snapshot snapshot, boolean enabled) {}
    public void setEffectsEnabled(UUID id, boolean value) {
        preferenceRevisions.merge(id, 1L, Long::sum);
        enabled.put(id, value);
        if (!value) previews.remove(id);
    }
    public void preview(Player player, CosmeticCatalog.Entry entry) {
        UUID id = player.getUniqueId();
        if (!enabled.getOrDefault(id, false)) { player.sendMessage(LangHelper.component(player, "cosmetics.effects-disabled")); return; }
        if (entry.category() == CosmeticCatalog.Category.TRAIL) previews.put(id, new PreviewWindow(entry, tick + previewTicks));
        else if (soundCooldown.getOrDefault(id, 0L) <= tick) {
            soundCooldown.put(id, tick+20);
            player.playSound(player.getLocation(), sounds.get(entry.id()), 0.5f, 1.2f);
        }
    }
    /** Replaces the first-login chime only; sound remains private and arrival semantics stay unchanged. */
    public void playWelcome(Player player) {
        UUID id = player.getUniqueId();
        long revision = preferenceRevisions.getOrDefault(id, 0L);
        plugin.getCore().getCosmeticService().snapshot(id).thenCombine(
                plugin.getCore().getPlayerPreferenceService().load(id),
                (snapshot, preferences) -> new Loaded(snapshot, preferences.lobbyEffectsEnabled()))
                .whenComplete((loaded, error) -> onServer(() -> {
            if (!player.isOnline() || Bukkit.getPlayer(id) != player) return;
            boolean effectsEnabled = preferenceRevisions.getOrDefault(id, 0L) != revision
                    ? enabled.getOrDefault(id, false) : error == null && loaded.enabled();
            if (!effectsEnabled) {
                if (error != null) plugin.getLogger().log(java.util.logging.Level.WARNING, "Welcome preference unavailable for " + id, error);
                return;
            }
            Sound sound = Sound.BLOCK_NOTE_BLOCK_CHIME;
            if (error == null) {
                String selected = loaded.snapshot().equipped().get(CosmeticCatalog.Category.SOUND);
                for (var entry : plugin.getCore().getCosmeticService().catalog().entries())
                    if (entry.id().equals(selected) && allowed(player, entry, loaded.snapshot())) sound = sounds.get(entry.id());
            } else plugin.getLogger().log(java.util.logging.Level.WARNING, "Welcome cosmetic unavailable for " + id, error);
            player.playSound(player.getLocation(), sound, 0.7f, 1.2f);
        }));
    }
    private boolean allowed(Player player, CosmeticCatalog.Entry entry, CosmeticService.Snapshot snapshot) {
        return entry.available(snapshot.level(), plugin.getCore().getPermissionManager().getVipLevel(player.getUniqueId()), snapshot.purchased().contains(entry.id()));
    }
    private void render() {
        long started = System.nanoTime();
        try { renderTrails(); }
        finally {
            long elapsed = System.nanoTime() - started;
            renderCalls++;
            renderNanos += elapsed;
            maximumRenderNanos = Math.max(maximumRenderNanos, elapsed);
        }
    }
    private void renderTrails() {
        tick += interval;
        for (Player source : Bukkit.getOnlinePlayers()) {
            UUID id = source.getUniqueId(); Location position = source.getLocation();
            Location last = previous.put(id, position);
            PreviewWindow preview = previews.get(id);
            if (preview != null) {
                if (!preview.active(tick)) previews.remove(id);
                else if (enabled.getOrDefault(id, false)) emit(source, position, preview.entry());
                continue;
            }
            if (!enabled.getOrDefault(id, false) || last == null || last.getWorld() != position.getWorld()
                    || last.distanceSquared(position) < 0.0025) continue;
            CosmeticService.Snapshot snapshot = snapshots.get(id);
            if (snapshot == null) continue;
            String selected = snapshot.equipped().get(CosmeticCatalog.Category.TRAIL);
            if (selected == null) continue;
            var entry = plugin.getCore().getCosmeticService().catalog().entries().stream().filter(value -> value.id().equals(selected)).findFirst().orElse(null);
            if (entry == null || !allowed(source, entry, snapshot)) continue;
            emit(source, position, entry);
            for (Player viewer : position.getWorld().getNearbyPlayers(position, range)) {
                if (viewer != source && canReceive(enabled.getOrDefault(viewer.getUniqueId(), false), viewer.canSee(source),
                        viewer.getLocation().distanceSquared(position), range)) emit(viewer, position, entry);
            }
        }
    }
    /** Uses the same inclusive radius after Paper's spatial candidate search. */
    static boolean canReceive(boolean effectsEnabled, boolean sourceVisible, double distanceSquared, double range) {
        return effectsEnabled && sourceVisible && distanceSquared <= range * range;
    }
    private void emit(Player recipient, Location position, CosmeticCatalog.Entry entry) {
        emissions++;
        recipient.spawnParticle(particles.get(entry.id()), position.getX(), position.getY()+0.15, position.getZ(), count, 0.12, 0.05, 0.12, 0);
    }
    @EventHandler public void join(PlayerJoinEvent event) { refresh(event.getPlayer()); }
    @EventHandler public void quit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        snapshots.remove(id); enabled.remove(id); previous.remove(id); previews.remove(id); soundCooldown.remove(id); requests.remove(id); preferenceRevisions.remove(id);
    }
    private void onServer(Runnable action) { if (!closed && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> { if (!closed) action.run(); }); }
    @Override public void close() {
        closed = true; task.cancel(); HandlerList.unregisterAll(this);
        snapshots.clear(); enabled.clear(); previous.clear(); previews.clear(); soundCooldown.clear(); requests.clear(); preferenceRevisions.clear();
    }
}
