package fr.tropicube.core.statistics;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkGameResultTest {
    @Test void resultCopiesCollectionsForSafeAsynchronousPersistence() {
        Set<String> winners = new HashSet<>(Set.of("BLUE"));
        List<NetworkGameResult.PlayerResult> players = new ArrayList<>(List.of(
                new NetworkGameResult.PlayerResult(UUID.randomUUID(), true, false, 1, 0, 1)));
        NetworkGameResult result = new NetworkGameResult(UUID.randomUUID(), "fallenkingdoms", "LAST_KINGDOM", winners, Instant.now(), players);
        winners.clear(); players.clear();
        assertEquals(Set.of("BLUE"), result.winners());
        assertEquals(1, result.players().size());
        assertThrows(UnsupportedOperationException.class, () -> result.winners().add("RED"));
    }
}
