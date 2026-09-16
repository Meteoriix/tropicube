package fr.tropicube.fallenkingdoms.config;

/** Validated real-time durations used to drive the accelerated Minecraft clock. */
public record WorldCycleSettings(int dayDurationSeconds, int nightDurationSeconds) {
    public WorldCycleSettings {
        if (dayDurationSeconds <= 0 || nightDurationSeconds <= 0)
            throw new IllegalArgumentException("world-cycle: les durées doivent être strictement positives");
    }

    public long timeAt(long elapsedMillis) {
        long dayMillis = dayDurationSeconds * 1_000L;
        long nightMillis = nightDurationSeconds * 1_000L;
        long position = Math.floorMod(elapsedMillis, dayMillis + nightMillis);
        if (position < dayMillis) return position * 12_000L / dayMillis;
        return 12_000L + (position - dayMillis) * 12_000L / nightMillis;
    }

    public boolean isNight(long elapsedMillis) {
        return timeAt(elapsedMillis) >= 12_000L;
    }
}
