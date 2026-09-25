package fr.tropicube.sheepwars.scoreboard;

import fr.tropicube.core.ui.ScoreboardTemplate;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Builds the complete client-side representation of configured SheepWars scoreboard lines. */
final class ScoreboardLineRenderer {
    private static final int HIGHEST_SCORE = 15;
    private static final Component BLANK_DISPLAY = Component.text("\u00A0");

    private ScoreboardLineRenderer() {
    }

    static List<RenderedLine> render(List<ScoreboardTemplate.Line> definitions,
                                     Function<String, Component> localizedLine) {
        Objects.requireNonNull(definitions, "definitions");
        Objects.requireNonNull(localizedLine, "localizedLine");
        List<RenderedLine> rendered = new ArrayList<>(definitions.size());
        int score = HIGHEST_SCORE;
        for (ScoreboardTemplate.Line definition : definitions) {
            boolean blank = definition.blank();
            Component display = blank
                    ? BLANK_DISPLAY
                    : Objects.requireNonNull(localizedLine.apply(definition.key()), definition.key());
            rendered.add(new RenderedLine(
                    "sw_" + score,
                    score--,
                    display,
                    blank ? NumberFormat.blank() : null,
                    blank));
        }
        return List.copyOf(rendered);
    }

    record RenderedLine(String entry, int score, Component display, NumberFormat numberFormat, boolean blank) {
        RenderedLine {
            Objects.requireNonNull(entry, "entry");
            Objects.requireNonNull(display, "display");
        }
    }
}
