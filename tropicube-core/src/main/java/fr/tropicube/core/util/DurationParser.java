package fr.tropicube.core.util;

import java.util.Locale;
import java.util.OptionalLong;

/** Parses positive durations expressed in seconds, minutes, hours or days. */
public final class DurationParser {
    private DurationParser() {}

    public static OptionalLong parseSeconds(String input) {
        if (input == null) return OptionalLong.empty();
        String value = input.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return OptionalLong.empty();

        long multiplier = 1;
        char suffix = value.charAt(value.length() - 1);
        if (Character.isLetter(suffix)) {
            multiplier = switch (suffix) {
                case 's' -> 1;
                case 'm' -> 60;
                case 'h' -> 3_600;
                case 'd' -> 86_400;
                default -> -1;
            };
            value = value.substring(0, value.length() - 1);
        }
        if (multiplier < 0 || value.isEmpty()) return OptionalLong.empty();

        try {
            long amount = Long.parseLong(value);
            if (amount <= 0) return OptionalLong.empty();
            return OptionalLong.of(Math.multiplyExact(amount, multiplier));
        } catch (NumberFormatException | ArithmeticException ignored) {
            return OptionalLong.empty();
        }
    }
}
