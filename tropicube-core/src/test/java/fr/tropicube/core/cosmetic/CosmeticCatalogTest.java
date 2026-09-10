package fr.tropicube.core.cosmetic;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static fr.tropicube.core.cosmetic.CosmeticCatalog.*;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticCatalogTest {
    private Entry entry(String id, Access access, int threshold) {
        return new Entry(id, Category.TRAIL, access, threshold, "CLOUD");
    }
    @Test void accessRequiresTheRightLevelVipOrPurchase() {
        assertTrue(entry("free", Access.FREE, 0).available(1, 0, false));
        var level = entry("level", Access.LEVEL, 5);
        assertFalse(level.available(4, 3, true));
        assertTrue(level.available(5, 0, false));
        var vip = entry("vip", Access.VIP, 2);
        assertTrue(vip.available(1, 2, false));
        assertFalse(vip.available(99, 1, true));
        var paid = entry("paid", Access.CURRENCY, 500);
        assertFalse(paid.available(99, 3, false));
        assertTrue(paid.available(1, 0, true));
    }
    @Test void upcomingRewardsAreSortedAndExcludeUnlockedAndOtherAcquisitionRules() {
        var catalog = new CosmeticCatalog(List.of(entry("five", Access.LEVEL, 5), entry("free", Access.FREE, 0), entry("three", Access.LEVEL, 3)));
        assertEquals(List.of("three", "five"), catalog.upcoming(1).stream().map(Entry::id).toList());
        assertEquals(List.of("five"), catalog.upcoming(3).stream().map(Entry::id).toList());
        assertTrue(catalog.upcoming(5).isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> catalog.entries().clear());
    }
    @Test void rejectsInvalidEntriesDuplicatesAndEmptyCatalogues() {
        assertThrows(IllegalArgumentException.class, () -> entry("bad_id", Access.FREE, 0));
        assertThrows(IllegalArgumentException.class, () -> entry("bad", Access.FREE, 1));
        assertThrows(IllegalArgumentException.class, () -> entry("bad", Access.CURRENCY, 0));
        assertThrows(IllegalArgumentException.class, () -> entry("bad", Access.VIP, 4));
        assertThrows(IllegalArgumentException.class, () -> new CosmeticCatalog(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new CosmeticCatalog(List.of(entry("same", Access.FREE, 0), entry("same", Access.LEVEL, 3))));
    }
    @Test void bundledCatalogueContainsTheEightRequestedRewards() throws Exception {
        try (var input = getClass().getResourceAsStream("/cosmetics.yml")) {
            var catalog = CosmeticCatalog.load(input);
            assertEquals(8, catalog.entries().size());
            assertEquals(500, catalog.find("fireflies").requirement());
            assertEquals(300, catalog.find("crystal").requirement());
            assertThrows(IllegalArgumentException.class, () -> catalog.find("removed"));
        }
    }
    @Test void customDefinitionsRequireNamesInAllFourLanguages() {
        var catalog = new CosmeticCatalog(List.of(entry("custom", Access.FREE, 0)));
        assertDoesNotThrow(() -> catalog.validateNames((language, key) -> key.equals("cosmetics.name-custom")));
        var error = assertThrows(IllegalArgumentException.class,
                () -> catalog.validateNames((language, key) -> !language.equals("de")));
        assertTrue(error.getMessage().contains("languages/de.yml"));
        assertTrue(error.getMessage().contains("cosmetics.name-custom"));
    }
    @Test void rejectsMalformedYamlUnknownEnumsAndScalarCoercion() {
        String valid = "version: 1\nentries:\n  breeze:\n    category: TRAIL\n    access: FREE\n    requirement: 0\n    effect: CLOUD\n";
        for (String invalid : List.of(valid.replace("version: 1", "version: '1'"), valid.replace("TRAIL", "UNKNOWN"),
                valid.replace("requirement: 0", "requirement: 0.5"), valid.replace("requirement: 0", "requirement: '0'"),
                valid.replace("effect: CLOUD", "effect: 3"), "version: [")) {
            assertThrows(IllegalArgumentException.class, () -> CosmeticCatalog.load(new ByteArrayInputStream(invalid.getBytes(StandardCharsets.UTF_8))));
        }
    }
}
