package fr.tropicube.docker.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Shared Redis protocol for acknowledged private-game whitelist mutations. */
public final class WhitelistUpdateProtocol {
    private static final String REQUEST_PREFIX = "PROXY:HOST_WHITELIST:";
    private static final String RESULT_PREFIX = "SHEEPWARS:HOST_WHITELIST_RESULT:";

    private WhitelistUpdateProtocol() {
    }

    public static String requestCommand(UUID hostId, boolean add, UUID requestId, String target) {
        Objects.requireNonNull(hostId, "hostId");
        Objects.requireNonNull(requestId, "requestId");
        if (target == null || target.isBlank() || target.contains(":")) {
            throw new IllegalArgumentException("La cible de whitelist est invalide");
        }
        return "HOST_WHITELIST:" + hostId + ':' + (add ? "ADD" : "REMOVE") + ':' + requestId + ':' + target;
    }

    public static Optional<Request> parseRequestMessage(String message) {
        if (message == null || !message.startsWith(REQUEST_PREFIX)) return Optional.empty();
        String[] parts = message.substring(REQUEST_PREFIX.length()).split(":", 4);
        if (parts.length != 4 || parts[3].isBlank()) return Optional.empty();
        try {
            boolean add;
            if ("ADD".equalsIgnoreCase(parts[1])) add = true;
            else if ("REMOVE".equalsIgnoreCase(parts[1])) add = false;
            else return Optional.empty();
            return Optional.of(new Request(UUID.fromString(parts[0]), add,
                    UUID.fromString(parts[2]), parts[3]));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static String resultCommand(UUID hostId, UUID requestId) {
        return "HOST_WHITELIST_RESULT:" + Objects.requireNonNull(hostId, "hostId") + ':'
                + Objects.requireNonNull(requestId, "requestId");
    }

    public static Optional<Result> parseResultMessage(String message) {
        if (message == null || !message.startsWith(RESULT_PREFIX)) return Optional.empty();
        String[] parts = message.substring(RESULT_PREFIX.length()).split(":", 2);
        if (parts.length != 2) return Optional.empty();
        try {
            return Optional.of(new Result(UUID.fromString(parts[0]), UUID.fromString(parts[1])));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public record Request(UUID hostId, boolean add, UUID requestId, String target) {
    }

    public record Result(UUID hostId, UUID requestId) {
    }
}
