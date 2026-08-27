package fr.tropicube.docker.model;

import java.util.Optional;
import java.util.UUID;

/** Immutable network contract for a player's cumulative access levels. */
public record PlayerAccessProfile(int vipLevel, int modLevel, long revision) {
    public static final int MAX_VIP_LEVEL = 3;
    public static final int MAX_MOD_LEVEL = 4;
    private static final String KEY_PREFIX = "player:access:";

    public PlayerAccessProfile {
        if (vipLevel < 0 || vipLevel > MAX_VIP_LEVEL) {
            throw new IllegalArgumentException("vipLevel doit être compris entre 0 et " + MAX_VIP_LEVEL);
        }
        if (modLevel < 0 || modLevel > MAX_MOD_LEVEL) {
            throw new IllegalArgumentException("modLevel doit être compris entre 0 et " + MAX_MOD_LEVEL);
        }
        if (revision < 0) throw new IllegalArgumentException("revision doit être positive ou nulle");
    }

    public static PlayerAccessProfile none() {
        return new PlayerAccessProfile(0, 0, 0);
    }

    public static String key(UUID uuid) {
        return KEY_PREFIX + uuid;
    }

    /** Compact, dependency-free Redis representation: vip:mod:revision. */
    public String serialize() {
        return vipLevel + ":" + modLevel + ":" + revision;
    }

    public static Optional<PlayerAccessProfile> parse(String value) {
        if (value == null || value.isBlank()) return Optional.empty();
        String[] fields = value.split(":", -1);
        if (fields.length != 3) return Optional.empty();
        try {
            return Optional.of(new PlayerAccessProfile(
                    Integer.parseInt(fields[0]), Integer.parseInt(fields[1]), Long.parseLong(fields[2])));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
