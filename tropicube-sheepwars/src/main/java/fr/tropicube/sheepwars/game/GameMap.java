package fr.tropicube.sheepwars.game;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Describes a map, its teams, its spawn points and its objectives. */
public class GameMap {
    private String name;
    private int voidLimit;
    private MapHazards hazards = MapHazards.defaults();
    private final Map<GameTeam, List<Location>> teamSpawns = new EnumMap<>(GameTeam.class);
    private List<Location> powerUpSpawns = List.of();

    public List<Location> getSpawns(GameTeam team) {
        List<Location> spawns = teamSpawns.get(team);
        return spawns != null ? List.copyOf(spawns) : List.of();
    }

    public void addTeamSpawns(GameTeam team, List<Location> locations) {
        teamSpawns.put(team, new ArrayList<>(locations));
    }

    public List<Location> getPowerUpSpawns() {
        return powerUpSpawns;
    }

    public void setPowerUpSpawns(List<Location> powerUpSpawns) {
        this.powerUpSpawns = List.copyOf(powerUpSpawns);
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public int getVoidLimit() { return voidLimit; }

    public void setVoidLimit(int voidLimit) { this.voidLimit = voidLimit; }

    public MapHazards getHazards() { return hazards; }

    public void setHazards(MapHazards hazards) { this.hazards = hazards; }

    public boolean isNotReady() {
        List<Location> red = teamSpawns.get(GameTeam.RED);
        List<Location> blue = teamSpawns.get(GameTeam.BLUE);
        return red == null || red.isEmpty()
                || blue == null || blue.isEmpty() || name == null || name.isEmpty();
    }
}
