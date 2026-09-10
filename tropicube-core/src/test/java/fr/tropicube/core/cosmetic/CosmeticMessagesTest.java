package fr.tropicube.core.cosmetic;

import fr.tropicube.core.menu.NetworkMenuStyle;
import fr.tropicube.core.util.MessageStyle;
import fr.tropicube.language.PlaceholderValues;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticMessagesTest {
    @Test void freeAndRestrictedEntriesRenderWithoutExtraPositionalArgumentsOrVisibleTags() throws Exception {
        try (var input = getClass().getResourceAsStream("/cosmetics.yml")) {
            var catalog = CosmeticCatalog.load(input);
            for (String language : List.of("fr", "en", "de", "es")) {
                try (var resource = getClass().getResourceAsStream("/languages/" + language + ".yml")) {
                    assertNotNull(resource);
                    var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
                    for (String key : yaml.getKeys(true)) {
                        if (!yaml.isConfigurationSection(key)) assertTrue(key.split("[.]").length <= 2,
                                "Deployment language merge cannot accept nested leaf: " + key);
                    }
                    for (var entry : catalog.entries()) {
                        var access = MessageStyle.component(yaml.getString("cosmetics.access-" + entry.access().name().toLowerCase(Locale.ROOT)),
                                PlaceholderValues.of("requirement", entry.requirement()));
                        String accessText = PlainTextComponentSerializer.plainText().serialize(access);
                        assertFalse(accessText.contains("{"));
                        var name = MessageStyle.component(yaml.getString(entry.nameKey()));
                        var lore = MessageStyle.component(yaml.getString("cosmetics.action"), NetworkMenuStyle.actionPlaceholders(name));
                        String visible = PlainTextComponentSerializer.plainText().serialize(lore);
                        assertFalse(visible.contains("<"), visible);
                        assertFalse(visible.contains("{"), visible);
                        assertTrue(visible.contains(PlainTextComponentSerializer.plainText().serialize(name)));
                    }
                }
            }
        }
    }
}
