package fr.tropicube.fallenkingdoms.game;

import fr.tropicube.fallenkingdoms.map.BlockRegion;

import java.util.LinkedHashSet;
import java.util.List;

/** Produces only the nearby portion of a cuboid wall so particle work stays bounded per viewer. */
public final class BarrierGeometry {
    private BarrierGeometry() { }

    public static List<Point> nearbyWall(BlockRegion region, double viewerX, double viewerY, double viewerZ,
                                         int viewDistance, double spacing, int verticalRadius) {
        double maximumDistanceSquared = (double) viewDistance * viewDistance;
        double minimumY = Math.max(region.minY(), viewerY - verticalRadius);
        double maximumY = Math.min(region.maxY() + 1.0, viewerY + verticalRadius);
        if (minimumY > maximumY) return List.of();
        LinkedHashSet<Point> result = new LinkedHashSet<>();
        for (double y = minimumY; y <= maximumY + 0.001; y += spacing) {
            for (double x = region.minX(); x <= region.maxX() + 1.001; x += spacing) {
                addNearby(result, new Point(x, y, region.minZ()), viewerX, viewerY, viewerZ, maximumDistanceSquared);
                addNearby(result, new Point(x, y, region.maxZ() + 1.0), viewerX, viewerY, viewerZ, maximumDistanceSquared);
            }
            for (double z = region.minZ(); z <= region.maxZ() + 1.001; z += spacing) {
                addNearby(result, new Point(region.minX(), y, z), viewerX, viewerY, viewerZ, maximumDistanceSquared);
                addNearby(result, new Point(region.maxX() + 1.0, y, z), viewerX, viewerY, viewerZ, maximumDistanceSquared);
            }
        }
        return List.copyOf(result);
    }

    private static void addNearby(LinkedHashSet<Point> result, Point point, double x, double y, double z,
                                  double maximumDistanceSquared) {
        double dx = point.x() - x;
        double dy = point.y() - y;
        double dz = point.z() - z;
        if (dx * dx + dy * dy + dz * dz <= maximumDistanceSquared) result.add(point);
    }

    public record Point(double x, double y, double z) { }
}
