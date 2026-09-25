package fr.tropicube.lobby.managers;

import fr.tropicube.docker.model.InstanceMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueueDisplayTest {
    @Test
    void rendersQuickPlayWithoutAnInventedFormat() {
        var template = new LobbyServerManager.TemplateInfo(
                "fallenkingdoms", "Fallen Kingdoms", "FALLENKINGDOMS", 30,
                InstanceMode.QUICK_PLAY, "FALLENKINGDOMS", null);

        assertEquals("Fallen Kingdoms <dark_gray>•</dark_gray> 🌴",
                QueueDisplay.from(template).decorate("Fallen Kingdoms"));
    }

    @Test
    void rendersRankedAndBetaWithTheirPublishedFormats() {
        var ranked = new LobbyServerManager.TemplateInfo(
                "sheepwars-ranked-4v4", "Sheepwars Ranked 4v4", "SHEEPWARS", 8,
                InstanceMode.RANKED_4V4, "SHEEPWARS", "4v4");
        var beta = new LobbyServerManager.TemplateInfo(
                "fallenkingdoms-beta-2v2", "Fallen Kingdoms 2v2", "BETA", 30,
                InstanceMode.QUICK_PLAY, "FALLENKINGDOMS", "2v2");

        assertEquals("SheepWars <dark_gray>•</dark_gray> ⚔ <dark_gray>•</dark_gray> 4v4",
                QueueDisplay.from(ranked).decorate("SheepWars"));
        assertEquals("Fallen Kingdoms <dark_gray>•</dark_gray> 🧪 <dark_gray>•</dark_gray> 2v2",
                QueueDisplay.from(beta).decorate("Fallen Kingdoms"));
    }

    @Test
    void supportsCustomAndLegacyTemplateMetadata() {
        var custom = new LobbyServerManager.TemplateInfo(
                "sheepwars", "Sheepwars", "SHEEPWARS", 16,
                InstanceMode.CUSTOM, "SHEEPWARS", null);
        var legacyRanked = new LobbyServerManager.TemplateInfo(
                "sheepwars-ranked-8v8", "Sheepwars Ranked 8v8", "SHEEPWARS", 16,
                InstanceMode.RANKED_8V8);

        assertEquals("SheepWars <dark_gray>•</dark_gray> ⭐",
                QueueDisplay.from(custom).decorate("SheepWars"));
        assertEquals("SheepWars <dark_gray>•</dark_gray> ⚔ <dark_gray>•</dark_gray> 8v8",
                QueueDisplay.from(legacyRanked).decorate("SheepWars"));
    }
}
