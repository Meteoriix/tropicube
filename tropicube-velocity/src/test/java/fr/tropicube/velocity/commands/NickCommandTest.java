package fr.tropicube.velocity.commands;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NickCommandTest {

    @Test
    void acceptsOnlyEnableAndExactOffForms() {
        assertEquals(NickCommand.NickAction.ENABLE, NickCommand.parseAction(new String[0]));
        assertEquals(NickCommand.NickAction.DISABLE, NickCommand.parseAction(new String[]{"off"}));
        assertEquals(NickCommand.NickAction.DISABLE, NickCommand.parseAction(new String[]{"OFF"}));
        assertEquals(NickCommand.NickAction.INVALID, NickCommand.parseAction(new String[]{"random"}));
        assertEquals(NickCommand.NickAction.INVALID, NickCommand.parseAction(new String[]{"off", "extra"}));
    }
}
