package fr.tropicube.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MessageStyleTest {

    @Test
    void rendersNetworkAndGameBrandsWithoutDecorativeBrackets() {
        assertEquals("TROPICUBE > Opération réussie", MessageStyle.plain("<tc><green>Opération réussie"));
        assertEquals("SHEEPWARS > Partie lancée", MessageStyle.plain("<sw><green>Partie lancée"));
    }

    @Test
    void limitsBoldToBrandsAndExplicitlyBoldText() {
        Component network = MessageStyle.component("<tc><green>Message normal <bold>important</bold>");
        assertBoldState(network, "TROPICUBE", TextDecoration.State.TRUE);
        assertBoldState(network, " > ", TextDecoration.State.FALSE);
        assertBoldState(network, "Message normal ", TextDecoration.State.FALSE);
        assertBoldState(network, "important", TextDecoration.State.TRUE);

        Component sheepWars = MessageStyle.component("<sw><gray>Notification normale");
        assertBoldState(sheepWars, "SHEEPWARS", TextDecoration.State.TRUE);
        assertBoldState(sheepWars, "Notification normale", TextDecoration.State.FALSE);
    }

    @Test
    void technicalLogHasAReadablePlainFallback() {
        String rendered = MessageStyle.log("tc", "redis", "<green>Connexion établie");
        assertFalse(rendered.contains("<green>"));
        assertFalse(rendered.contains("[Tropicube]"));
    }

    @Test
    void rendersEscapedCommandParametersLiterally() {
        assertEquals("Usage : /msg <joueur> <message>",
                MessageStyle.plain("<red>Usage : /msg \\<joueur> \\<message>"));
    }

    @Test
    void rendersBothMiniMessageLineBreakAliases() {
        assertEquals("Première\nDeuxième\nTroisième",
                MessageStyle.plain("<gray>Première<br>Deuxième<newline>Troisième"));
    }

    @Test
    void limitsPlayerChatFormattingToAuthorizedVisualTags() {
        String ordinary = MessageStyle.prepareChat("<red>Texte &a vert", false);
        assertEquals("<red>Texte  vert", PlainTextComponentSerializer.plainText()
                .serialize(MessageStyle.chatComponent(ordinary)));

        String authorized = MessageStyle.prepareChat("<red>Rouge</red><click:run_command:'/op'>Action</click>", true);
        Component rendered = MessageStyle.chatComponent(authorized);
        assertEquals("Rouge<click:run_command:'/op'>Action</click>",
                PlainTextComponentSerializer.plainText().serialize(rendered));
        assertEquals(NamedTextColor.RED, rendered.children().getFirst().color());
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
