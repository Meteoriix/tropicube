package fr.tropicube.sheepwars.menu;

import fr.tropicube.sheepwars.game.GameMap;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MapVoteTallyTest {
    @Test
    void includesEveryMapAndCountsOneVotePerPlayer() {
        List<GameMap> maps = List.of(map("Dirigeables"), map("Temple"), map("Galions"), map("Steampunk"));
        UUID regular = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        Map<UUID, GameMap> votes = Map.of(regular, maps.get(0), secondPlayer, maps.get(2));

        Map<GameMap, Integer> counts = MapVoteTally.counts(maps, votes);
        MapVoteTally.Standing standing = MapVoteTally.standing(maps, votes);

        assertEquals(4, counts.size());
        assertEquals(1, counts.get(maps.get(0)));
        assertEquals(1, counts.get(maps.get(2)));
        assertEquals(MapVoteTally.Status.TIE, standing.status());
        assertNull(standing.leader());
        assertEquals(1, standing.votes());
    }

    @Test
    void reportsATieAtZeroAndAfterEqualVotes() {
        List<GameMap> maps = List.of(map("Galions"), map("Temple"));
        MapVoteTally.Standing emptyVote = MapVoteTally.standing(maps, Map.of());
        assertEquals(MapVoteTally.Status.TIE, emptyVote.status());
        assertEquals(0, emptyVote.votes());
        assertNull(emptyVote.leader());

        Map<UUID, GameMap> votes = new LinkedHashMap<>();
        votes.put(UUID.randomUUID(), maps.get(0));
        votes.put(UUID.randomUUID(), maps.get(1));
        MapVoteTally.Standing tied = MapVoteTally.standing(maps, votes);
        assertEquals(MapVoteTally.Status.TIE, tied.status());
        assertEquals(1, tied.votes());
    }

    @Test
    void ignoresVotesForMapsOutsideTheCurrentCatalog() {
        GameMap available = map("Galions");
        MapVoteTally.Standing standing = MapVoteTally.standing(List.of(available),
                Map.of(UUID.randomUUID(), map("Retirée")));

        assertEquals(MapVoteTally.Status.LEADER, standing.status());
        assertSame(available, standing.leader());
        assertEquals(0, standing.votes());
    }

    @Test
    void replacingAPlayersChoiceKeepsExactlyOneVote() {
        List<GameMap> maps = List.of(map("Galions"), map("Temple"));
        UUID player = UUID.randomUUID();
        Map<UUID, GameMap> votes = new LinkedHashMap<>();
        votes.put(player, maps.get(0));
        votes.put(player, maps.get(1));

        Map<GameMap, Integer> counts = MapVoteTally.counts(maps, votes);

        assertEquals(0, counts.get(maps.get(0)));
        assertEquals(1, counts.get(maps.get(1)));
    }

    private static GameMap map(String name) {
        GameMap map = new GameMap();
        map.setName(name);
        return map;
    }
}
