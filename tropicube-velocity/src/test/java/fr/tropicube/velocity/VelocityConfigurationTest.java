package fr.tropicube.velocity;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VelocityConfigurationTest {

    @Test
    void bundledTemplatesAllowSeveralConcurrentInstances() throws IOException {
        ConfigurationNode config = loadBundledConfig();

        for (String templateId : new String[]{"lobby", "sheepwars"}) {
            ConfigurationNode template = config.node("templates", templateId);
            assertEquals(1, template.node("min-instances").getInt());
            assertEquals(5, template.node("max-instances").getInt());
            assertTrue(template.node("max-instances").getInt()
                    > template.node("min-instances").getInt());
        }
    }

    private ConfigurationNode loadBundledConfig() throws IOException {
        var resource = getClass().getResource("/config.yml");
        assertNotNull(resource);
        return YamlConfigurationLoader.builder().url(resource).build().load();
    }
}
