package fr.tropicube.core.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MenuSessionTest {
    @Test void repeatedClicksCannotStartTwoMutationsInOneRenderedScreen() {
        var session = new MenuSession();
        assertTrue(session.beginAction());
        assertFalse(session.beginAction());
        assertFalse(session.canInteract());
        assertTrue(session.completeAction());
        assertTrue(session.beginAction());
    }
    @Test void completionAfterClosingOrNavigatingNeverReactivatesTheOldScreen() {
        var old = new MenuSession();
        assertTrue(old.beginAction());
        old.close();
        var next = new MenuSession();
        assertFalse(old.completeAction());
        assertFalse(old.isOpen());
        assertFalse(old.beginAction());
        assertTrue(next.canInteract());
        old.close();
        assertFalse(old.completeAction());
    }
}
