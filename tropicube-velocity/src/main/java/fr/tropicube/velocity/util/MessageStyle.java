package fr.tropicube.velocity.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.ansi.ANSIComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Locale;

/** Central visual vocabulary for Velocity messages and technical logs. */
public final class MessageStyle {

    private static final Component NETWORK_PREFIX = Component.text("TROPICUBE", NamedTextColor.GOLD,
                    TextDecoration.BOLD)
            .append(Component.text(" > ", NamedTextColor.DARK_GRAY).decoration(TextDecoration.BOLD, false));
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder()
            .tags(TagResolver.builder()
                    .resolver(StandardTags.defaults())
                    .tag("tc", Tag.inserting(NETWORK_PREFIX))
                    .build())
            .build();

    private MessageStyle() {}

    /** Parses MiniMessage with the Tropicube {@code <tc>} tag. */
    public static Component component(String message) {
        return MINI_MESSAGE.deserialize(message);
    }

    /** Renders a MiniMessage technical log using ANSI when the terminal supports it. */
    public static String log(String scope, String message) {
        return ANSIComponentSerializer.ansi().serialize(component("<tc><dark_gray><bold>"
                + MINI_MESSAGE.escapeTags(scope.toUpperCase(Locale.ROOT)) + "</bold> ></dark_gray> " + message));
    }

    /** Plain fallback used by tests and non-ANSI log consumers. */
    public static String plain(String message) {
        return PlainTextComponentSerializer.plainText().serialize(component(message));
    }
}
