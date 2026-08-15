package fr.tropicube.sheepwars.scoreboard;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreboardManagerTest {

    @Test
    void tablistUsesOnlyTheVisibleNameAndTeamColor() {
        Component expected = Component.text("MaskedWolf", NamedTextColor.RED);

        assertEquals(expected, ScoreboardManager.teamColoredName("MaskedWolf", NamedTextColor.RED));
    }
}
