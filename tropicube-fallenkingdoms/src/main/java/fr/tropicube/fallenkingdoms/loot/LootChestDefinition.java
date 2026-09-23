package fr.tropicube.fallenkingdoms.loot;

import fr.tropicube.fallenkingdoms.map.Position;

/** One explicitly map-owned progressive loot chest. */
public record LootChestDefinition(String id, Position position) {
    public LootChestDefinition {
        if (id == null || !id.matches("[a-z0-9-]+") || position == null) {
            throw new IllegalArgumentException("Coffre de butin sans identifiant ou position valide");
        }
    }
}
