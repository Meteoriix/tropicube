package fr.tropicube.sheepwars.powerup;

/** Geometry helper preventing fast arrows from skipping a small target between two ticks. */
public final class ArrowPathCollision {
    private ArrowPathCollision() {}

    public static double distanceSquaredToSegment(double startX, double startY, double startZ,
                                                   double endX, double endY, double endZ,
                                                   double targetX, double targetY, double targetZ) {
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared == 0) return squaredDistance(startX, startY, startZ, targetX, targetY, targetZ);
        double projection = ((targetX - startX) * dx + (targetY - startY) * dy + (targetZ - startZ) * dz)
                / lengthSquared;
        double clamped = Math.max(0, Math.min(1, projection));
        return squaredDistance(startX + clamped * dx, startY + clamped * dy, startZ + clamped * dz,
                targetX, targetY, targetZ);
    }

    private static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x1 - x2;
        double dy = y1 - y2;
        double dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
