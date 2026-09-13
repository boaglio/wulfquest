package wulf.data;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every creature's sprite exists with the frames the renderer draws — two walk
 * frames and their left-facing mirrors — and the death puff exists (AGENTS.md
 * §12.2, §20.6).
 */
public final class CreatureSpriteValidator {

    /** The frames the creature renderer uses, mirrors included. */
    public static final List<String> WALK_FRAMES = List.of("walk0", "walk1", "walk0_l", "walk1_l");
    public static final String PUFF = "puff";
    public static final List<String> PUFF_FRAMES = List.of("f0", "f1");

    private static final String CREATURES = "data/entities/creatures.json";
    private static final String INDEX = "data/art/sprites/index.json";

    private CreatureSpriteValidator() {
    }

    public static void check(CreatureData creatures, SpriteRepository sprites) {
        check(creatures, framesBySprite(sprites));
    }

    /** Sprite name to its frame ids, mirrors included. */
    public static Map<String, Set<String>> framesBySprite(SpriteRepository sprites) {
        Map<String, Set<String>> framesBySprite = new LinkedHashMap<>();
        for (Map.Entry<String, SpriteData> e : sprites.all().entrySet()) {
            Set<String> frames = new LinkedHashSet<>();
            e.getValue().frames().forEach(f -> frames.add(f.id()));
            frames.addAll(e.getValue().mirror().keySet());
            framesBySprite.put(e.getKey(), frames);
        }
        return framesBySprite;
    }

    /** @param framesBySprite sprite name to its frame ids, mirrors included */
    public static void check(CreatureData creatures, Map<String, Set<String>> framesBySprite) {
        List<CreatureData.Species> roster = creatures.creatures();
        for (int i = 0; i < roster.size(); i++) {
            String sprite = roster.get(i).sprite();
            String at = "/creatures/" + i + "/sprite";
            Set<String> frames = framesBySprite.get(sprite);
            if (frames == null) {
                throw new DataException(CREATURES, at, "names sprite '" + sprite + "', which is not in " + INDEX);
            }
            for (String needed : WALK_FRAMES) {
                if (!frames.contains(needed)) {
                    throw new DataException(CREATURES, at,
                            "names sprite '" + sprite + "', which has no frame '" + needed + "'; creatures need " + WALK_FRAMES);
                }
            }
        }
        Set<String> puff = framesBySprite.get(PUFF);
        if (puff == null || !puff.containsAll(PUFF_FRAMES)) {
            throw new DataException(INDEX, "/sprites",
                    "has no '" + PUFF + "' sprite with frames " + PUFF_FRAMES + ", which dying creatures show");
        }
    }
}
