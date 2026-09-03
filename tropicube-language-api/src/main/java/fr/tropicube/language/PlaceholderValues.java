package fr.tropicube.language;

import net.kyori.adventure.text.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable named values supplied to a localized template. */
public final class PlaceholderValues {
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9_]*");
    private static final PlaceholderValues EMPTY = new PlaceholderValues(Map.of());

    private final Map<String, PlaceholderValue> values;

    private PlaceholderValues(Map<String, PlaceholderValue> values) {
        this.values = Map.copyOf(values);
    }

    public static PlaceholderValues empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Adapts positional callers to a named template using the placeholders' declared order.
     * This exists only while callers migrate to explicit names; language resources must keep
     * the same placeholder order in every locale.
     */
    public static PlaceholderValues ordered(String template, Object... arguments) {
        return ordered(template, EMPTY, arguments);
    }

    /**
     * Adapts positional arguments while preserving values supplied independently by the runtime.
     * Placeholders present in {@code defaults} do not consume an argument, regardless of their
     * position in the localized template.
     */
    public static PlaceholderValues ordered(String template, PlaceholderValues defaults, Object... arguments) {
        Objects.requireNonNull(arguments, "arguments");
        Objects.requireNonNull(defaults, "defaults");
        var names = TemplateRenderer.orderedPlaceholders(template).stream()
                .filter(name -> !defaults.values.containsKey(name))
                .toList();
        if (arguments.length > names.size()) {
            throw new IllegalArgumentException("Trop d'arguments pour le modèle : " + arguments.length
                    + " reçus, " + names.size() + " attendus");
        }
        Builder builder = builder();
        defaults.values.forEach(builder::putValue);
        for (int index = 0; index < arguments.length; index++) {
            builder.put(names.get(index), arguments[index]);
        }
        return builder.build();
    }

    public static PlaceholderValues of(String name, Object value) {
        return builder().put(name, value).build();
    }

    public Map<String, PlaceholderValue> asMap() {
        return values;
    }

    public static final class Builder {
        private final Map<String, PlaceholderValue> values = new LinkedHashMap<>();

        public Builder put(String name, Object value) {
            return putValue(name, PlaceholderValue.plain(value));
        }

        public Builder putComponent(String name, Component value) {
            return putValue(name, PlaceholderValue.component(value));
        }

        public Builder putValue(String name, PlaceholderValue value) {
            Objects.requireNonNull(name, "name");
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("Nom de placeholder invalide : " + name);
            }
            values.put(name, Objects.requireNonNull(value, "value"));
            return this;
        }

        public PlaceholderValues build() {
            return values.isEmpty() ? EMPTY : new PlaceholderValues(values);
        }
    }
}
