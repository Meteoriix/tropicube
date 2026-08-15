package fr.tropicube.lobby.utils;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LangHelperTest {

    @Test
    void usesTheIdentityRestoredInTheDisplayNameInsteadOfAStaleProfileName() {
        assertEquals("MaskedWolf", LangHelper.getVisibleName(Component.text("MaskedWolf"), "RealPlayer"));
        assertEquals("RealPlayer", LangHelper.getVisibleName(Component.text("RealPlayer"), "OldNick"));
        assertEquals("RealPlayer", LangHelper.getVisibleName(Component.empty(), "RealPlayer"));
    }
}
