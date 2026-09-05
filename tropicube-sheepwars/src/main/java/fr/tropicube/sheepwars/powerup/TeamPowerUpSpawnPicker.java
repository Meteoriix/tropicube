package fr.tropicube.sheepwars.powerup;

import java.util.concurrent.ThreadLocalRandom;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** Selects target locations without immediately repeating the previous candidate. */
final class TeamPowerUpSpawnPicker {
    private final RandomGenerator random;
    private int previousIndex = -1;

    TeamPowerUpSpawnPicker() {
        this(ThreadLocalRandom.current());
    }

    TeamPowerUpSpawnPicker(RandomGenerator random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    int pick(int candidateCount) {
        if (candidateCount <= 0) throw new IllegalArgumentException("Au moins un emplacement est requis");
        if (candidateCount == 1) {
            previousIndex = 0;
            return 0;
        }
        boolean hasPrevious = previousIndex >= 0 && previousIndex < candidateCount;
        int selected = random.nextInt(hasPrevious ? candidateCount - 1 : candidateCount);
        if (hasPrevious && selected >= previousIndex) selected++;
        previousIndex = selected;
        return selected;
    }

    void reset() {
        previousIndex = -1;
    }
}
