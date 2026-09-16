package fr.tropicube.sheepwars.util;

import fr.tropicube.sheepwars.game.GameMap;
import fr.tropicube.sheepwars.game.MapHazards;
import fr.tropicube.sheepwars.game.GameTeam;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Load SheepWars maps from configuration and validate their key points. */
public class MapsUtil {
    private static final Pattern POWER_UP_TARGET = Pattern.compile("target([1-9][0-9]*)");

    public static List<GameMap> loadMaps(ConfigurationSection section, World world) {
        List<GameMap> maps = new ArrayList<>();

        for(String mapKey : section.getKeys(false)) {
            if(mapKey.equals("lobby") || !mapKey.startsWith("map_") || !section.isConfigurationSection(mapKey)) continue;

            ConfigurationSection mapSection = section.getConfigurationSection(mapKey);

            if(mapSection == null || !mapSection.getBoolean("enabled")) continue;

            GameMap gameMap = new GameMap();
            gameMap.setName(mapSection.getString("name"));
            gameMap.setVoidLimit(mapSection.getInt("void_limit"));
            gameMap.setHazards(loadHazards(mapSection));
            gameMap.addTeamSpawns(GameTeam.RED,  loadSpawns(mapSection, world, "spawns.red"));
            gameMap.addTeamSpawns(GameTeam.BLUE,  loadSpawns(mapSection, world, "spawns.blue"));
            gameMap.setPowerUpSpawns(loadPowerUpSpawns(mapSection, world));

            if (gameMap.isNotReady()) continue;
            maps.add(gameMap);
        }

        return maps;
    }

    /** Loads optional per-map hazards while preserving safe legacy defaults. */
    public static MapHazards loadHazards(ConfigurationSection mapSection) {
        boolean voidKillEnabled = mapSection.getBoolean("hazards.void-kill-enabled", true);
        boolean waterPoisonEnabled = mapSection.getBoolean("hazards.water-poison.enabled", false);
        int durationTicks = mapSection.getInt("hazards.water-poison.duration-ticks", 20);
        int amplifier = mapSection.getInt("hazards.water-poison.amplifier", 0);
        if (durationTicks < 1 || durationTicks > 200) {
            throw new IllegalArgumentException("hazards.water-poison.duration-ticks doit valoir entre 1 et 200");
        }
        if (amplifier < 0 || amplifier > 4) {
            throw new IllegalArgumentException("hazards.water-poison.amplifier doit valoir entre 0 et 4");
        }
        return new MapHazards(voidKillEnabled, waterPoisonEnabled, durationTicks, amplifier);
    }

    public static Location loadLocation(ConfigurationSection section, World world, String path) {
        if (world == null) return null;
        ConfigurationSection sub = section.getConfigurationSection(path);
        if (sub == null) return null;
        return new Location(world,
                sub.getDouble("x"), sub.getDouble("y"), sub.getDouble("z"),
                (float) sub.getDouble("yaw"), (float) sub.getDouble("pitch"));
    }

    // Loads spawn1 to spawn8 from the given path.
    public static List<Location> loadSpawns(ConfigurationSection section, World world, String path) {
        List<Location> locations = new ArrayList<>();
        ConfigurationSection sub = section.getConfigurationSection(path);
        if (sub == null) return locations;
        for (int i = 1; i <= 8; i++) {
            Location loc = loadLocation(sub, world,"spawn" + i);
            if (loc != null) locations.add(loc);
        }
        return locations;
    }

    /** Loads every numbered floating target candidate from powerups.target1, target2, and so on. */
    public static List<Location> loadPowerUpSpawns(ConfigurationSection section, World world) {
        List<Location> locations = new ArrayList<>();
        ConfigurationSection targets = section.getConfigurationSection("powerups");
        if (targets == null) return locations;
        List<String> targetKeys = targets.getKeys(false).stream()
                .filter(key -> POWER_UP_TARGET.matcher(key).matches())
                .sorted(Comparator.comparingInt(MapsUtil::targetIndex))
                .toList();
        for (String targetKey : targetKeys) {
            Location location = loadLocation(targets, world, targetKey);
            if (location != null) locations.add(location);
        }
        return locations;
    }

    private static int targetIndex(String key) {
        var matcher = POWER_UP_TARGET.matcher(key);
        if (!matcher.matches()) throw new IllegalArgumentException("Clé de cible invalide : " + key);
        return Integer.parseInt(matcher.group(1));
    }
}
