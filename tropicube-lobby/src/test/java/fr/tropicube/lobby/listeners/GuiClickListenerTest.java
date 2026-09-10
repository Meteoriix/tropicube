package fr.tropicube.lobby.listeners;

import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

import static fr.tropicube.lobby.listeners.GuiClickListener.TypeSelectorAction.NONE;
import static fr.tropicube.lobby.listeners.GuiClickListener.TypeSelectorAction.PUBLIC_INSTANCES;
import static fr.tropicube.lobby.listeners.GuiClickListener.TypeSelectorAction.CHOOSE_MODE;
import static fr.tropicube.lobby.listeners.GuiClickListener.TypeSelectorAction.RANKED;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GuiClickListenerTest {

    @Test
    void routesSupportedGameSelectorClicks() {
        assertEquals(CHOOSE_MODE, GuiClickListener.typeSelectorAction(ClickType.LEFT));
        assertEquals(CHOOSE_MODE, GuiClickListener.typeSelectorAction(ClickType.DOUBLE_CLICK));
        assertEquals(CHOOSE_MODE, GuiClickListener.typeSelectorAction(ClickType.CREATIVE));
        assertEquals(RANKED, GuiClickListener.typeSelectorAction(ClickType.RIGHT));
        assertEquals(RANKED, GuiClickListener.typeSelectorAction(ClickType.SHIFT_RIGHT));
        assertEquals(PUBLIC_INSTANCES, GuiClickListener.typeSelectorAction(ClickType.SHIFT_LEFT));
        assertEquals(PUBLIC_INSTANCES, GuiClickListener.typeSelectorAction(ClickType.MIDDLE));
    }

    @Test
    void ignoresUnrelatedInventoryActions() {
        assertEquals(NONE, GuiClickListener.typeSelectorAction(ClickType.WINDOW_BORDER_LEFT));
        assertEquals(NONE, GuiClickListener.typeSelectorAction(ClickType.WINDOW_BORDER_RIGHT));
        assertEquals(NONE, GuiClickListener.typeSelectorAction(ClickType.NUMBER_KEY));
        assertEquals(NONE, GuiClickListener.typeSelectorAction(ClickType.DROP));
        assertEquals(NONE, GuiClickListener.typeSelectorAction(ClickType.CONTROL_DROP));
        assertEquals(NONE, GuiClickListener.typeSelectorAction(ClickType.SWAP_OFFHAND));
        assertEquals(NONE, GuiClickListener.typeSelectorAction(ClickType.UNKNOWN));
    }
}
