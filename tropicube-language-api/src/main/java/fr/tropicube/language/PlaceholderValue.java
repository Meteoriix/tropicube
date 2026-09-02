package fr.tropicube.language;

import net.kyori.adventure.text.Component;

import java.util.Objects;

/** A typed value that can safely be inserted into a localized MiniMessage template. */
public sealed interface PlaceholderValue permits PlaceholderValue.Plain, PlaceholderValue.Rich {

    /** Creates an escaped, unformatted value suitable for user-controlled text and scalar data. */
    static PlaceholderValue plain(Object value) {
        return new Plain(String.valueOf(value));
    }

    /** Creates a trusted Adventure component while preserving its visual formatting. */
    static PlaceholderValue component(Component value) {
        return new Rich(Objects.requireNonNull(value, "value"));
    }

    record Plain(String value) implements PlaceholderValue {
        public Plain {
            Objects.requireNonNull(value, "value");
        }
    }

    record Rich(Component value) implements PlaceholderValue {
        public Rich {
            Objects.requireNonNull(value, "value");
        }
    }
}
