package fr.tropicube.fallenkingdoms.loot;

import fr.tropicube.fallenkingdoms.TropicubeFallenKingdoms;
import fr.tropicube.fallenkingdoms.map.MapDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Random;

/** Validates, marks and refills only the map-owned loot inventories. */
public final class LootChestService {
    private final TropicubeFallenKingdoms plugin;
    private final LootTables tables;
    private final LootRoller roller = new LootRoller();
    private final Random random;
    private final NamespacedKey markerKey;
    private final Set<String> readyMaps = new HashSet<>();
    private final Set<BlockKey> activeBlocks = new HashSet<>();
    private final Map<String, String> failures = new HashMap<>();
    private MapDefinition activeMap;

    public LootChestService(TropicubeFallenKingdoms plugin, LootTables tables, UUID sessionId) {
        this.plugin = plugin;
        this.tables = tables;
        this.random = new Random(sessionId.getMostSignificantBits() ^ sessionId.getLeastSignificantBits());
        this.markerKey = new NamespacedKey(plugin, "progressive_loot_chest");
    }

    /** Loads required chunks asynchronously, then validates chest blocks on the server thread. */
    public void validateAsync(Collection<MapDefinition> maps, Runnable completion) {
        Map<BlockKey, CompletableFuture<?>> chunks = new HashMap<>();
        for (MapDefinition map : maps) {
            World world = Bukkit.getWorld(map.world());
            if (world == null) {
                failures.put(map.id(), "monde introuvable: " + map.world());
                continue;
            }
            for (LootChestDefinition definition : map.lootChests()) {
                BlockKey key = BlockKey.of(world, definition.position().x(), definition.position().y(), definition.position().z());
                chunks.computeIfAbsent(key.chunk(), ignored -> world.getChunkAtAsync(key.x() >> 4, key.z() >> 4));
            }
        }
        CompletableFuture.allOf(chunks.values().toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (failure != null) plugin.getLogger().log(java.util.logging.Level.SEVERE,
                            "Impossible de charger les chunks des coffres FK", failure);
                    for (MapDefinition map : maps) validateMap(map, failure);
                    completion.run();
                }));
    }

    private void validateMap(MapDefinition map, Throwable loadFailure) {
        if (loadFailure != null || failures.containsKey(map.id())) return;
        World world = Bukkit.getWorld(map.world());
        Set<String> inventories = new HashSet<>();
        for (LootChestDefinition definition : map.lootChests()) {
            BlockKey key = BlockKey.of(world, definition.position().x(), definition.position().y(), definition.position().z());
            if (!(world.getBlockAt(key.x(), key.y(), key.z()).getState() instanceof Chest chest)) {
                failures.put(map.id(), definition.id() + " ne référence pas un coffre préplacé en " + key);
                return;
            }
            String inventoryKey = inventoryKey(chest.getInventory());
            if (!inventories.add(inventoryKey)) {
                failures.put(map.id(), "le même coffre est référencé plusieurs fois: " + definition.id());
                return;
            }
        }
        readyMaps.add(map.id());
    }

    public boolean ready(MapDefinition map) {
        return readyMaps.contains(map.id());
    }

    public String failure(MapDefinition map) {
        return failures.get(map.id());
    }

    /** Marks and empties all selected-map inventories at the start of preparation. */
    public void initialize(MapDefinition map) {
        if (!ready(map)) throw new IllegalStateException("Coffres non validés pour " + map.id());
        activeMap = map;
        activeBlocks.clear();
        World world = Bukkit.getWorld(map.world());
        for (LootChestDefinition definition : map.lootChests()) {
            BlockKey configured = BlockKey.of(world, definition.position().x(), definition.position().y(), definition.position().z());
            Chest chest = (Chest) world.getBlockAt(configured.x(), configured.y(), configured.z()).getState();
            for (Chest side : sides(chest.getInventory())) {
                side.getPersistentDataContainer().set(markerKey, PersistentDataType.STRING, definition.id());
                side.update(true, false);
                activeBlocks.add(BlockKey.of(side.getLocation()));
            }
            chest.getInventory().clear();
        }
    }

    /** Replaces every special inventory with the configured table for the supplied day. */
    public void refill(int day) {
        if (activeMap == null) return;
        DailyLootTable table = tables.forDay(day);
        World world = Bukkit.getWorld(activeMap.world());
        Set<String> filled = new HashSet<>();
        for (LootChestDefinition definition : activeMap.lootChests()) {
            BlockKey key = BlockKey.of(world, definition.position().x(), definition.position().y(), definition.position().z());
            Chest chest = (Chest) world.getBlockAt(key.x(), key.y(), key.z()).getState();
            Inventory inventory = chest.getInventory();
            if (!filled.add(inventoryKey(inventory))) continue;
            inventory.clear();
            var slots = new ArrayList<Integer>();
            for (int slot = 0; slot < inventory.getSize(); slot++) slots.add(slot);
            Collections.shuffle(slots, random);
            var stacks = roller.roll(table, random);
            for (int index = 0; index < stacks.size(); index++) {
                LootRoller.LootStack stack = stacks.get(index);
                inventory.setItem(slots.get(index), new ItemStack(stack.material(), stack.amount()));
            }
        }
    }

    public boolean isLootBlock(org.bukkit.block.Block block) {
        return activeBlocks.contains(BlockKey.of(block.getLocation()));
    }

    public boolean isLootInventory(Inventory inventory) {
        return sides(inventory).stream().anyMatch(chest -> activeBlocks.contains(BlockKey.of(chest.getLocation())));
    }

    private static Collection<Chest> sides(Inventory inventory) {
        InventoryHolder holder = inventory.getHolder(false);
        if (holder instanceof Chest chest) return java.util.List.of(chest);
        if (holder instanceof DoubleChest doubleChest) {
            var result = new ArrayList<Chest>(2);
            if (doubleChest.getLeftSide() instanceof Chest left) result.add(left);
            if (doubleChest.getRightSide() instanceof Chest right) result.add(right);
            return result;
        }
        return java.util.List.of();
    }

    private static String inventoryKey(Inventory inventory) {
        return sides(inventory).stream().map(Chest::getLocation).map(BlockKey::of).map(BlockKey::toString)
                .sorted().reduce((left, right) -> left + "|" + right).orElse("invalid");
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {
        static BlockKey of(Location location) {
            return new BlockKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
        static BlockKey of(World world, double x, double y, double z) {
            return new BlockKey(world.getUID(), (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        }
        BlockKey chunk() { return new BlockKey(worldId, x >> 4, 0, z >> 4); }
        @Override public String toString() { return x + "," + y + "," + z; }
    }
}
