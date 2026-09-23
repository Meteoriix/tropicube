package fr.tropicube.fallenkingdoms.loot;

import java.util.List;

/** Immutable loot table applied to every configured chest for one game day. */
public record DailyLootTable(int day, int rolls, List<WeightedLootEntry> entries) {
    public DailyLootTable {
        entries = List.copyOf(entries);
        if (day < 2 || day > 6 || rolls <= 0 || rolls > 27 || entries.isEmpty()) {
            throw new IllegalArgumentException("Table de butin journalière invalide");
        }
    }

    public int totalWeight() {
        return entries.stream().mapToInt(WeightedLootEntry::weight).sum();
    }
}
