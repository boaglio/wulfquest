package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AGENTS.md §20.6 — the AnimationValidator. */
class AnimationValidatorTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final PlayerData RULES = DB.load("entities/player", PlayerData.class);

    private static Map<String, Set<String>> realFrames() {
        Map<String, Set<String>> m = new HashMap<>();
        new SpriteRepository(DB).get("player").anchors().forEach((k, v) -> m.put(k, new HashSet<>(v.keySet())));
        return m;
    }

    private static PlayerData with(String sprite, String animation, PlayerData.Animation value) {
        Map<String, PlayerData.Animation> animations = new HashMap<>(RULES.animations());
        animations.put(animation, value);
        return new PlayerData(RULES.schemaVersion(), RULES.id(), RULES.displayName(), sprite, RULES.collisionBox(),
                RULES.speed(), RULES.lives(), RULES.spawn(), RULES.death(), RULES.sabre(), animations);
    }

    private static PlayerData.Animation ticks(int ticksPerFrame, boolean gaitVariants, String... frames) {
        return new PlayerData.Animation(List.of(frames), ticksPerFrame, true, PlayerData.Animation.TICKS, gaitVariants);
    }

    private static PlayerData.Animation phases(int ticksPerFrame, String... frames) {
        return new PlayerData.Animation(List.of(frames), ticksPerFrame, false, PlayerData.Animation.SABRE_PHASE, true);
    }

    private static DataException failure(PlayerData player, Map<String, Set<String>> frames) {
        DataException e = catchThrowableOfType(DataException.class, () -> AnimationValidator.check(player, frames));
        assertThat(e).as("expected a DataException").isNotNull();
        return e;
    }

    @Test
    void theShippedPlayerIsValid() {
        AnimationValidator.check(RULES, new SpriteRepository(DB));
    }

    @Test
    void aMissingFrameNamesTheAnimationAndIndex() {
        DataException e = failure(with("player", "walk_side", ticks(5, false, "side_walk0", "side_hop")), realFrames());
        assertThat(e.pointer()).isEqualTo("/animations/walk_side/frames/1");
        assertThat(e.detail()).contains("side_hop");
    }

    @Test
    void aSideFrameWithoutAMirrorIsReported() {
        Map<String, Set<String>> frames = realFrames();
        frames.put("side_x", Set.of());
        DataException e = failure(with("player", "walk_side", ticks(5, false, "side_x")), frames);
        assertThat(e.pointer()).isEqualTo("/animations/walk_side/frames/0");
        assertThat(e.detail()).contains("side_x_l");
    }

    @Test
    void aHeldTickAnimationMustBeASingleFrame() {
        DataException e = failure(with("player", "die", ticks(0, false, "die0", "die1")), realFrames());
        assertThat(e.pointer()).isEqualTo("/animations/die/ticksPerFrame");
    }

    @Test
    void anUnknownSpriteIsReported() {
        DataException e = catchThrowableOfType(DataException.class,
                () -> AnimationValidator.check(with("ghost", "die", RULES.animation("die")), new SpriteRepository(DB)));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/sprite");
    }

    @Test
    void aSabrePhaseAnimationHasExactlyThreeFrames() {
        DataException e = failure(with("player", "swing_side", phases(0, "side_swing0", "side_swing1")), realFrames());
        assertThat(e.pointer()).isEqualTo("/animations/swing_side/frames");
        assertThat(e.detail()).contains("windup, strike, recover");
    }

    @Test
    void aSabrePhaseAnimationIsNotTickTimed() {
        DataException e = failure(with("player", "swing_side", phases(4, "side_swing0", "side_swing1", "side_swing2")),
                realFrames());
        assertThat(e.pointer()).isEqualTo("/animations/swing_side/ticksPerFrame");
    }

    @Test
    void theStrikeFrameNeedsAHandAnchor() {
        Map<String, Set<String>> frames = realFrames();
        frames.put("side_swing1", Set.of());
        DataException e = failure(RULES, frames);
        assertThat(e.pointer()).isEqualTo("/animations/swing_side/frames/1");
        assertThat(e.detail()).contains("side_swing1").contains("hand");
    }

    @Test
    void aMissingGaitVariantIsReported() {
        Map<String, Set<String>> frames = realFrames();
        frames.remove("side_swing2_walk3");
        DataException e = failure(RULES, frames);
        assertThat(e.pointer()).isEqualTo("/animations/swing_side/frames/2");
        assertThat(e.detail()).contains("side_swing2_walk3");
    }

    @Test
    void gaitVariantsAreOnlyForSwings() {
        DataException e = failure(with("player", "walk_side", ticks(5, true, "side_walk0")), realFrames());
        assertThat(e.pointer()).isEqualTo("/animations/walk_side/gaitVariants");
    }
}
