package fr.tropicube.docker.model;

import com.google.gson.Gson;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Versioned and deduplicatable envelope for Redis Pub/Sub messages. */
public record NetworkEvent(
        UUID eventId,
        int version,
        String type,
        String source,
        long occurredAt,
        String payload
) {
    private static final Gson GSON = new Gson();

    public NetworkEvent {
        Objects.requireNonNull(eventId, "eventId");
        if (version <= 0) throw new IllegalArgumentException("version doit être strictement positive");
        type = requireNonBlank(type, "type");
        source = requireNonBlank(source, "source");
        if (occurredAt <= 0) throw new IllegalArgumentException("occurredAt doit être strictement positif");
        Objects.requireNonNull(payload, "payload");
    }

    public static NetworkEvent create(String type, String source, String payload) {
        return new NetworkEvent(UUID.randomUUID(), 1, type, source, Instant.now().toEpochMilli(), payload);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static NetworkEvent fromJson(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("JSON d'événement vide");
        try {
            NetworkEvent event = GSON.fromJson(json, NetworkEvent.class);
            if (event == null) throw new IllegalArgumentException("JSON d'événement invalide");
            return new NetworkEvent(event.eventId, event.version, event.type, event.source,
                    event.occurredAt, event.payload);
        } catch (RuntimeException error) {
            if (error instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("JSON d'événement invalide", error);
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " est obligatoire");
        return value;
    }
}
