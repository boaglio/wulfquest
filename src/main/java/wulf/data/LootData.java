package wulf.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bound view of {@code data/entities/loot.json}: score events (AGENTS.md §16.3). */
public record LootData(int schemaVersion, Map<String, Integer> events) {

    public LootData {
        events = Collections.unmodifiableMap(new LinkedHashMap<>(events));
    }

    public int event(String name) {
        Integer v = events.get(name);
        if (v == null) {
            throw new IllegalStateException("loot.json has no score event '" + name + "'");
        }
        return v;
    }
}
