package fr.tropicube.core.progression;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Immutable, versioned mission definitions loaded from missions.yml. */
public record MissionCatalog(int version, List<Mission> daily, List<Mission> weekly) {
    public record Mission(String id, String event, long target, long experience, double currency,
                          int rerollTokens) {}

    public MissionCatalog {
        if (version <= 0) throw new IllegalArgumentException("version de catalogue invalide");
        daily = List.copyOf(daily);
        weekly = List.copyOf(weekly);
        if (daily.size() < 6 || weekly.size() < 4) throw new IllegalArgumentException(
                "Le catalogue doit permettre une rotation et un reroll sans doublon");
    }

    public static MissionCatalog load(InputStream input) {
        if (input == null) throw new IllegalArgumentException("missions.yml absent");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                new InputStreamReader(input, StandardCharsets.UTF_8));
        return new MissionCatalog(yaml.getInt("version"), read(yaml, "daily"), read(yaml, "weekly"));
    }

    private static List<Mission> read(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException(path + " absent du catalogue");
        List<Mission> values = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            String base = path + "." + id;
            String event = yaml.getString(base + ".event", "").trim();
            long target = yaml.getLong(base + ".target");
            long experience = yaml.getLong(base + ".reward-experience");
            double currency = yaml.getDouble(base + ".reward-currency");
            int rerollTokens = yaml.getInt(base + ".reward-reroll-tokens", 0);
            if (!id.matches("[a-z0-9_-]{1,64}") || event.isBlank() || target <= 0 || experience < 0
                    || currency < 0 || rerollTokens < 0 || rerollTokens > 5)
                throw new IllegalArgumentException("Mission invalide: " + id);
            values.add(new Mission(id, event, target, experience, currency, rerollTokens));
        }
        return List.copyOf(values);
    }

    public Mission find(String id) {
        return java.util.stream.Stream.concat(daily.stream(), weekly.stream())
                .filter(mission -> mission.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Mission inconnue: " + id));
    }
}
