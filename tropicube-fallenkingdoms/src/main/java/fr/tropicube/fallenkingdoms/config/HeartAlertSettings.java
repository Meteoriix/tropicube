package fr.tropicube.fallenkingdoms.config;

/** Validated presentation settings for an allied heart under attack. */
public record HeartAlertSettings(String sound, float volume, float pitch, int soundCooldownTicks,
                                 int durationTicks, int flashIntervalTicks) {
    public HeartAlertSettings {
        if (sound == null || !sound.matches("[a-z0-9._-]+:[a-z0-9/._-]+") || volume <= 0 || pitch <= 0 || soundCooldownTicks < 0
                || durationTicks <= 0 || flashIntervalTicks <= 0) {
            throw new IllegalArgumentException("heart-alert: son, volume, pitch et délais invalides");
        }
    }
}
