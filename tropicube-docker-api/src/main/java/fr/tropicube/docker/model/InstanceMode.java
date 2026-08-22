package fr.tropicube.docker.model;

import java.util.Locale;

/** Functional mode of an instance, independent from its Docker template. */
public enum InstanceMode {
    LOBBY,
    QUICK_PLAY,
    RANKED_4V4,
    RANKED_8V8,
    CUSTOM;

    /** Resolves a backward-compatible default from the template and host marker. */
    public static InstanceMode infer(String templateId, boolean customGame) {
        if (customGame) return CUSTOM;
        String normalized = templateId == null ? "" : templateId.toUpperCase(Locale.ROOT);
        return normalized.contains("LOBBY") ? LOBBY : QUICK_PLAY;
    }

    public boolean isRanked() {
        return this == RANKED_4V4 || this == RANKED_8V8;
    }
}
