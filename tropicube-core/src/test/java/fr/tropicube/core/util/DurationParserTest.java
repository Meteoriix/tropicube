package fr.tropicube.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DurationParserTest {
    @Test
    void parsesSupportedUnits() {
        assertEquals(30, DurationParser.parseSeconds("30s").orElseThrow());
        assertEquals(120, DurationParser.parseSeconds("2m").orElseThrow());
        assertEquals(10_800, DurationParser.parseSeconds("3H").orElseThrow());
        assertEquals(172_800, DurationParser.parseSeconds("2d").orElseThrow());
        assertEquals(45, DurationParser.parseSeconds("45").orElseThrow());
    }

    @Test
    void rejectsInvalidNonPositiveAndOverflowingValues() {
        assertTrue(DurationParser.parseSeconds(null).isEmpty());
        assertTrue(DurationParser.parseSeconds("").isEmpty());
        assertTrue(DurationParser.parseSeconds("ten minutes").isEmpty());
        assertTrue(DurationParser.parseSeconds("0m").isEmpty());
        assertTrue(DurationParser.parseSeconds("-2h").isEmpty());
        assertTrue(DurationParser.parseSeconds(Long.MAX_VALUE + "d").isEmpty());
    }
}
