package fr.tropicube.fallenkingdoms.map;

import org.bukkit.Location;
import org.bukkit.World;

/** Immutable position independent from a loaded Bukkit world. */
public record Position(double x, double y, double z, float yaw, float pitch) {
    public Location in(World world) { return new Location(world, x, y, z, yaw, pitch); }
}
