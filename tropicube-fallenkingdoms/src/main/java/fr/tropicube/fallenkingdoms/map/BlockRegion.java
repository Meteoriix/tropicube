package fr.tropicube.fallenkingdoms.map;

import org.bukkit.Location;

/** Inclusive, normalized cuboid used for gameplay authorization. */
public record BlockRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public BlockRegion {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Région inversée");
    }
    public boolean contains(Location location) {
        return location.getBlockX() >= minX && location.getBlockX() <= maxX
                && location.getBlockY() >= minY && location.getBlockY() <= maxY
                && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }
    public boolean contains(Position position) {
        return position.x() >= minX && position.x() <= maxX + 1
                && position.y() >= minY && position.y() <= maxY + 1
                && position.z() >= minZ && position.z() <= maxZ + 1;
    }
    public boolean overlaps(BlockRegion other) {
        return minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY
                && minZ <= other.maxZ && maxZ >= other.minZ;
    }
}
