package wulf.data;

import java.util.List;

/** Bound view of {@code data/art/sprites/index.json}. Authoritative: never glob (§20.4). */
public record SpriteIndex(int schemaVersion, List<String> sprites) {

    public SpriteIndex {
        sprites = List.copyOf(sprites);
    }
}
