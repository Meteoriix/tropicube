package fr.tropicube.fallenkingdoms.loot;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

/** Pure weighted selection with deterministic injection for tests. */
public final class LootRoller {
    public List<LootStack> roll(DailyLootTable table, RandomGenerator random) {
        Map<Material, Integer> totals = new LinkedHashMap<>();
        for (int draw = 0; draw < table.rolls(); draw++) {
            WeightedLootEntry selected = select(table, random.nextInt(table.totalWeight()));
            int amount = random.nextInt(selected.minimumAmount(), selected.maximumAmount() + 1);
            totals.merge(selected.material(), amount, Integer::sum);
        }
        List<LootStack> result = new ArrayList<>();
        totals.forEach((material, total) -> {
            int remaining = total;
            while (remaining > 0) {
                int amount = Math.min(remaining, 64);
                result.add(new LootStack(material, amount));
                remaining -= amount;
            }
        });
        return List.copyOf(result);
    }

    private WeightedLootEntry select(DailyLootTable table, int ticket) {
        int cursor = ticket;
        for (WeightedLootEntry entry : table.entries()) {
            cursor -= entry.weight();
            if (cursor < 0) return entry;
        }
        throw new IllegalStateException("Poids de butin incohérents");
    }

    public record LootStack(Material material, int amount) { }
}
