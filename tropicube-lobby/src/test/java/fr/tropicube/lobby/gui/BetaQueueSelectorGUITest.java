package fr.tropicube.lobby.gui;

import fr.tropicube.language.TemplateRenderer;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BetaQueueSelectorGUITest {

    @Test
    void formattedQueueNameIsInsertedAsAComponentWithoutVisibleMiniMessageTags() {
        MiniMessage miniMessage = MiniMessage.miniMessage();
        var queueName = miniMessage.deserialize("<light_purple>Fallen Kingdoms 1v1</light_purple>");

        var message = TemplateRenderer.component(miniMessage, "<white>File {queue}</white>",
                BetaQueueSelectorGUI.displayNamePlaceholder(queueName));

        assertEquals("File Fallen Kingdoms 1v1",
                PlainTextComponentSerializer.plainText().serialize(message));
        assertTrue(miniMessage.serialize(message).contains("<light_purple>Fallen Kingdoms 1v1"));
        assertEquals("Fallen Kingdoms 1v1", BetaQueueSelectorGUI.plainText(queueName));
    }
}
