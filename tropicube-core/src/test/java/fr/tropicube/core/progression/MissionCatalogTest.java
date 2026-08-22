package fr.tropicube.core.progression;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MissionCatalogTest {
    @Test void bundledCatalogIsVersionedAndSupportsRerolls() {
        MissionCatalog catalog = MissionCatalog.load(getClass().getClassLoader().getResourceAsStream("missions.yml"));
        assertEquals(2, catalog.version());
        assertTrue(catalog.daily().size() >= 6);
        assertTrue(catalog.weekly().size() >= 4);
        assertEquals("MATCH_PLAYED", catalog.find("play_2").event());
        assertEquals(1, catalog.find("play_12").rerollTokens());
    }
}
