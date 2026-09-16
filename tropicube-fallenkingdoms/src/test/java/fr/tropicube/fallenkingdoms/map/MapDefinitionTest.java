package fr.tropicube.fallenkingdoms.map;

import fr.tropicube.fallenkingdoms.game.KingdomId;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MapDefinitionTest {
    @Test void acceptsAnyValidMapAndRejectsMissingLayoutBases() {
        BlockRegion playable = new BlockRegion(-100, 0, -100, 100, 150, 100);
        BaseDefinition blue = new BaseDefinition(new Position(-50, 70, 0, 0, 0),
                new Position(-55, 70, 0, 0, 0), new BlockRegion(-70, 0, -20, -30, 100, 20));
        BaseDefinition red = new BaseDefinition(new Position(50, 70, 0, 0, 0),
                new Position(55, 70, 0, 0, 0), new BlockRegion(30, 0, -20, 70, 100, 20));
        assertDoesNotThrow(() -> new MapDefinition("any-map", "fk.map-any", "world", new Position(0, 80, 0, 0, 0),
                new Position(0, 90, 0, 0, 0), playable, 0, 0, 200,
                Map.of(2, List.of(KingdomId.BLUE, KingdomId.RED)), Map.of(KingdomId.BLUE, blue, KingdomId.RED, red)));
        assertThrows(IllegalArgumentException.class, () -> new MapDefinition("broken", "fk.map-broken", "world",
                new Position(0, 80, 0, 0, 0), new Position(0, 90, 0, 0, 0), playable, 0, 0, 200,
                Map.of(2, List.of(KingdomId.BLUE, KingdomId.RED)), Map.of(KingdomId.BLUE, blue)));
    }

    @Test void acceptsAnEligiblePoolLargerThanTheActiveKingdomCount() {
        BlockRegion playable = new BlockRegion(-100, 0, -100, 100, 150, 100);
        BaseDefinition blue = new BaseDefinition(new Position(-50, 70, 0, 0, 0),
                new Position(-55, 70, 0, 0, 0), new BlockRegion(-70, 0, -20, -30, 100, 20));
        BaseDefinition red = new BaseDefinition(new Position(50, 70, 0, 0, 0),
                new Position(55, 70, 0, 0, 0), new BlockRegion(30, 0, -20, 70, 100, 20));
        BaseDefinition green = new BaseDefinition(new Position(0, 70, 50, 0, 0),
                new Position(0, 70, 55, 0, 0), new BlockRegion(-20, 0, 30, 20, 100, 70));
        assertDoesNotThrow(() -> new MapDefinition("pooled", "fk.map-pooled", "world",
                new Position(0, 80, 0, 0, 0), new Position(0, 90, 0, 0, 0), playable, 0, 0, 200,
                Map.of(2, List.of(KingdomId.BLUE, KingdomId.RED, KingdomId.GREEN)),
                Map.of(KingdomId.BLUE, blue, KingdomId.RED, red, KingdomId.GREEN, green)));
    }
}
