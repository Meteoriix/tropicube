package fr.tropicube.fallenkingdoms.game;

import org.bukkit.Material;
import java.util.List;
import java.util.Map;

/** Validated immutable starter kit. */
public record KitDefinition(String id, Material icon, List<KitItem> items) {
    public KitDefinition { items = List.copyOf(items); }
    public record KitItem(Material material, int amount, int slot, String potionType,
                          int damage, Map<String, Integer> enchantments) {
        public KitItem { enchantments = Map.copyOf(enchantments); }
    }
}
