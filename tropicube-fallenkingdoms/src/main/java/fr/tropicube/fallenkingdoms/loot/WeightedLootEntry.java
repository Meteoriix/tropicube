package fr.tropicube.fallenkingdoms.loot;

import org.bukkit.Material;

/** One weighted material and inclusive quantity range. */
public record WeightedLootEntry(Material material, int minimumAmount, int maximumAmount, int weight) {
    public WeightedLootEntry {
        if (material == null || material == Material.AIR || minimumAmount <= 0
                || maximumAmount < minimumAmount || maximumAmount > 64 || weight <= 0) {
            throw new IllegalArgumentException("Entrée de butin invalide");
        }
    }
}
