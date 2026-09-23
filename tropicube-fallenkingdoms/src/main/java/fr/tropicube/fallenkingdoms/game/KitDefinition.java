package fr.tropicube.fallenkingdoms.game;

import org.bukkit.Material;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Validated immutable starter kit. */
public record KitDefinition(String id, Material icon, List<KitItem> items) {
    public KitDefinition { items = List.copyOf(items); }

    /** Aggregates identical configured stacks while preserving their menu order. */
    public List<KitItemSummary> contentSummary() {
        Map<KitItemKey, KitItemSummary> summaries = new LinkedHashMap<>();
        for (KitItem item : items) {
            KitItemKey key = new KitItemKey(item.material(), item.potionType(), item.damage(), item.enchantments());
            summaries.compute(key, (ignored, current) -> current == null
                    ? new KitItemSummary(item, item.amount(), 1)
                    : new KitItemSummary(current.item(), current.amount() + item.amount(), current.stacks() + 1));
        }
        return List.copyOf(summaries.values());
    }

    public record KitItem(Material material, int amount, int slot, String potionType,
                          int damage, Map<String, Integer> enchantments) {
        public KitItem { enchantments = Map.copyOf(enchantments); }
    }

    public record KitItemSummary(KitItem item, int amount, int stacks) { }
    private record KitItemKey(Material material, String potionType, int damage, Map<String, Integer> enchantments) { }
}
