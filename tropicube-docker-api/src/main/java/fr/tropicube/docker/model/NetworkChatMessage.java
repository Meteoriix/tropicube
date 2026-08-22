package fr.tropicube.docker.model;

import com.google.gson.Gson;

import java.util.Objects;
import java.util.UUID;

/** Minimal immutable chat payload used for network delivery and short-lived evidence. */
public record NetworkChatMessage(
        String messageId,
        UUID authorId,
        String authorName,
        String body,
        String instanceId,
        String channel,
        long sentAt
) {
    private static final Gson GSON = new Gson();

    public NetworkChatMessage {
        messageId = text(messageId, "messageId", 64);
        Objects.requireNonNull(authorId, "authorId");
        authorName = text(authorName, "authorName", 64);
        body = text(body, "body", 512);
        instanceId = text(instanceId, "instanceId", 64);
        channel = text(channel, "channel", 24);
        if (sentAt <= 0) throw new IllegalArgumentException("sentAt doit être positif");
    }

    public String toJson() { return GSON.toJson(this); }

    public static NetworkChatMessage fromJson(String json) {
        try {
            NetworkChatMessage value = GSON.fromJson(json, NetworkChatMessage.class);
            if (value == null) throw new IllegalArgumentException("Message JSON vide");
            return new NetworkChatMessage(value.messageId, value.authorId, value.authorName,
                    value.body, value.instanceId, value.channel, value.sentAt);
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("Message JSON invalide", error);
        }
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new IllegalArgumentException(field + " doit contenir entre 1 et " + maximum + " caractères");
        }
        return value;
    }
}
