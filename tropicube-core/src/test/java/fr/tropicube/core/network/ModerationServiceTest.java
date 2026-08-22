package fr.tropicube.core.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationServiceTest {
    @Test void retentionConstantsMatchApprovedPolicy() {
        assertEquals(900, ModerationService.CHAT_BUFFER_SECONDS);
        assertEquals(90L * 24 * 60 * 60 * 1000, ModerationService.EVIDENCE_RETENTION_MILLIS);
    }
}
