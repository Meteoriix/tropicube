package fr.tropicube.velocity.managers;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionRateLimiterTest {
    @Test
    void quarantinesBurstingAddressAndExpiresIt() {
        MutableClock clock = new MutableClock();
        ConnectionRateLimiter limiter = new ConnectionRateLimiter(clock, 2, 10, 1_000, 5_000);
        assertTrue(limiter.allow("127.0.0.1"));
        assertTrue(limiter.allow("127.0.0.1"));
        assertFalse(limiter.allow("127.0.0.1"));
        clock.millis = 5_001;
        assertTrue(limiter.allow("127.0.0.1"));
    }

    private static final class MutableClock extends Clock {
        private long millis = 1;
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(millis); }
        @Override public long millis() { return millis; }
    }
}
