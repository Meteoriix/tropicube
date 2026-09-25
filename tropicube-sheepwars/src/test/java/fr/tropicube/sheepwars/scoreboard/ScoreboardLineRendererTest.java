package fr.tropicube.sheepwars.scoreboard;

import fr.tropicube.core.ui.ScoreboardTemplate;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScoreboardLineRendererTest {

    @Test
    void everyVariantRendersOneStableBlankRowAfterTheMap() {
        YamlConfiguration resource = YamlConfiguration.loadConfiguration(
                Path.of("src/main/resources/scoreboards.yml").toFile());
        var variants = resource.getConfigurationSection("scoreboards.sheepwars.variants");
        assertTrue(variants != null);

        for (String variant : variants.getKeys(false)) {
            List<Map<?, ?>> configured = variants.getMapList(variant + ".lines");
            List<ScoreboardTemplate.Line> definitions = configured.stream()
                    .map(ScoreboardLineRendererTest::definition)
                    .toList();
            List<ScoreboardLineRenderer.RenderedLine> rendered = ScoreboardLineRenderer.render(
                    definitions, Component::text);

            assertEquals(configured.size(), rendered.size(), variant);
            assertEquals(rendered.size(), new HashSet<>(rendered.stream()
                    .map(ScoreboardLineRenderer.RenderedLine::entry).toList()).size(), variant);
            for (int index = 0; index < rendered.size(); index++) {
                assertEquals(15 - index, rendered.get(index).score(), variant);
            }

            int mapIndex = indexOfKey(definitions, "sw.sb-map");
            assertTrue(mapIndex >= 0, variant);
            ScoreboardLineRenderer.RenderedLine blank = rendered.get(mapIndex + 1);
            assertTrue(blank.blank(), variant);
            assertEquals(Component.text("\u00A0"), blank.display(), variant);
            assertSame(NumberFormat.blank(), blank.numberFormat(), variant);
            rendered.stream().filter(line -> !line.blank())
                    .forEach(line -> assertNull(line.numberFormat(), variant));
        }
    }

    @Test
    void blankRowWritesItsUniqueEntryDisplayAndNumberFormatToPaper() {
        ScoreboardLineRenderer.RenderedLine line = ScoreboardLineRenderer.render(
                List.of(ScoreboardTemplate.Line.empty()), Component::text).getFirst();
        List<String> calls = new ArrayList<>();
        Score score = proxy(Score.class, (proxy, method, arguments) -> {
            calls.add(method.getName() + ":" + arguments[0]);
            return null;
        });
        Objective objective = proxy(Objective.class, (proxy, method, arguments) -> {
            assertEquals("getScore", method.getName());
            assertEquals(line.entry(), arguments[0]);
            return score;
        });

        ScoreboardManager.setLine(objective, line);

        assertEquals(List.of(
                "setScore:" + line.score(),
                "customName:" + line.display(),
                "numberFormat:" + line.numberFormat()), calls);
    }

    private static ScoreboardTemplate.Line definition(Map<?, ?> configured) {
        return Boolean.TRUE.equals(configured.get("blank"))
                ? ScoreboardTemplate.Line.empty()
                : ScoreboardTemplate.Line.localized(String.valueOf(configured.get("key")));
    }

    private static int indexOfKey(List<ScoreboardTemplate.Line> definitions, String key) {
        for (int index = 0; index < definitions.size(); index++) {
            if (key.equals(definitions.get(index).key())) return index;
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
