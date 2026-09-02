package fr.tropicube.language;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateRendererTest {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void insertsPlainValuesWithoutParsingMiniMessage() {
        Component rendered = TemplateRenderer.component(MINI_MESSAGE, "<green>Bonjour {player}",
                PlaceholderValues.of("player", "<red>Nathan"));

        assertEquals("Bonjour <red>Nathan", PLAIN.serialize(rendered));
    }

    @Test
    void insertsRichComponentsWithTheirStyle() {
        Component rendered = TemplateRenderer.component(MINI_MESSAGE, "Équipe : {team}",
                PlaceholderValues.builder().putComponent("team", Component.text("Rouge", NamedTextColor.RED)).build());

        assertEquals("Équipe : Rouge", PLAIN.serialize(rendered));
        assertEquals(NamedTextColor.RED, rendered.children().getLast().color());
    }

    @Test
    void keepsAndReportsMissingValues() {
        List<String> missing = new ArrayList<>();
        Component rendered = TemplateRenderer.component(MINI_MESSAGE, "{known} {missing}",
                PlaceholderValues.of("known", "ok"), missing::add);

        assertEquals("ok {missing}", PLAIN.serialize(rendered));
        assertEquals(List.of("missing"), missing);
    }

    @Test
    void exposesUniquePlaceholderNamesAndRejectsInvalidOnes() {
        assertEquals(Set.of("player", "online_players"),
                TemplateRenderer.placeholders("{player} {online_players} {player}"));
        assertThrows(IllegalArgumentException.class,
                () -> PlaceholderValues.of("Not-valid", "value"));
    }

    @Test
    void adaptsPositionalCallersUsingDeclaredPlaceholderOrder() {
        PlaceholderValues values = PlaceholderValues.ordered(
                "{player} a {balance} pièces ({player})", "Nathan", 42);

        assertEquals(Set.of("player", "balance"), values.asMap().keySet());
        assertEquals(List.of("player", "balance"),
                TemplateRenderer.orderedPlaceholders("{player} a {balance} pièces ({player})"));
        assertThrows(IllegalArgumentException.class,
                () -> PlaceholderValues.ordered("{player}", "Nathan", 42));
    }
}
