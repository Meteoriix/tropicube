package fr.tropicube.fallenkingdoms.game;

import org.bukkit.Material;
import java.util.List;

/** Validated immutable starter kit. */
public record KitDefinition(String id, Material icon, List<KitItem> items) {
    public KitDefinition { items = List.copyOf(items); }
    public record KitItem(Material material, int amount, int slot, String potionType) { }
}
