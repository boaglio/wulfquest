package wulf.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The {@code AnimationValidator} of AGENTS.md §20.6, for the player's animations:
 *
 * <ul>
 *   <li>every frame an animation names exists in the sprite, and every side-view
 *       frame has a left-facing mirror;</li>
 *   <li>a {@code ticks} animation held at zero ticks has exactly one frame;</li>
 *   <li>a {@code sabrePhase} animation has exactly three frames — windup, strike,
 *       recover — is not tick-timed, and its strike frame has a "hand" anchor;</li>
 *   <li>{@code gaitVariants} belong to swings only, and every frame exists on every
 *       gait of the matching walk.</li>
 * </ul>
 */
public final class AnimationValidator {

    private static final String SOURCE = "data/entities/player.json";

    /** Frames whose names start with this are side views, drawn mirrored when facing left (§11.4). */
    public static final String SIDE_PREFIX = "side_";
    public static final String MIRROR_SUFFIX = "_l";
    /** A swing pose on walking legs: {@code side_swing1} on gait 2 is {@code side_swing1_walk2}. */
    public static final String GAIT_INFIX = "_walk";
    /** The anchor the blade is drawn from. */
    public static final String HAND = "hand";

    private static final String SWING = "swing_";
    private static final String WALK = "walk_";
    private static final int STRIKE = 1;

    private AnimationValidator() {
    }

    public static void check(PlayerData player, SpriteRepository sprites) {
        if (!sprites.all().containsKey(player.sprite())) {
            throw new DataException(SOURCE, "/sprite",
                    "names sprite '" + player.sprite() + "', which is not in art/sprites/index.json");
        }
        Map<String, Set<String>> anchorsByFrame = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, SpriteData.Point>> e : sprites.get(player.sprite()).anchors().entrySet()) {
            anchorsByFrame.put(e.getKey(), e.getValue().keySet());
        }
        check(player, anchorsByFrame);
    }

    /**
     * @param anchorsByFrame every available frame id, mirrors included, mapped to the names of its anchors
     */
    public static void check(PlayerData player, Map<String, Set<String>> anchorsByFrame) {
        for (Map.Entry<String, PlayerData.Animation> e : new TreeMap<>(player.animations()).entrySet()) {
            String name = e.getKey();
            PlayerData.Animation a = e.getValue();
            String at = "/animations/" + name;
            List<String> frames = a.frames();
            for (int i = 0; i < frames.size(); i++) {
                requireFrame(player, anchorsByFrame, frames.get(i), at + "/frames/" + i);
            }

            if (a.sabrePhase()) {
                if (frames.size() != 3) {
                    throw new DataException(SOURCE, at + "/frames", "has " + frames.size()
                            + " frames; a sabrePhase animation has exactly 3: windup, strike, recover (§11.6)");
                }
                if (a.ticksPerFrame() != 0) {
                    throw new DataException(SOURCE, at + "/ticksPerFrame", "is " + a.ticksPerFrame()
                            + ", but a sabrePhase animation is timed by the sabre's phases, so it must be 0");
                }
                requireHand(player, anchorsByFrame, frames.get(STRIKE), at + "/frames/" + STRIKE);
            } else if (a.ticksPerFrame() == 0 && frames.size() > 1) {
                throw new DataException(SOURCE, at + "/ticksPerFrame",
                        "is 0 but the animation has " + frames.size() + " frames; only a single frame can be held");
            }

            if (a.gaitVariants()) {
                if (!name.startsWith(SWING)) {
                    throw new DataException(SOURCE, at + "/gaitVariants",
                            "is true, but only swing_* animations have walking variants");
                }
                String walkName = WALK + name.substring(SWING.length());
                PlayerData.Animation walk = player.animations().get(walkName);
                if (walk == null) {
                    throw new DataException(SOURCE, at + "/gaitVariants",
                            "needs a '" + walkName + "' animation to take its gait from");
                }
                for (int i = 0; i < frames.size(); i++) {
                    for (int g = 0; g < walk.frames().size(); g++) {
                        String variant = frames.get(i) + GAIT_INFIX + g;
                        requireFrame(player, anchorsByFrame, variant, at + "/frames/" + i);
                        if (a.sabrePhase() && i == STRIKE) {
                            requireHand(player, anchorsByFrame, variant, at + "/frames/" + i);
                        }
                    }
                }
            }
        }
    }

    private static void requireFrame(PlayerData player, Map<String, Set<String>> frames, String id, String pointer) {
        if (!frames.containsKey(id)) {
            throw new DataException(SOURCE, pointer,
                    "names frame '" + id + "', which sprite '" + player.sprite() + "' does not have");
        }
        if (id.startsWith(SIDE_PREFIX) && !frames.containsKey(id + MIRROR_SUFFIX)) {
            throw new DataException(SOURCE, pointer, "is a side-view frame with no left-facing mirror '" + id
                    + MIRROR_SUFFIX + "' in sprite '" + player.sprite() + "'");
        }
    }

    private static void requireHand(PlayerData player, Map<String, Set<String>> frames, String id, String pointer) {
        for (String f : id.startsWith(SIDE_PREFIX) ? List.of(id, id + MIRROR_SUFFIX) : List.of(id)) {
            if (!frames.get(f).contains(HAND)) {
                throw new DataException(SOURCE, pointer, "needs strike frame '" + f
                        + "' to have a \"hand\" anchor in sprite '" + player.sprite() + "': the blade is drawn from it");
            }
        }
    }
}
