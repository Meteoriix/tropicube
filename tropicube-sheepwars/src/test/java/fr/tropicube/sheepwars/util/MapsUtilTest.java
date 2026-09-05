package fr.tropicube.sheepwars.util;

import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapsUtilTest {
    @Test
    void loadsEveryNumberedPowerUpCandidateInNumericOrder() {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("powerups.target12.x", 12.0);
        configuration.set("powerups.target1.x", 1.0);
        configuration.set("powerups.target9.x", 9.0);
        configuration.set("powerups.decorative.x", 99.0);
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(),
                new Class<?>[] {World.class}, (_, _, _) -> null);

        var locations = MapsUtil.loadPowerUpSpawns(configuration, world);

        assertEquals(3, locations.size());
        assertEquals(1.0, locations.get(0).getX());
        assertEquals(9.0, locations.get(1).getX());
        assertEquals(12.0, locations.get(2).getX());
    }
}
