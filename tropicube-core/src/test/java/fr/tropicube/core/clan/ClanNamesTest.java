package fr.tropicube.core.clan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClanNamesTest {
    @Test void acceptsNamesWithSpacesAndAccentsButRejectsMarkupAndWrongLengths() {
        assertTrue(ClanNames.validName(" Les Étoiles "));
        assertTrue(ClanNames.validName("A".repeat(32)));
        for (String name : new String[]{"ab", " ", "A".repeat(33), "<red>Clan", "/clan", "!"})
            assertFalse(ClanNames.validName(name), name);
        assertFalse(ClanNames.validName(null));
    }

    @Test void tagsAreNormalizedAndHaveStrictLimits() {
        assertEquals("ABC12", ClanNames.normalizeTag(" abc12 "));
        assertTrue(ClanNames.validTag("ab"));
        assertTrue(ClanNames.validTag("abcdefgh"));
        for (String tag : new String[]{"a", "abcdefghi", "a b", "éé", "<a>", "!"})
            assertFalse(ClanNames.validTag(tag), tag);
        assertFalse(ClanNames.validTag(null));
    }
}
