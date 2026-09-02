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
