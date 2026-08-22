package fr.tropicube.velocity.managers;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/** Sliding-window connection limiter with temporary adaptive quarantines. */
public final class ConnectionRateLimiter {
    private final Clock clock;
    private final int addressLimit;
    private final int globalLimit;
    private final long windowMillis;
    private final long quarantineMillis;
    private final Map<String, Deque<Long>> byAddress = new HashMap<>();
    private final Map<String, Long> quarantinedUntil = new HashMap<>();
    private final Deque<Long> global = new ArrayDeque<>();

    public ConnectionRateLimiter(Clock clock, int addressLimit, int globalLimit,
                                 long windowMillis, long quarantineMillis) {
        this.clock = clock;
        if (addressLimit <= 0 || globalLimit < addressLimit || windowMillis <= 0 || quarantineMillis <= 0) {
            throw new IllegalArgumentException("Configuration anti-bot invalide");
        }
        this.addressLimit = addressLimit;
        this.globalLimit = globalLimit;
        this.windowMillis = windowMillis;
        this.quarantineMillis = quarantineMillis;
    }

    /** Returns whether the attempt is accepted and records it atomically. */
    public synchronized boolean allow(String address) {
        long now = clock.millis();
        purge(global, now);
        quarantinedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        Long blockedUntil = quarantinedUntil.get(address);
        if (blockedUntil != null && blockedUntil > now) return false;

        Deque<Long> attempts = byAddress.computeIfAbsent(address, ignored -> new ArrayDeque<>());
        purge(attempts, now);
        if (attempts.size() >= addressLimit || global.size() >= globalLimit) {
            quarantinedUntil.put(address, now + quarantineMillis);
            return false;
        }
        attempts.addLast(now);
        global.addLast(now);
        if (attempts.isEmpty()) byAddress.remove(address);
        return true;
    }

    public synchronized int quarantinedAddresses() {
        long now = clock.millis();
        quarantinedUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
        return quarantinedUntil.size();
    }

    private void purge(Deque<Long> timestamps, long now) {
        long cutoff = now - windowMillis;
        while (!timestamps.isEmpty() && timestamps.peekFirst() <= cutoff) timestamps.removeFirst();
    }
}
