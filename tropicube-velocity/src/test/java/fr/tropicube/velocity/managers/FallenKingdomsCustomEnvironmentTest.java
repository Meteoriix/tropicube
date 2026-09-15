package fr.tropicube.velocity.managers;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FallenKingdomsCustomEnvironmentTest {
    @Test void acceptsOnlyDocumentedOptions(){
        var values=FallenKingdomsCustomEnvironment.parse("FK_COUNTDOWN_SECONDS=30;FK_COMBAT_PROFILE=LEGACY_1_8;FK_ENABLED_KITS=miner,scout");
        assertEquals("30",values.get("FK_COUNTDOWN_SECONDS"));
        assertThrows(IllegalArgumentException.class,()->FallenKingdomsCustomEnvironment.parse("RCON_PASSWORD=stolen"));
        assertThrows(IllegalArgumentException.class,()->FallenKingdomsCustomEnvironment.parse("FK_COUNTDOWN_SECONDS=$(bad)"));
    }
}
