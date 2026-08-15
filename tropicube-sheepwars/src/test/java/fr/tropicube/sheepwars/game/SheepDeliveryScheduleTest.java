package fr.tropicube.sheepwars.game;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SheepDeliveryScheduleTest {

    private static final UUID FIRST_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void firstDeliveryBecomesDueAfterTheCompleteInterval() {
        SheepDeliverySchedule schedule = new SheepDeliverySchedule(20, List.of(FIRST_PLAYER));

        advance(schedule, 19);
        assertFalse(schedule.isDue(FIRST_PLAYER));
        schedule.advanceSecond();
        assertTrue(schedule.isDue(FIRST_PLAYER));
    }

    @Test
    void missedDeliveryRemainsDueUntilItIsActuallyDelivered() {
        SheepDeliverySchedule schedule = new SheepDeliverySchedule(20, List.of(FIRST_PLAYER));

        advance(schedule, 35);
        assertTrue(schedule.isDue(FIRST_PLAYER));
        schedule.markDelivered(FIRST_PLAYER);
        advance(schedule, 19);
        assertFalse(schedule.isDue(FIRST_PLAYER));
        schedule.advanceSecond();
        assertTrue(schedule.isDue(FIRST_PLAYER));
    }

    @Test
    void playersKeepIndependentDeadlines() {
        SheepDeliverySchedule schedule = new SheepDeliverySchedule(10, List.of(FIRST_PLAYER, SECOND_PLAYER));

        advance(schedule, 10);
        schedule.markDelivered(FIRST_PLAYER);
        advance(schedule, 5);
        schedule.markDelivered(SECOND_PLAYER);
        advance(schedule, 5);

        assertTrue(schedule.isDue(FIRST_PLAYER));
        assertFalse(schedule.isDue(SECOND_PLAYER));
    }

    @Test
    void fullDefaultMatchContainsTwentyNineUsefulPeriodicDeadlines() {
        SheepDeliverySchedule schedule = new SheepDeliverySchedule(20, List.of(FIRST_PLAYER));
        int deliveries = 0;

        for (int elapsedSecond = 1; elapsedSecond < 600; elapsedSecond++) {
            schedule.advanceSecond();
            if (!schedule.isDue(FIRST_PLAYER)) continue;
            deliveries++;
            schedule.markDelivered(FIRST_PLAYER);
        }

        assertEquals(29, deliveries);
    }

    @Test
    void removedPlayerNeverBecomesDueAgain() {
        SheepDeliverySchedule schedule = new SheepDeliverySchedule(1, List.of(FIRST_PLAYER));

        schedule.remove(FIRST_PLAYER);
        schedule.advanceSecond();

        assertFalse(schedule.isDue(FIRST_PLAYER));
    }

    private static void advance(SheepDeliverySchedule schedule, int seconds) {
        for (int second = 0; second < seconds; second++) schedule.advanceSecond();
    }
}
