package wulf.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every sprite in the content database, loaded eagerly through the
 * authoritative index (AGENTS.md §20.4 — never a directory listing, so the
 * jar and the filesystem behave identically).
 */
public final class SpriteRepository {

    private final Map<String, SpriteData> byName;

    public SpriteRepository(JsonDb db) {
        SpriteIndex index = db.load("art/sprites/index", SpriteIndex.class, "sprite_index");
        Map<String, SpriteData> loaded = new LinkedHashMap<>();
        for (String name : index.sprites()) {
            SpriteData data = db.load("art/sprites/" + name + ".sprite", SpriteData.class, "sprite");
            if (!data.name().equals(name)) {
                throw new DataException("data/art/sprites/" + name + ".sprite.json", "/name",
                        "is '" + data.name() + "' but art/sprites/index.json lists this file as '" + name + "'");
            }
            loaded.put(name, data);
        }
        this.byName = Collections.unmodifiableMap(loaded);
    }

    public SpriteData get(String name) {
        SpriteData d = byName.get(name);
        if (d == null) {
            throw new IllegalStateException("no sprite '" + name + "' in art/sprites/index.json");
        }
        return d;
    }

    public Map<String, SpriteData> all() {
        return byName;
    }
}
