package fr.tropicube.sheepwars.game;

import fr.tropicube.language.PlaceholderValues;
import net.kyori.adventure.text.Component;

import java.util.Objects;

/** Builds the typed values inserted into the localized personal match summary. */
final class MatchSummaryPlaceholders {
    private MatchSummaryPlaceholders() { }

    static PlaceholderValues create(int kills, int sheepThrown, Component matchResult) {
        return PlaceholderValues.builder()
                .put("kills", kills)
                .put("sheep_thrown", sheepThrown)
                .putComponent("match_result", Objects.requireNonNull(matchResult, "matchResult"))
                .build();
    }
}
