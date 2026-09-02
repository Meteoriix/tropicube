package fr.tropicube.language;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Safely renders named placeholders written as {@code {lower_snake_case}}. */
public final class TemplateRenderer {
    private static final Pattern NAMED = Pattern.compile("\\{([a-z][a-z0-9_]*)}");

    private TemplateRenderer() { }

    public static Component component(MiniMessage miniMessage, String template, PlaceholderValues values) {
        return component(miniMessage, template, values, _ -> { });
    }

    /** Renders a component and reports every placeholder that did not receive a value. */
    public static Component component(
            MiniMessage miniMessage,
            String template,
            PlaceholderValues values,
            Consumer<String> missingPlaceholder
    ) {
        Objects.requireNonNull(miniMessage, "miniMessage");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(missingPlaceholder, "missingPlaceholder");

        TagResolver.Builder tags = TagResolver.builder();
        StringBuilder transformed = new StringBuilder(template.length());
        Matcher matcher = NAMED.matcher(template);
        Set<String> missing = new LinkedHashSet<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            PlaceholderValue value = values.asMap().get(name);
            if (value == null) {
                missing.add(name);
                matcher.appendReplacement(transformed, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            tags.tag(name, Tag.inserting(asComponent(value)));
            matcher.appendReplacement(transformed, Matcher.quoteReplacement("<" + name + ">"));
        }
        matcher.appendTail(transformed);
        missing.forEach(missingPlaceholder);
        return miniMessage.deserialize(transformed.toString(), tags.build());
    }

    public static Set<String> placeholders(String template) {
        return Set.copyOf(orderedPlaceholders(template));
    }

    /** Returns distinct placeholders in their first-occurrence order. */
    public static List<String> orderedPlaceholders(String template) {
        Matcher matcher = NAMED.matcher(Objects.requireNonNull(template, "template"));
        Set<String> result = new LinkedHashSet<>();
        while (matcher.find()) result.add(matcher.group(1));
        return List.copyOf(result);
    }

    private static Component asComponent(PlaceholderValue value) {
        return switch (value) {
            case PlaceholderValue.Plain plain -> Component.text(plain.value());
            case PlaceholderValue.Rich rich -> rich.value();
        };
    }
}
