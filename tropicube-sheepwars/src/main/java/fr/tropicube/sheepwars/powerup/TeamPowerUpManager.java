package fr.tropicube.sheepwars.powerup;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.game.GameManager;
import fr.tropicube.sheepwars.game.GameMap;
import fr.tropicube.sheepwars.game.GameState;
import fr.tropicube.sheepwars.game.GameTeam;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.util.LangHelper;
import fr.tropicube.sheepwars.util.PlayerDisplayName;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns the single floating wool target for one running match. All entity and inventory work stays
 * on the Paper thread, and {@link #stop()} invalidates it before a late tick can act.
 */
public final class TeamPowerUpManager {
    private final TropicubeSheepwars plugin;
    private final GameManager gameManager;
    private final TeamPowerUpSettings settings;
    private final TeamPowerUpPicker picker;
    private final TeamPowerUpSpawnPicker spawnPicker = new TeamPowerUpSpawnPicker();
    private final List<Location> spawnCandidates = new ArrayList<>();
    private final Map<UUID, Location> previousArrowLocations = new HashMap<>();
    private Target activeTarget;
    private BukkitTask task;
    private long currentTick;
    private long respawnAtTick;

    public TeamPowerUpManager(TropicubeSheepwars plugin, GameManager gameManager,
                              TeamPowerUpSettings settings) {
        this.plugin = plugin;
        this.gameManager = gameManager;
        this.settings = settings;
        this.picker = new TeamPowerUpPicker(settings.weights());
    }

    /** Selects one configured target location and begins collision checks for the selected map. */
    public void start(GameMap map) {
        stop();
        if (!settings.enabled() || map == null || map.getPowerUpSpawns().isEmpty()) return;
        map.getPowerUpSpawns().stream().map(Location::clone).forEach(spawnCandidates::add);
        spawnNext();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    /** Removes displays and cancels the retained task. Safe to call repeatedly. */
    public void stop() {
        if (task != null) task.cancel();
        task = null;
        if (activeTarget != null) activeTarget.removeDisplay();
        activeTarget = null;
        spawnCandidates.clear();
        spawnPicker.reset();
        previousArrowLocations.clear();
        currentTick = 0;
        respawnAtTick = 0;
    }

    private void tick() {
        if (gameManager.getState() != GameState.PLAYING) {
            stop();
            return;
        }
        currentTick++;
        if (activeTarget == null && currentTick >= respawnAtTick) spawnNext();
        detectArrowHits();
        if (currentTick % 20 == 0) {
            previousArrowLocations.entrySet().removeIf(entry -> {
                var entity = plugin.getServer().getEntity(entry.getKey());
                return !(entity instanceof AbstractArrow arrow) || !arrow.isValid();
            });
        }
    }

    private void detectArrowHits() {
        Target target = activeTarget;
        if (target == null || target.display == null) return;
        World world = target.location.getWorld();
        if (world == null) return;

        double radiusSquared = settings.hitRadius() * settings.hitRadius();
        for (AbstractArrow arrow : world.getEntitiesByClass(AbstractArrow.class)) {
            if (!(arrow.getShooter() instanceof Player shooter) || !arrow.isValid()) continue;
            if (arrow.getVelocity().lengthSquared() < 0.0001) continue;
            GamePlayer gamePlayer = gameManager.getPlayer(shooter);
            if (gamePlayer == null || !gamePlayer.isAlive() || gamePlayer.getTeam() == null) continue;

            Location current = arrow.getLocation();
            Location previous = previousArrowLocations.put(arrow.getUniqueId(), current.clone());
            if (previous == null || previous.getWorld() != current.getWorld()) {
                previous = current.clone().subtract(arrow.getVelocity());
            }
            if (target.location.getWorld() != current.getWorld()) continue;
            double distanceSquared = ArrowPathCollision.distanceSquaredToSegment(
                    previous.getX(), previous.getY(), previous.getZ(),
                    current.getX(), current.getY(), current.getZ(),
                    target.location.getX(), target.location.getY(), target.location.getZ());
            if (distanceSquared > radiusSquared) continue;
            activate(target, shooter, gamePlayer.getTeam());
            previousArrowLocations.remove(arrow.getUniqueId());
            arrow.remove();
            return;
        }
    }

    private void spawnNext() {
        if (spawnCandidates.isEmpty()) return;
        Location location = spawnCandidates.get(spawnPicker.pick(spawnCandidates.size())).clone();
        Target target = new Target(location);
        TeamPowerUpType type = picker.pick();
        Location entityLocation = target.location.clone().subtract(0.5, 0.5, 0.5);
        BlockDisplay display = Objects.requireNonNull(target.location.getWorld()).spawn(entityLocation,
                BlockDisplay.class, spawned -> {
                    spawned.setBlock(type.material().createBlockData());
                    spawned.setGlowing(true);
                    spawned.setGlowColorOverride(Color.WHITE);
                    spawned.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                    spawned.setPersistent(false);
                    spawned.setViewRange(128.0F);
                });
        target.type = type;
        target.display = display;
        activeTarget = target;
        target.location.getWorld().spawnParticle(Particle.END_ROD, target.location, 14, 0.45, 0.45, 0.45, 0.01);
    }

    private void activate(Target target, Player shooter, GameTeam team) {
        TeamPowerUpType type = target.type;
        target.removeDisplay();
        activeTarget = null;
        respawnAtTick = currentTick + settings.respawnTicks();
        apply(type, team);

        World world = target.location.getWorld();
        if (world != null) {
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, target.location, 30, 0.6, 0.6, 0.6, 0.1);
            world.playSound(target.location, Sound.BLOCK_BEACON_ACTIVATE, 1.0F, 1.25F);
        }
        String shooterName = PlayerDisplayName.resolve(shooter);
        for (GamePlayer teammate : gameManager.getAliveTeamPlayers(team)) {
            Player player = teammate.getBukkitPlayer();
            if (player == null) continue;
            player.sendMessage(LangHelper.component(player, "sw.powerup-activated", shooterName,
                    LangHelper.get(player, type.languageKey())));
        }
    }

    private void apply(TeamPowerUpType type, GameTeam team) {
        for (GamePlayer teammate : gameManager.getAliveTeamPlayers(team)) {
            Player player = teammate.getBukkitPlayer();
            if (player == null) continue;
            switch (type) {
                case HEALING -> {
                    var maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
                    double maximum = maxHealth == null ? 20.0 : maxHealth.getValue();
                    player.setHealth(Math.min(maximum, player.getHealth() + settings.healingHealth()));
                }
                case POISON_ARROWS -> player.getInventory().addItem(createPoisonArrows(player));
                case SPEED -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                        settings.speedDurationTicks(), settings.speedAmplifier(), false, true, true));
            }
        }
    }

    private ItemStack createPoisonArrows(Player player) {
        ItemStack arrows = new ItemStack(org.bukkit.Material.TIPPED_ARROW, settings.poisonArrowCount());
        PotionMeta meta = (PotionMeta) arrows.getItemMeta();
        meta.addCustomEffect(new PotionEffect(PotionEffectType.POISON, settings.poisonDurationTicks(),
                settings.poisonAmplifier()), true);
        meta.customName(LangHelper.component(player, "sw.powerup-poison-arrow-item"));
        arrows.setItemMeta(meta);
        return arrows;
    }

    private static final class Target {
        private final Location location;
        private BlockDisplay display;
        private TeamPowerUpType type;

        private Target(Location location) {
            this.location = location;
        }

        private void removeDisplay() {
            if (display != null) display.remove();
            display = null;
            type = null;
        }
    }
}
