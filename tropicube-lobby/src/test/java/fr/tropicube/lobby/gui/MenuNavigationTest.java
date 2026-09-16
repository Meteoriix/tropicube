package fr.tropicube.lobby.gui;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class MenuNavigationTest {
    @Test void serverBrowserKeepsNavigationInTheFooterWithBackLeftAndCloseRight() {
        var slots = Set.of(ServerSelectorGUI.SLOT_PREV, ServerSelectorGUI.SLOT_NEXT, ServerSelectorGUI.SLOT_BEST,
                ServerSelectorGUI.SLOT_FILTER, ServerSelectorGUI.SLOT_BACK, ServerSelectorGUI.SLOT_CLOSE);
        assertEquals(6, slots.size());
        assertTrue(slots.stream().allMatch(slot -> slot >= 45 && slot <= 53));
        assertEquals(45, ServerSelectorGUI.SLOT_BACK);
        assertEquals(53, ServerSelectorGUI.SLOT_CLOSE);
    }
    @Test void shopAndLanguageKeepDistinctActionsAndClosingAtTheRightEdge() {
        assertEquals(26, LanguageSelectorGUI.CLOSE_SLOT);
        assertEquals(26, VipShopGUI.HOME_CLOSE_SLOT);
        assertNotEquals(VipShopGUI.HOME_GRADES_SLOT, VipShopGUI.HOME_COSMETICS_SLOT);
        assertEquals(53, VipShopGUI.GRADES_CLOSE_SLOT);
        assertEquals(45, VipShopGUI.GRADES_BACK_SLOT);
    }

    @Test void gameSelectorKeepsBetaAndCustomGamesSeparatedInTheFooter() {
        Map<Integer, String> layout = ServerTypeSelectorGUI.layoutTypes(
                Set.of("SHEEPWARS", "BETA", "FALLENKINGDOMS"));

        assertEquals("BETA", layout.get(ServerTypeSelectorGUI.BETA_SLOT));
        assertEquals(30, ServerTypeSelectorGUI.BETA_SLOT);
        assertEquals(Set.of(11, 15, 30), layout.keySet());
        assertFalse(layout.containsKey(31));
        assertEquals(32, ServerTypeSelectorGUI.CUSTOM_GAME_SLOT);
        assertEquals(1, layout.entrySet().stream()
                .filter(entry -> "BETA".equalsIgnoreCase(entry.getValue()))
                .count());
    }
}
