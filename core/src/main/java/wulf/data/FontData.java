package wulf.data;

import java.util.List;
import java.util.Map;

/** Bound view of {@code data/art/font/font.json} (AGENTS.md §10.4). */
public record FontData(int schemaVersion, Map<String, Set> sets) {

    public FontData {
        sets = Map.copyOf(sets);
    }

    public record Size(int w, int h) {
    }

    /** One font size: glyph masks plus metrics. */
    public record Set(
            String name,
            Size size,
            int advance,
            int lineHeight,
            Map<String, Integer> legend,
            Map<String, List<String>> glyphs,
            String fallback) {

        public Set {
            legend = Map.copyOf(legend);
            glyphs = Map.copyOf(glyphs);
        }
    }
}
