package fr.tropicube.sheepwars.game;

import fr.tropicube.language.PlaceholderValue;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MatchSummaryPlaceholdersTest {
    @Test
    void preservesTheFormattedMatchResultAsAComponent() {
        Component survived = Component.text("survivant", NamedTextColor.GREEN);

        var values = MatchSummaryPlaceholders.create(2, 7, survived).asMap();

        assertEquals("2", assertInstanceOf(PlaceholderValue.Plain.class, values.get("kills")).value());
        assertEquals("7", assertInstanceOf(PlaceholderValue.Plain.class, values.get("sheep_thrown")).value());
        PlaceholderValue.Rich result = assertInstanceOf(PlaceholderValue.Rich.class, values.get("match_result"));
        assertEquals(survived, result.value());
    }
}
