package fr.tropicube.lobby.commands;

import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheepwarsRejoinCommandTest {

    @Test
    void onlyAcceptsTheArgumentlessCanonicalForm() {
        assertTrue(SheepwarsRejoinCommand.acceptsArguments(new String[0]));
        assertFalse(SheepwarsRejoinCommand.acceptsArguments(new String[]{"join"}));
        assertFalse(SheepwarsRejoinCommand.acceptsArguments(new String[]{"anything"}));
    }

    @Test
    void legacySwCommandIsNotDeclared() {
        YamlConfiguration pluginYaml = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/plugin.yml").toFile());
        assertTrue(pluginYaml.isConfigurationSection("commands.rejoin"));
        assertFalse(pluginYaml.isConfigurationSection("commands.sw"));
    }
}
