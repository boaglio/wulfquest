package wulf.render;

import java.util.LinkedHashMap;
import java.util.Map;
import wulf.data.FontData;

/** The loaded font sets (AGENTS.md §10.4). */
public final class Fonts {

    /** The dense panel font; the only set M1 ships. */
    public static final String SMALL = "small";

    private final Map<String, FontSet> sets;

    public Fonts(String source, FontData data) {
        Map<String, FontSet> built = new LinkedHashMap<>();
        for (Map.Entry<String, FontData.Set> e : data.sets().entrySet()) {
            built.put(e.getKey(), new FontSet(source, e.getKey(), e.getValue()));
        }
        this.sets = Map.copyOf(built);
    }

    public FontSet get(String key) {
        FontSet f = sets.get(key);
        if (f == null) {
            throw new IllegalStateException("no font set '" + key + "' in font.json");
        }
        return f;
    }

    public FontSet small() {
        return get(SMALL);
    }
}
