package fr.tropicube.core.social;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FriendshipRepositoryTest {
    @Test
    void canonicalPairIsIndependentOfArgumentOrder() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");

        assertEquals(FriendshipRepository.Pair.of(first, second), FriendshipRepository.Pair.of(second, first));
    }

    @Test
    void canonicalPairRejectsSelfFriendship() {
        UUID player = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> FriendshipRepository.Pair.of(player, player));
    }
}
