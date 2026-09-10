package fr.tropicube.core.cosmetic;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static fr.tropicube.core.cosmetic.CosmeticCatalog.*;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticSnapshotTest {
    @Test void filtersPartitionTheCatalogueAndVipLossKeepsTheSelectionWithoutAccess() throws Exception {
        try (var input = getClass().getResourceAsStream("/cosmetics.yml")) {
            var catalog = CosmeticCatalog.load(input);
            var selections = new EnumMap<Category, String>(Category.class);
            selections.put(Category.TRAIL, "hearts");
            var owned = new HashSet<String>(Set.of("fireflies"));
            var snapshot = new CosmeticService.Snapshot(1, 0, 0, BigDecimal.ZERO, owned, selections);
            owned.clear(); selections.clear();
            assertEquals("hearts", snapshot.equipped().get(Category.TRAIL));
            assertFalse(snapshot.available(catalog.find("hearts")));
            assertTrue(snapshot.available(catalog.find("fireflies")));
            var available = snapshot.entries(catalog, Category.TRAIL, CosmeticService.Filter.AVAILABLE);
            var locked = snapshot.entries(catalog, Category.TRAIL, CosmeticService.Filter.LOCKED);
            assertEquals(List.of("breeze", "fireflies"), available.stream().map(Entry::id).toList());
            assertEquals(List.of("sparks", "hearts"), locked.stream().map(Entry::id).toList());
            assertEquals(4, snapshot.entries(catalog, Category.TRAIL, CosmeticService.Filter.ALL).size());
            assertThrows(UnsupportedOperationException.class, () -> snapshot.equipped().clear());
            assertThrows(UnsupportedOperationException.class, () -> available.clear());
        }
    }
}
