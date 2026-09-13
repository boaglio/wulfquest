package wulf.data;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The {@code AnimationValidator} of AGENTS.md §20.6: every frame an animation
 * names exists in its sprite, every side-view frame has a left-facing mirror,
 * and a zero-tick (held) animation has exactly one frame.
 */
public final class AnimationValidator {

    private static final String SOURCE = "data/entities/player.json";

    /** Frames whose names start with this are side views, drawn mirrored when facing left (§11.4). */
    public static final String SIDE_PREFIX = "side_";
    public static final String MIRROR_SUFFIX = "_l";

    private AnimationValidator() {
    }

    public static void check(PlayerData player, SpriteRepository sprites) {
        if (!sprites.all().containsKey(player.sprite())) {
            throw new DataException(SOURCE, "/sprite",
                    "names sprite '" + player.sprite() + "', which is not in art/sprites/index.json");
        }
        SpriteData sprite = sprites.get(player.sprite());
        Set<String> frames = new LinkedHashSet<>();
        for (SpriteData.Frame f : sprite.frames()) {
            frames.add(f.id());
        }
        frames.addAll(sprite.mirror().keySet());
        check(player, frames);
    }

    /** The rules, against an explicit set of available frame ids (mirrors included). */
    public static void check(PlayerData player, Set<String> availableFrames) {
        for (Map.Entry<String, PlayerData.Animation> e : new TreeMap<>(player.animations()).entrySet()) {
            String at = "/animations/" + e.getKey();
            List<String> names = e.getValue().frames();
            for (int i = 0; i < names.size(); i++) {
                String id = names.get(i);
                if (!availableFrames.contains(id)) {
                    throw new DataException(SOURCE, at + "/frames/" + i,
                            "names frame '" + id + "', which sprite '" + player.sprite() + "' does not have");
                }
                if (id.startsWith(SIDE_PREFIX) && !availableFrames.contains(id + MIRROR_SUFFIX)) {
                    throw new DataException(SOURCE, at + "/frames/" + i,
                            "is a side-view frame with no left-facing mirror '" + id + MIRROR_SUFFIX + "' in sprite '"
                                    + player.sprite() + "'");
                }
            }
            if (e.getValue().ticksPerFrame() == 0 && names.size() > 1) {
                throw new DataException(SOURCE, at + "/ticksPerFrame",
                        "is 0 but the animation has " + names.size() + " frames; only a single frame can be held");
            }
        }
    }
}
