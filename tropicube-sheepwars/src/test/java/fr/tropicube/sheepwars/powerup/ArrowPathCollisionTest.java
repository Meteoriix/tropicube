package fr.tropicube.sheepwars.powerup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArrowPathCollisionTest {
    @Test
    void detectsAHitAlongAFastArrowSegment() {
        double distance = ArrowPathCollision.distanceSquaredToSegment(
                0, 0, 0, 10, 0, 0, 5, 0.5, 0);

        assertEquals(0.25, distance, 0.000001);
    }

    @Test
    void clampsTheProjectionOutsideTheSegment() {
        double distance = ArrowPathCollision.distanceSquaredToSegment(
                0, 0, 0, 2, 0, 0, 4, 0, 0);

        assertEquals(4.0, distance, 0.000001);
    }

    @Test
    void handlesAStationaryArrow() {
        double distance = ArrowPathCollision.distanceSquaredToSegment(
                1, 2, 3, 1, 2, 3, 2, 2, 3);

        assertEquals(1.0, distance, 0.000001);
    }
}
