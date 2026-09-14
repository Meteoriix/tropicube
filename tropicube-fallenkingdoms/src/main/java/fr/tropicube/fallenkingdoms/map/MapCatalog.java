package fr.tropicube.fallenkingdoms.map;

import fr.tropicube.fallenkingdoms.game.KingdomId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;

/** Loads enabled maps with precise structural validation. */
public final class MapCatalog {
    private final Map<String, MapDefinition> maps;
    private MapCatalog(Map<String, MapDefinition> maps) { this.maps = Map.copyOf(maps); }
    public static MapCatalog load(FileConfiguration config) {
        String world = required(config, "locations.world");
        Position lobby = position(requiredSection(config, "locations.lobby"));
        Position spectator = position(requiredSection(config, "locations.spectator"));
        ConfigurationSection root = requiredSection(config, "locations.maps");
        Map<String, MapDefinition> result = new LinkedHashMap<>();
        for (String id : root.getKeys(false)) {
            if (!id.matches("[a-z0-9-]+")) {
                throw new IllegalArgumentException("locations.maps." + id
                        + ": identifiant attendu en minuscules ([a-z0-9-]+)");
            }
            ConfigurationSection map = requiredSection(root, id); if (!map.getBoolean("enabled")) continue;
            BlockRegion playable = region(requiredSection(map, "playable-region"));
            ConfigurationSection border = requiredSection(map, "border");
            ConfigurationSection borderCenter = requiredSection(border, "center");
            Map<KingdomId, BaseDefinition> bases = new EnumMap<>(KingdomId.class);
            ConfigurationSection kingdoms = requiredSection(map, "kingdoms");
            for (String name : kingdoms.getKeys(false)) { KingdomId kingdom = KingdomId.valueOf(name.toUpperCase(Locale.ROOT)); ConfigurationSection base = requiredSection(kingdoms, name); bases.put(kingdom, new BaseDefinition(position(requiredSection(base, "spawn")), position(requiredSection(base, "heart")), region(requiredSection(base, "base-region")))); }
            Map<Integer, List<KingdomId>> layouts = new HashMap<>(); ConfigurationSection layoutRoot = requiredSection(map, "layouts");
            for (String count : layoutRoot.getKeys(false)) layouts.put(Integer.parseInt(count), layoutRoot.getStringList(count).stream().map(KingdomId::valueOf).toList());
            MapDefinition definition = new MapDefinition(id, required(map, "display-name-key"), world, lobby, spectator, playable,
                    borderCenter.getDouble("x"), borderCenter.getDouble("z"), border.getDouble("initial-size"), layouts, bases);
            result.put(id, definition);
        }
        if (result.isEmpty()) throw new IllegalArgumentException("locations.maps: aucune carte activée");
        return new MapCatalog(result);
    }
    public MapDefinition first() { return maps.values().iterator().next(); }
    public MapDefinition select(String id) {
        if (id == null || id.isBlank()) return first();
        MapDefinition selected = maps.get(id.toLowerCase(Locale.ROOT));
        if (selected == null) throw new IllegalArgumentException("locations.maps." + id + ": carte absente ou désactivée");
        return selected;
    }
    public Collection<MapDefinition> maps() { return maps.values(); }
    private static Position position(ConfigurationSection s) { return new Position(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"), (float)s.getDouble("yaw"), (float)s.getDouble("pitch")); }
    private static BlockRegion region(ConfigurationSection s) { ConfigurationSection a=requiredSection(s,"min"), b=requiredSection(s,"max"); return new BlockRegion(a.getInt("x"),a.getInt("y"),a.getInt("z"),b.getInt("x"),b.getInt("y"),b.getInt("z")); }
    private static ConfigurationSection requiredSection(ConfigurationSection root, String path) { ConfigurationSection section=root.getConfigurationSection(path); if(section==null) throw new IllegalArgumentException(path+": section manquante"); return section; }
    private static String required(FileConfiguration root, String path) { String value=root.getString(path); if(value==null||value.isBlank()) throw new IllegalArgumentException(path+": valeur manquante"); return value; }
    private static String required(ConfigurationSection root, String path) { String value=root.getString(path); if(value==null||value.isBlank()) throw new IllegalArgumentException(path+": valeur manquante"); return value; }
}
