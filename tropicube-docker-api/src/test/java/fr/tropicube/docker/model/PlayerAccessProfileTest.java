package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlayerAccessProfileTest {
    @Test
    void roundTripsRedisContract() {
        var profile = new PlayerAccessProfile(2, 3, 42);
        assertEquals(profile, PlayerAccessProfile.parse(profile.serialize()).orElseThrow());
        assertTrue(PlayerAccessProfile.parse("broken").isEmpty());
    }

    @Test
    void levelsAreCumulativeAndOwnerIsGlobal() {
        AccessPolicy policy = AccessPolicy.defaults();
        assertTrue(policy.hasPermission(new PlayerAccessProfile(2, 0, 1), "tropicube.queue.priority"));
        assertFalse(policy.hasPermission(new PlayerAccessProfile(1, 0, 1), "tropicube.queue.priority"));
        assertTrue(policy.hasPermission(new PlayerAccessProfile(0, 3, 1), "tropicube.admin.diagnostic"));
        assertTrue(policy.hasPermission(new PlayerAccessProfile(0, 4, 1), "minecraft.command.stop"));
    }

    @Test
    void configuredThresholdOverridesTheDefault() {
        AccessPolicy policy = new AccessPolicy(Map.of("tropicube.queue.priority", 3), Map.of());

        assertFalse(policy.hasPermission(new PlayerAccessProfile(2, 0, 1), "tropicube.queue.priority"));
        assertTrue(policy.hasPermission(new PlayerAccessProfile(3, 0, 1), "tropicube.queue.priority"));
    }

    @Test
    void rejectsUnknownPermissionsAndInvalidThresholds() {
        assertThrows(IllegalArgumentException.class,
                () -> new AccessPolicy(Map.of("tropicube.unknown", 1), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new AccessPolicy(Map.of(), Map.of("tropicube.ban", 0)));
    }

    @Test
    void moderationHierarchyOnlyAllowsStrictlyLowerLevels() {
        PlayerAccessProfile administrator = new PlayerAccessProfile(3, 3, 1);

        assertTrue(AccessPolicy.canManageVip(administrator));
        assertTrue(AccessPolicy.canAssignMod(administrator, 2));
        assertFalse(AccessPolicy.canAssignMod(administrator, 3));
        assertFalse(AccessPolicy.canAssignMod(administrator, 4));
    }
}
