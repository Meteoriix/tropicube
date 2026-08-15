package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnderdogBonusAllocatorTest {

    @Test
    void oneMissingPlayerCreatesOnlyOneSharedPool() {
        assertEquals(List.of(1, 1, 1, 0, 0, 0, 0),
                UnderdogBonusAllocator.allocate(7, 1, 3, 2));
    }

    @Test
    void perPlayerCapPreventsExtremeCompensation() {
        assertEquals(List.of(2), UnderdogBonusAllocator.allocate(1, 1, 3, 2));
        assertEquals(List.of(2, 2), UnderdogBonusAllocator.allocate(2, 3, 3, 2));
    }

    @Test
    void emptyOrBalancedTeamsReceiveNothing() {
        assertEquals(List.of(), UnderdogBonusAllocator.allocate(0, 1, 3, 2));
        assertEquals(List.of(), UnderdogBonusAllocator.allocate(4, 0, 3, 2));
    }
}
