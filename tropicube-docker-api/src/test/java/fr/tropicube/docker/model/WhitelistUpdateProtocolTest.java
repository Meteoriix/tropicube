package fr.tropicube.docker.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitelistUpdateProtocolTest {

    @Test
    void roundTripsRequestsAndResults() {
        UUID hostId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        String request = "PROXY:" + WhitelistUpdateProtocol.requestCommand(hostId, true, requestId, "Tropico_7");
        var parsedRequest = WhitelistUpdateProtocol.parseRequestMessage(request).orElseThrow();
        assertEquals(hostId, parsedRequest.hostId());
        assertEquals(requestId, parsedRequest.requestId());
        assertEquals("Tropico_7", parsedRequest.target());
        assertTrue(parsedRequest.add());

        String result = "SHEEPWARS:" + WhitelistUpdateProtocol.resultCommand(hostId, requestId);
        assertEquals(requestId, WhitelistUpdateProtocol.parseResultMessage(result).orElseThrow().requestId());
    }

    @Test
    void rejectsMalformedMessages() {
        assertFalse(WhitelistUpdateProtocol.parseRequestMessage("PROXY:HOST_WHITELIST:bad").isPresent());
        assertFalse(WhitelistUpdateProtocol.parseResultMessage("SHEEPWARS:HOST_WHITELIST_RESULT:bad").isPresent());
    }
}
