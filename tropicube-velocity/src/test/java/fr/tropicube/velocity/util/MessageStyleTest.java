package fr.tropicube.velocity.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessageStyleTest {

    @Test
    void rendersTheNetworkBrandWithoutDecorativeBrackets() {
        assertEquals("TROPICUBE > Serveur disponible", MessageStyle.plain("<tc><green>Serveur disponible"));
    }

    @Test
    void limitsBoldToTheBrandAndExplicitTechnicalContext() {
        Component message = MessageStyle.component("<tc><green>Message normal <bold>important</bold>");
        assertBoldState(message, "TROPICUBE", TextDecoration.State.TRUE);
        assertBoldState(message, " > ", TextDecoration.State.FALSE);
        assertBoldState(message, "Message normal ", TextDecoration.State.FALSE);
        assertBoldState(message, "important", TextDecoration.State.TRUE);

        Component log = MessageStyle.component("<tc><dark_gray><bold>PROXY</bold> ></dark_gray> <gray>Message");
        assertBoldState(log, "PROXY", TextDecoration.State.TRUE);
        assertBoldState(log, "Message", TextDecoration.State.FALSE);
    }

    @Test
    void technicalLogDoesNotLeakMiniMessageTags() {
        String rendered = MessageStyle.log("docker", "<yellow>Instance en attente");
        assertFalse(rendered.contains("<yellow>"));
        assertFalse(rendered.contains("[Tropicube]"));
    }

    private static void assertBoldState(Component component, String content, TextDecoration.State expected) {
        TextDecoration.State state = findEffectiveBoldState(component, content, TextDecoration.State.NOT_SET);
        assertNotNull(state, () -> "Texte introuvable : " + content);
        assertEquals(expected, state, () -> "État de gras incorrect pour : " + content);
    }

    private static TextDecoration.State findEffectiveBoldState(
            Component component,
            String content,
            TextDecoration.State inherited
    ) {
        TextDecoration.State local = component.decoration(TextDecoration.BOLD);
        TextDecoration.State effective = local == TextDecoration.State.NOT_SET ? inherited : local;
        if (component instanceof TextComponent textComponent && textComponent.content().equals(content)) {
            return effective;
        }
        for (Component child : component.children()) {
            TextDecoration.State found = findEffectiveBoldState(child, content, effective);
            if (found != null) return found;
        }
        return null;
    }
}
