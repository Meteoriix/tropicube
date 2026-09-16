package fr.tropicube.fallenkingdoms.hud;

import fr.tropicube.fallenkingdoms.game.KingdomId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallenKingdomsHudTest {
    @Test void scoreboardKeepsOnlyTeamsAllocatedToTheMatch() {
        Set<KingdomId> active = Set.of(KingdomId.BLUE, KingdomId.RED);
        assertTrue(FallenKingdomsHud.showLine("fk.sb-blue", active));
        assertTrue(FallenKingdomsHud.showLine("fk.sb-red", active));
        assertFalse(FallenKingdomsHud.showLine("fk.sb-green", active));
        assertFalse(FallenKingdomsHud.showLine("fk.sb-yellow", active));
        assertFalse(FallenKingdomsHud.showLine("fk.sb-orange", active));
        assertTrue(FallenKingdomsHud.showLine("fk.sb-phase", active));
    }
}
