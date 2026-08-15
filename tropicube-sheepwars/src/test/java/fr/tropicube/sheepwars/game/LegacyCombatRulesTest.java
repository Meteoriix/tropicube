package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyCombatRulesTest {

    @Test
    void restoresLegacySwordAndBowDamage() {
        assertEquals(5.0, LegacyCombatRules.meleeDamage(4.0, true, false, 5.0, 6.0));
        assertEquals(6.0, LegacyCombatRules.meleeDamage(5.0, false, true, 5.0, 6.0));
        assertEquals(8.5, LegacyCombatRules.bowDamage(10.0, 0.85));
    }

    @Test
    void computesLegacyKnockbackAndVerticalCap() {
        var normal = LegacyCombatRules.knockback(0, 0, 0, 1, 0, false, 0.4, 0.8, 0.4);
        assertEquals(0.4, normal.x(), 0.0001);
        assertEquals(0.4, normal.y(), 0.0001);

        var sprint = LegacyCombatRules.knockback(0, 1, 0, 0, 2, true, 0.4, 0.8, 0.4);
        assertEquals(0.8, sprint.z(), 0.0001);
        assertEquals(0.4, sprint.y(), 0.0001);
    }
}
