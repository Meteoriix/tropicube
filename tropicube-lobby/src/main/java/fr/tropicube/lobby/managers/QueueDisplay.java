package fr.tropicube.lobby.managers;

import fr.tropicube.docker.model.InstanceMode;

import java.util.Objects;

/** Pure player-facing queue description derived from published template metadata. */
record QueueDisplay(String gameType, Kind kind, String format) {
    enum Kind { QUICK_PLAY, RANKED, BETA, CUSTOM }

    QueueDisplay {
        gameType = gameType == null || gameType.isBlank() ? "UNKNOWN" : gameType;
        Objects.requireNonNull(kind, "kind");
        format = format == null || format.isBlank() ? null : format.trim();
    }

    static QueueDisplay from(LobbyServerManager.TemplateInfo template) {
        if (template == null) return new QueueDisplay("UNKNOWN", Kind.QUICK_PLAY, null);
        String game = template.gameType() == null || template.gameType().isBlank()
                ? template.type() : template.gameType();
        Kind kind;
        if (template.mode() == InstanceMode.CUSTOM) kind = Kind.CUSTOM;
        else if ("BETA".equalsIgnoreCase(template.type())) kind = Kind.BETA;
        else if (template.mode().isRanked()) kind = Kind.RANKED;
        else kind = Kind.QUICK_PLAY;
        String format = template.gameFormat();
        if ((format == null || format.isBlank()) && template.mode() == InstanceMode.RANKED_4V4) format = "4v4";
        if ((format == null || format.isBlank()) && template.mode() == InstanceMode.RANKED_8V8) format = "8v8";
        return new QueueDisplay(game, kind, format);
    }

    String icon() {
        return switch (kind) {
            case QUICK_PLAY -> "🌴";
            case RANKED -> "⚔";
            case BETA -> "🧪";
            case CUSTOM -> "⭐";
        };
    }

    String decorate(String localizedGameName) {
        String base = localizedGameName + " <dark_gray>•</dark_gray> " + icon();
        return format == null ? base : base + " <dark_gray>•</dark_gray> " + format;
    }
}
