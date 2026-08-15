package fr.tropicube.sheepwars.sheep;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadialDamageTest {

    @Test
    void damageFallsOffLinearlyAndStopsAtRadius() {
        assertEquals(7.0, RadialDamage.calculate(0, 6.5, 7, 1, 0, 0));
        assertEquals(3.5, RadialDamage.calculate(3.25, 6.5, 7, 1, 0, 0));
        assertEquals(0.0, RadialDamage.calculate(6.5, 6.5, 7, 1, 0, 0));
    }

    @Test
    void dpsMultiplierChangesDamageButNotRadius() {
        assertEquals(8.4, RadialDamage.calculate(0, 6.5, 7, 1.2, 0, 0), 0.0001);
        assertEquals(0.0, RadialDamage.calculate(7, 6.5, 7, 1.2, 0, 0));
    }

    @Test
    void sharedDamageBudgetCapsMultiExplosionAbilities() {
        assertEquals(2.0, RadialDamage.calculate(0, 3.5, 3, 1, 6, 8));
        assertEquals(0.0, RadialDamage.calculate(0, 3.5, 3, 1, 8, 8));
        assertEquals(1.6, RadialDamage.calculate(0, 3.5, 3, 1.2, 8, 8), 0.0001);
    }
}
