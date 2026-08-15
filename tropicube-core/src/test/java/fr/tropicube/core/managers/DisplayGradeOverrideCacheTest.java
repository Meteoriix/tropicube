package fr.tropicube.core.managers;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DisplayGradeOverrideCacheTest {

    @Test
    void retainsAndNormalizesTheVisualGradeIndependentlyFromTheRealGrade() {
        UUID uuid = UUID.randomUUID();
        DisplayGradeOverrideCache cache = new DisplayGradeOverrideCache();

        cache.put(uuid, " MaskedWolf ", " premium ");

        DisplayGradeOverrideCache.DisplayOverride identity = cache.get(uuid).orElseThrow();
        assertEquals("MaskedWolf", identity.name());
        assertEquals("PREMIUM", identity.gradeName());
        assertEquals(identity, cache.resolve(uuid, "RealPlayer", "ADMIN"));
        cache.remove(uuid);
        assertTrue(cache.get(uuid).isEmpty());
        assertEquals(new DisplayGradeOverrideCache.DisplayOverride("RealPlayer", "ADMIN"),
                cache.resolve(uuid, "RealPlayer", "ADMIN"));
    }

    @Test
    void formatsTheTablistNameWithTheSelectedDisplayGrade() {
        PermissionManager.Grade premium = new PermissionManager.Grade(
                "PREMIUM", "Premium", "<light_purple>[Premium] ", "",
                "<light_purple>", 30, true, false, Set.of());

        assertEquals("<light_purple>[Premium] <light_purple>MaskedWolf",
                PermissionManager.formatName(Map.of("PREMIUM", premium), "PREMIUM", "MaskedWolf"));
    }
}
