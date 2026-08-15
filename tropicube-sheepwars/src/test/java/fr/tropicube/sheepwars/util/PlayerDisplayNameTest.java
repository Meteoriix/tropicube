package fr.tropicube.sheepwars.util;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerDisplayNameTest {

    @Test
    void chatUsesTheIdentityRestoredByNickOffInsteadOfTheStaleConnectionName() {
        assertEquals("MaskedWolf", PlayerDisplayName.resolve(Component.text("MaskedWolf"), "RealPlayer"));
        assertEquals("RealPlayer", PlayerDisplayName.resolve(Component.text("RealPlayer"), "MaskedWolf"));
        assertEquals("RealPlayer", PlayerDisplayName.resolve(Component.empty(), "RealPlayer"));
    }
}
