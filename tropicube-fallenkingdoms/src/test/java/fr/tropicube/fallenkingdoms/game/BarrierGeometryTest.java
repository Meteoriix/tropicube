package fr.tropicube.fallenkingdoms.game;

import fr.tropicube.fallenkingdoms.map.BlockRegion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BarrierGeometryTest {
    @Test void returnsOnlyNearbyWallPointsInsideTheVerticalWindow() {
        BlockRegion region = new BlockRegion(0, 0, 0, 10, 20, 10);
        var points = BarrierGeometry.nearbyWall(region, -2, 10, 5, 8, 2, 4);
        assertFalse(points.isEmpty());
        assertTrue(points.stream().allMatch(point -> point.y() >= 6 && point.y() <= 14));
        assertTrue(points.stream().allMatch(point -> {
            double dx=point.x()+2,dy=point.y()-10,dz=point.z()-5;
            return dx*dx+dy*dy+dz*dz<=64.0001;
        }));
        assertTrue(points.stream().allMatch(point -> point.x() == 0 || point.x() == 11
                || point.z() == 0 || point.z() == 11));
    }

    @Test void returnsNothingWhenTheViewerIsTooFarAway() {
        assertTrue(BarrierGeometry.nearbyWall(new BlockRegion(0, 0, 0, 10, 20, 10),
                100, 10, 100, 8, 2, 4).isEmpty());
    }
}
