package fr.tropicube.lobby.gui;

import fr.tropicube.language.TemplateRenderer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsGUITest {
    @Test
    void suppliesExactlyThePlaceholdersRequiredByEveryAutoReplayState() {
        var states = List.of(
                SettingsGUI.autoReplayPresentation(-1),
                SettingsGUI.autoReplayPresentation(0),
                SettingsGUI.autoReplayPresentation(5));

        assertEquals(List.of(
                "lobby.settings-auto-replay-off",
                "lobby.settings-auto-replay-confirm",
                "lobby.settings-auto-replay-on"), states.stream().map(SettingsGUI.AutoReplayPresentation::key).toList());
        assertEquals(List.of(Set.of(), Set.of(), Set.of("remaining_games")), states.stream()
                .map(state -> state.placeholders().asMap().keySet())
                .toList());

        for (String language : List.of("fr", "en", "de", "es")) {
            var catalog = YamlConfiguration.loadConfiguration(Path.of("../tropicube-core/src/main/resources/languages",
                    language + ".yml").toFile());
            for (SettingsGUI.AutoReplayPresentation state : states) {
                assertEquals(TemplateRenderer.placeholders(catalog.getString(state.key())),
                        state.placeholders().asMap().keySet(), language + ": " + state.key());
            }
        }
    }
}
