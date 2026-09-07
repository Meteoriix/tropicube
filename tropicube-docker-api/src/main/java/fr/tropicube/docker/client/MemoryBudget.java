package fr.tropicube.docker.client;

import java.util.HashMap;
import java.util.Map;

/** Atomic accounting of container limits, including creations not yet visible in Docker. */
public final class MemoryBudget {
    private final long limitMiB;
    private final Map<String, Long> reservations = new HashMap<>();

    public MemoryBudget(long limitMiB) {
        if (limitMiB <= 0) throw new IllegalArgumentException("docker.memory-budget-mib must be positive: " + limitMiB);
        this.limitMiB = limitMiB;
    }

    /** Rejects new work without disturbing existing reservations, even after a budget reduction. */
    public synchronized void reserve(String id, long memoryMiB) {
        if (memoryMiB <= 0) throw new IllegalArgumentException("memoryMiB must be positive");
        if (reservations.containsKey(id)) throw new IllegalStateException("Duplicate memory reservation: " + id);
        if (memoryMiB > limitMiB - reservedMiB()) throw new CapacityExceededException();
        reservations.put(id, memoryMiB);
    }

    /** Restores actual Docker limits; an already running container must never be omitted. */
    public synchronized void restore(String id, long memoryMiB) {
        if (memoryMiB <= 0) throw new IllegalArgumentException("Unbounded restored container: " + id);
        reservations.put(id, memoryMiB);
    }

    public synchronized void release(String id) { reservations.remove(id); }
    public synchronized long reservedMiB() { return reservations.values().stream().mapToLong(Long::longValue).sum(); }

    /** Temporary capacity refusal; callers may keep their existing matchmaking queue. */
    public static final class CapacityExceededException extends IllegalStateException {
        public CapacityExceededException() { super("Dynamic container memory budget exhausted"); }
    }
}
