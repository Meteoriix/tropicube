package fr.tropicube.core.clan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ClanServiceTest {
    @Test void weeklyKeyIsIsoAndSuccessionDelayIsThirtyDays() {
        assertTrue(ClanService.weekKey().matches("\\d{4}-W\\d{2}"));
        assertEquals(30, ClanService.OWNER_INACTIVITY_DAYS);
    }
}
