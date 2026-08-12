package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NickIdentityTest {

    @Test
    void preservesTheDisplayGradeAndReadsLegacyPayloads() {
        NickIdentity identity = new NickIdentity("MaskedWolf", "skin", "signature", "premium");

        assertEquals(identity, NickIdentity.fromJson(identity.toJson()).orElseThrow());
        assertEquals("PREMIUM", NickIdentity.fromJson("{\"n\":\"Legacy\",\"v\":\"skin\"}")
                .orElseThrow().displayGrade());
        assertEquals("nick:12345678-1234-1234-1234-123456789abc",
                NickIdentity.key(UUID.fromString("12345678-1234-1234-1234-123456789abc")));
        assertTrue(NickIdentity.fromJson("invalid").isEmpty());
    }
}
