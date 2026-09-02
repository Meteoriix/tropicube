package fr.tropicube.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ComponentLinesTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    void splitsMiniMessageBreaksIntoLoreRowsAndKeepsEmptyLines() {
        List<Component> lines = ComponentLines.split(
                MessageStyle.component("<gray>Première<br><br><yellow>Troisième"));

        assertEquals(List.of("Première", "", "Troisième"), lines.stream().map(PLAIN::serialize).toList());
        assertEquals(NamedTextColor.GRAY, lines.getFirst().children().getFirst().color());
        assertEquals(NamedTextColor.YELLOW, lines.getLast().children().getFirst().color());
    }

    @Test
    void supportsRawAndWindowsLineBreaksIncludingTrailingRows() {
        List<Component> lines = ComponentLines.split(Component.text("Une\r\nDeux\n"));

        assertEquals(List.of("Une", "Deux", ""), lines.stream().map(PLAIN::serialize).toList());
    }

    @Test
    void expandsSeveralExistingLoreRowsInOrder() {
        List<Component> lines = ComponentLines.splitAll(List.of(
                Component.text("Une\nDeux"),
                Component.text("Trois")));

        assertEquals(List.of("Une", "Deux", "Trois"), lines.stream().map(PLAIN::serialize).toList());
    }
}
