package fr.tropicube.core.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.flattener.FlattenerListener;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Converts embedded text line breaks into the separate components expected by Minecraft item lore. */
public final class ComponentLines {

    private ComponentLines() { }

    /**
     * Splits one component on every line break while retaining its effective Adventure styles.
     * Consecutive and trailing separators deliberately produce empty lore rows.
     */
    public static List<Component> split(Component component) {
        Objects.requireNonNull(component, "component");
        LineCollector collector = new LineCollector();
        ComponentFlattener.basic().flatten(component, collector);
        return collector.finish();
    }

    /** Expands all embedded line breaks from an ordered collection of components. */
    public static List<Component> splitAll(Collection<? extends ComponentLike> components) {
        Objects.requireNonNull(components, "components");
        return components.stream()
                .map(ComponentLike::asComponent)
                .flatMap(component -> split(component).stream())
                .toList();
    }

    private static final class LineCollector implements FlattenerListener {
        private final List<Component> lines = new ArrayList<>();
        private final Deque<Style> styles = new ArrayDeque<>();
        private Component current = Component.empty();

        private LineCollector() {
            styles.push(Style.empty());
        }

        @Override
        public void pushStyle(Style style) {
            styles.push(styles.peek().merge(style, Style.Merge.Strategy.ALWAYS));
        }

        @Override
        public void component(String text) {
            String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
            String[] parts = normalized.split("\n", -1);
            for (int index = 0; index < parts.length; index++) {
                if (!parts[index].isEmpty()) {
                    current = current.append(Component.text(parts[index]).style(styles.peek()));
                }
                if (index < parts.length - 1) {
                    lines.add(current);
                    current = Component.empty();
                }
            }
        }

        @Override
        public void popStyle(Style style) {
            styles.pop();
        }

        private List<Component> finish() {
            lines.add(current);
            return List.copyOf(lines);
        }
    }
}
