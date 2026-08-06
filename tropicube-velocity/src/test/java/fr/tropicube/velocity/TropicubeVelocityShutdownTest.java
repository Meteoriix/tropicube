package fr.tropicube.velocity;

import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TropicubeVelocityShutdownTest {

    @Test
    void removesDynamicServersWhenShutdownSettingIsMissing() {
        assertTrue(TropicubeVelocity.stopDynamicServersOnShutdown(BasicConfigurationNode.root()));
        assertTrue(TropicubeVelocity.stopDynamicServersOnShutdown(null));
    }

    @Test
    void honorsExplicitShutdownSetting() throws SerializationException {
        var config = BasicConfigurationNode.root();

        config.node("shutdown", "stop-dynamic-servers").set(false);
        assertFalse(TropicubeVelocity.stopDynamicServersOnShutdown(config));

        config.node("shutdown", "stop-dynamic-servers").set(true);
        assertTrue(TropicubeVelocity.stopDynamicServersOnShutdown(config));
    }
}
