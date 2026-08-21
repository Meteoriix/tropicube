package fr.tropicube.lobby.gui;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SocialGUITest {

    @Test
    void friendEntriesKeepTheResolvedProfileRequiredByPlayerHeads() {
        UUID playerId = UUID.randomUUID();
        ResolvableProfile profile = (ResolvableProfile) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{ResolvableProfile.class},
                (proxy, method, arguments) -> null);

        SocialGUI.FriendEntry entry = new SocialGUI.FriendEntry(playerId, "Ami", true, profile);

        assertEquals(playerId, entry.playerId());
        assertEquals("Ami", entry.username());
        assertSame(profile, entry.profile());
    }
}
