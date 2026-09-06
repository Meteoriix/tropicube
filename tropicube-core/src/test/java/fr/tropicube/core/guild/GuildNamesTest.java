package fr.tropicube.core.guild;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuildNamesTest {
    @Test void acceptsNamesWithSpacesAndAccentsButRejectsMarkupAndWrongLengths() {
        assertTrue(GuildNames.validName(" Les Étoiles "));
        assertTrue(GuildNames.validName("A".repeat(32)));
        for (String name : new String[]{"ab", " ", "A".repeat(33), "<red>Guilde", "/guild", "!"})
            assertFalse(GuildNames.validName(name), name);
        assertFalse(GuildNames.validName(null));
    }

    @Test void tagsAreNormalizedAndHaveStrictLimits() {
        assertEquals("ABC12", GuildNames.normalizeTag(" abc12 "));
        assertTrue(GuildNames.validTag("ab"));
        assertTrue(GuildNames.validTag("abcdefgh"));
        for (String tag : new String[]{"a", "abcdefghi", "a b", "éé", "<a>", "!"})
            assertFalse(GuildNames.validTag(tag), tag);
        assertFalse(GuildNames.validTag(null));
    }
}
