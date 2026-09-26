package fr.tropicube.velocity.commands;

import fr.tropicube.language.PlaceholderValues;
import fr.tropicube.velocity.util.MessageStyle;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TropiAdminCommandTest {

    @Test
    void joinsEveryRemainingArgumentIntoTheServerName() {
        assertEquals("Fallen Kingdoms 1v1-ab12cd34", TropiAdminCommand.joinArguments(
                new String[]{"info", "Fallen", "Kingdoms", "1v1-ab12cd34"}, 1));
        assertEquals("ab12cd34", TropiAdminCommand.joinArguments(
                new String[]{"info", "ab12cd34"}, 1));
        assertEquals("My Custom Server", TropiAdminCommand.joinArguments(
                new String[]{"start", "sheepwars", "My", "Custom", "Server"}, 2));
    }

    @Test
    void filtersMultiWordServerSuggestionsFromTheCompleteQuery() {
        assertEquals(List.of("Fallen Kingdoms 1v1-ab12cd34", "Fallen Kingdoms 2v2-ef56ab78"),
                TropiAdminCommand.filterInstanceNameSuggestions(List.of(
                        "Sheepwars-12345678",
                        "Fallen Kingdoms 2v2-ef56ab78",
                        "Fallen Kingdoms 1v1-ab12cd34"),
                        "fallen kingdoms"));
    }

    @Test
    void rendersTheLocalizedWhitelistStatusAsMiniMessage() {
        Component status = MessageStyle.component("<red>Oui");
        Component line = MessageStyle.component("<gray>Whitelist: {whitelist_status}",
                PlaceholderValues.builder().putComponent("whitelist_status", status).build());

        assertEquals("Whitelist: Oui", PlainTextComponentSerializer.plainText().serialize(line));
        assertEquals(NamedTextColor.RED, line.children().getLast().color());
    }
}
