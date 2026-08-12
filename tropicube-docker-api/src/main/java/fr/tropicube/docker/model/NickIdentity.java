package fr.tropicube.docker.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Optional;
import java.util.UUID;

/** Shared Redis representation of an active network nickname. */
public record NickIdentity(String name, String skinValue, String skinSignature, String displayGrade) {

    public static final String DEFAULT_DISPLAY_GRADE = "PREMIUM";
    public static final int TTL_SECONDS = 86_400;
    private static final String KEY_PREFIX = "nick:";

    /** Creates a validated nick identity. */
    public NickIdentity {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name is required");
        if (skinValue == null || skinValue.isBlank()) throw new IllegalArgumentException("skinValue is required");
        displayGrade = displayGrade == null || displayGrade.isBlank()
                ? DEFAULT_DISPLAY_GRADE : displayGrade.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Returns the canonical Redis key for a player's active identity. */
    public static String key(UUID uuid) {
        return KEY_PREFIX + uuid;
    }

    /** Serializes the compact, backward-compatible Redis payload. */
    public String toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("n", name);
        object.addProperty("v", skinValue);
        if (skinSignature != null) object.addProperty("s", skinSignature);
        object.addProperty("g", displayGrade);
        return object.toString();
    }

    /** Parses a Redis payload; legacy payloads default to the Premium display grade. */
    public static Optional<NickIdentity> fromJson(String json) {
        if (json == null || json.isBlank()) return Optional.empty();
        try {
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            return Optional.of(new NickIdentity(
                    object.get("n").getAsString(),
                    object.get("v").getAsString(),
                    object.has("s") ? object.get("s").getAsString() : null,
                    object.has("g") ? object.get("g").getAsString() : DEFAULT_DISPLAY_GRADE));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
