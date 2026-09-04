package fr.tropicube.sheepwars.util;

import fr.tropicube.sheepwars.game.GameMap;
import fr.tropicube.sheepwars.game.GameTeam;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Load SheepWars maps from configuration and validate their key points. */
public class MapsUtil {

    public static List<GameMap> loadMaps(ConfigurationSection section, World world) {
        List<GameMap> maps = new ArrayList<>();

        for(String mapKey : section.getKeys(false)) {
            if(mapKey.equals("lobby") || !mapKey.startsWith("map_") || !section.isConfigurationSection(mapKey)) continue;

            ConfigurationSection mapSection = section.getConfigurationSection(mapKey);

            if(mapSection == null || !mapSection.getBoolean("enabled")) continue;

            GameMap gameMap = new GameMap();
            gameMap.setName(mapSection.getString("name"));
            gameMap.setVoidLimit(mapSection.getInt("void_limit"));
            gameMap.addTeamSpawns(GameTeam.RED,  loadSpawns(mapSection, world, "spawns.red"));
            gameMap.addTeamSpawns(GameTeam.BLUE,  loadSpawns(mapSection, world, "spawns.blue"));
            gameMap.setPowerUpSpawns(loadPowerUpSpawns(mapSection, world));

            if (gameMap.isNotReady()) continue;
            maps.add(gameMap);
        }

        return maps;
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

    /** Loads up to eight floating target locations from powerups.target1 ... target8. */
    public static List<Location> loadPowerUpSpawns(ConfigurationSection section, World world) {
        List<Location> locations = new ArrayList<>();
        ConfigurationSection targets = section.getConfigurationSection("powerups");
        if (targets == null) return locations;
        for (int index = 1; index <= 8; index++) {
            Location location = loadLocation(targets, world, "target" + index);
            if (location != null) locations.add(location);
        }
        return locations;
    }
}
