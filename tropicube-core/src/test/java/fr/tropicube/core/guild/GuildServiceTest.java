package fr.tropicube.core.guild;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuildServiceTest {
    @Test void weeklyKeyIsIsoAndSuccessionDelayIsThirtyDays() {
        assertTrue(GuildService.weekKey().matches("\\d{4}-W\\d{2}"));
        assertEquals(30, GuildService.OWNER_INACTIVITY_DAYS);
    }
}
