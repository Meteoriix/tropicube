package fr.tropicube.sheepwars.sheep;

import fr.tropicube.sheepwars.TropicubeSheepwars;
import fr.tropicube.sheepwars.player.GamePlayer;
import fr.tropicube.sheepwars.player.PlayerKit;
import fr.tropicube.sheepwars.sheep.types.*;
import fr.tropicube.sheepwars.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.DyeColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Registre les comportements des moutons, construit leurs objets et pilote leur
 * trajectoire jusqu'à l'impact. Toutes ses méthodes liées aux entités Bukkit
 * doivent être appelées sur le thread principal du serveur.
 */
public class SheepManager {

    /** Nettoie les entités et bonus temporaires au début et à la fin d'une partie. */
    public void reset() {
        strengthBuffCounts.clear();
        for (Map.Entry<UUID, MechaData> entry : mechaGolems.entrySet()) {
            org.bukkit.entity.Entity golem = org.bukkit.Bukkit.getEntity(entry.getKey());
            if (golem != null) golem.remove();
            org.bukkit.entity.Entity passenger = org.bukkit.Bukkit.getEntity(entry.getValue().passengerUUID());
            if (passenger != null) passenger.remove();
        }
        mechaGolems.clear();
    }

    public record MechaData(UUID throwerUUID, UUID passengerUUID) {}

    private final TropicubeSheepwars plugin;
    private final Map<SheepType, AbstractSheep> sheepHandlers = new EnumMap<>(SheepType.class);

    /** Associe chaque golem mécanique à son lanceur et à son passager. */
    private final Map<UUID, MechaData> mechaGolems = new HashMap<>();

    /** Compte les bonus de force actifs par joueur. */
    private final Map<UUID, Integer> strengthBuffCounts = new HashMap<>();

    /** Pondérations mises en cache et recalculées par {@link #buildWeightCache()}. */
    private EnumMap<SheepType, Integer> sheepWeights;
    private int sheepWeightTotal;

    public final NamespacedKey sheepTypeKey;

    public SheepManager(TropicubeSheepwars plugin) {
        this.plugin = plugin;
        this.sheepTypeKey = new NamespacedKey(plugin, "sheep_type");

        register(new BoardingSheep());
        register(new TntSheep());
        register(new DistortSheep());
        register(new DarknessSheep());
        register(new FireSheep());
        register(new SwapSheep());
        register(new MeteorSheep());
        register(new SearchingSheep());
        register(new HealingSheep());
        register(new LightningSheep());
        register(new GravitySheep());
        register(new MechaSheep());
        register(new StrengthSheep());
        register(new PoisonSheep());
        register(new FragmentationSheep());

        buildWeightCache();
    }

    private void register(AbstractSheep sheep) {
        sheep.injectPlugin(this.plugin);
        sheepHandlers.put(sheep.getType(), sheep);
    }

    /** Rebuilds the cached weight table from config (call after /reload or on game start). */
    public void buildWeightCache() {
        sheepWeights = new EnumMap<>(SheepType.class);
        sheepWeightTotal = 0;
        for (SheepType type : SheepType.values()) {
            int weight = Math.max(0,
                    plugin.getConfig().getInt("default-settings.sheep-probabilities." + type.getConfigKey(), 10));
            if (plugin.getGameSettingsMenu() != null && !plugin.getGameSettingsMenu().isSheepEnabled(type))
                weight = 0;
            sheepWeights.put(type, weight);
            sheepWeightTotal += weight;
        }
        // Garantit une distribution valide même si toute la configuration est désactivée.
        if (sheepWeightTotal == 0) {
            SheepType fallback = Arrays.stream(SheepType.values())
                    .filter(type -> plugin.getGameSettingsMenu() == null
                            || plugin.getGameSettingsMenu().isSheepEnabled(type))
                    .findFirst()
                    .orElse(SheepType.TNT);
            sheepWeights.put(fallback, 1);
            sheepWeightTotal = 1;
        }
    }

    public AbstractSheep getHandler(SheepType type) {
        return sheepHandlers.get(type);
    }

    // ── Mecha golems ───────────────────────────────────────────────────────

    public void registerGolem(UUID golemId, MechaData data) {
        mechaGolems.put(golemId, data);
    }

    public MechaData getGolem(UUID golemId) {
        return mechaGolems.get(golemId);
    }

    public MechaData removeGolem(UUID golemId) {
        return mechaGolems.remove(golemId);
    }

    // ── Strength buff ──────────────────────────────────────────────────────

    public void addStrengthBuff(UUID uuid) { strengthBuffCounts.merge(uuid, 1, Integer::sum); }
    public void removeStrengthBuff(UUID uuid) {
        strengthBuffCounts.computeIfPresent(uuid, (_, count) -> count > 1 ? count - 1 : null);
    }
    public boolean hasStrengthBuff(UUID uuid) { return strengthBuffCounts.containsKey(uuid); }

    // ── Item creation ──────────────────────────────────────────────────────

    public ItemStack createSheepItem(SheepType type) {
        Material wool = getWoolMaterial(type);

        Component name = Component.text("Mouton " + type.getDisplayName())
                .color(type.getTextColor())
                .decoration(TextDecoration.ITALIC, false);

        Component lore = Component.text(type.getDescription())
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);

        return new ItemBuilder(wool)
                .name(name)
                .lore(lore)
                .persistentData(sheepTypeKey, PersistentDataType.STRING, type.name())
                .build();
    }

    private Material getWoolMaterial(SheepType type) {
        // DyeColor name maps directly to <color>_WOOL material name
        return Material.valueOf(type.getWool().name() + "_WOOL");
    }

    // ── Random sheep selection ─────────────────────────────────────────────

    public SheepType randomSheepType() {
        int rand = ThreadLocalRandom.current().nextInt(sheepWeightTotal);
        int sum = 0;
        for (Map.Entry<SheepType, Integer> entry : sheepWeights.entrySet()) {
            sum += entry.getValue();
            if (rand < sum) return entry.getKey();
        }
        return SheepType.TNT; // Garde défensive si les pondérations sont modifiées de manière concurrente.
    }

    // ── Sheep lifecycle ────────────────────────────────────────────────────

    public void launchSheep(Player thrower, SheepType type) {
        Location spawnLoc = thrower.getEyeLocation().add(thrower.getLocation().getDirection());
        Vector velocity;
        switch (type) {
            case HEALING, STRENGTH -> velocity = thrower.getLocation().getDirection().multiply(0.1);
            default -> {
                Vector dir = thrower.getLocation().getDirection();
                // Ajoute un arc ascendant sauf si le joueur vise fortement vers le bas.
                double arcBoost = Math.max(0, 0.3 + dir.getY() * 0.5);
                velocity = dir.multiply(3.6).add(new Vector(0, arcBoost, 0));
            }
        }
        final DyeColor originalColor = type.getWool();

        Sheep sheep = thrower.getWorld().spawn(spawnLoc, Sheep.class, s -> {
            s.setColor(originalColor);
            s.setSilent(true);
            s.setAware(false);
            s.setInvulnerable(true);
            s.setVelocity(velocity);
            s.getPersistentDataContainer().set(sheepTypeKey, PersistentDataType.STRING, type.name());
        });

        AbstractSheep handler = getHandler(type);
        handler.onLaunch(thrower, sheep);

        new BukkitRunnable() {
            int ticks = 0;
            int countdownTicks = 0;
            boolean inCountdown = false;

            @Override
            public void run() {
                if (sheep.isDead() || !sheep.isValid()) {
                    cancel();
                    return;
                }

                if (inCountdown) {
                    countdownTicks++;
                    sheep.setColor(countdownTicks % 8 < 4 ? DyeColor.WHITE : originalColor);

                    if (countdownTicks >= 25) {
                        sheep.setColor(originalColor);
                        explode(thrower, sheep, handler);
                        cancel();
                    }
                    return;
                }

                if (ticks > 200) {
                    explode(thrower, sheep, handler);
                    cancel();
                    return;
                }

                if (handler.onTick()) {
                    sheep.remove();
                    cancel();
                    return;
                }

                // Attend deux ticks avant les collisions pour quitter la hitbox du lanceur.
                boolean impacted = false;
                if (ticks > 2) {
                    Location loc = sheep.getLocation();

                    // Les boîtes englobantes distinguent les blocs partiels des blocs pleins.
                    BoundingBox sheepBB = sheep.getBoundingBox().expand(0.05);
                    int bx = (int) Math.floor(loc.getX());
                    int by = (int) Math.floor(loc.getY());
                    int bz = (int) Math.floor(loc.getZ());
                    outer:
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 2; dy++) {
                            for (int dz = -1; dz <= 1; dz++) {
                                Block b = loc.getWorld().getBlockAt(bx + dx, by + dy, bz + dz);
                                if (!b.isPassable() && b.getBoundingBox().overlaps(sheepBB)) {
                                    impacted = true;
                                    break outer;
                                }
                            }
                        }
                    }

                    // Anticipe la trajectoire du tick afin d'éviter de traverser un bloc fin.
                    if (!impacted) {
                        Vector vel = sheep.getVelocity();
                        if (vel.lengthSquared() > 0.0001) {
                            double speed = vel.length();
                            Vector norm = vel.clone().normalize();
                            for (double d = 0.15; d <= Math.min(speed + 0.5, 6.0); d += 0.2) {
                                int x = (int) Math.floor(loc.getX() + norm.getX() * d);
                                int y = (int) Math.floor(loc.getY() + norm.getY() * d);
                                int z = (int) Math.floor(loc.getZ() + norm.getZ() * d);
                                if (!loc.getWorld().getBlockAt(x, y, z).isPassable()) {
                                    impacted = true;
                                    break;
                                }
                            }
                        }
                    }

                    // Player collision (expanded hitbox)
                    if (!impacted) {
                        for (var entity : sheep.getNearbyEntities(0.9, 0.9, 0.9)) {
                            if (entity instanceof Player p && !p.equals(thrower)) {
                                impacted = true;
                                break;
                            }
                        }
                    }
                }

                if (impacted) {
                    if (handler.hasCountdown()) {
                        inCountdown = true;
                        sheep.setInvulnerable(false);
                        sheep.setGravity(false);
                        sheep.setVelocity(new Vector(0, 0, 0));

                        // Base 25 HP; SUPPORT_SHEEP kit raises this to 40
                        double mineHp = 25.0;
                        GamePlayer gp = plugin.getGameManager().getPlayer(thrower);
                        if (gp != null && gp.getKit() == PlayerKit.SUPPORT_SHEEP) mineHp = 40.0;
                        var maxHp = sheep.getAttribute(Attribute.MAX_HEALTH);
                        if (maxHp != null) {
                            maxHp.setBaseValue(mineHp);
                            sheep.setHealth(mineHp);
                        }
                    } else {
                        explode(thrower, sheep, handler);
                        cancel();
                    }
                    return;
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void explode(Player thrower, Sheep sheep, AbstractSheep handler) {
        if (!sheep.isValid()) return;
        try {
            boolean shouldRemove = handler.onImpact(thrower, sheep);

            if (shouldRemove) sheep.remove();
        } catch (Exception e) {
            plugin.getLogger().warning("Erreur dans onImpact pour " + handler.getType() + ": " + e.getMessage());
            sheep.remove();
        }
    }

    // ── Utilities ──────────────────────────────────────────────────────────

    public boolean isNotGameSheep(Sheep sheep) {
        return !sheep.getPersistentDataContainer().has(sheepTypeKey, PersistentDataType.STRING);
    }

    public SheepType getSheepType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String value = item.getItemMeta().getPersistentDataContainer()
                .get(sheepTypeKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return SheepType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
