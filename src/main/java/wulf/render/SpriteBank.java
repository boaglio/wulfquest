package wulf.render;

import java.util.LinkedHashMap;
import java.util.Map;
import wulf.data.SpriteData;
import wulf.data.SpriteRepository;

/** Every sprite, decoded once at boot. */
public final class SpriteBank {

    private final Map<String, Sprite> sprites;

    public SpriteBank(SpriteRepository repository) {
        Map<String, Sprite> decoded = new LinkedHashMap<>();
        for (Map.Entry<String, SpriteData> e : repository.all().entrySet()) {
            decoded.put(e.getKey(), new Sprite(e.getValue(), "data/art/sprites/" + e.getKey() + ".sprite.json"));
        }
        this.sprites = Map.copyOf(decoded);
    }

    public Sprite get(String name) {
        Sprite s = sprites.get(name);
        if (s == null) {
            throw new IllegalStateException("no sprite '" + name + "'");
        }
        return s;
    }
}
