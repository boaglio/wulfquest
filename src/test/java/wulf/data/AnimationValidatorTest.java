package wulf.data;

import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThat;

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

    private static PlayerData with(String sprite, String animation, PlayerData.Animation value) {
        Map<String, PlayerData.Animation> animations = new HashMap<>(RULES.animations());
        animations.put(animation, value);
        return new PlayerData(RULES.schemaVersion(), RULES.id(), RULES.displayName(), sprite, RULES.collisionBox(),
                RULES.speed(), RULES.lives(), RULES.spawn(), RULES.death(), RULES.sabre(), animations);
    }

    @Test
    void theShippedPlayerIsValid() {
        AnimationValidator.check(RULES, new SpriteRepository(DB));
    }

    @Test
    void aMissingFrameNamesTheAnimationAndIndex() {
        PlayerData bad = with("player", "walk_side",
                new PlayerData.Animation(List.of("side_walk0", "side_hop"), 5, true));
        DataException e = catchThrowableOfType(DataException.class,
                () -> AnimationValidator.check(bad, new SpriteRepository(DB)));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/animations/walk_side/frames/1");
        assertThat(e.detail()).contains("side_hop");
    }

    @Test
    void aSideFrameWithoutAMirrorIsReported() {
        // Every real frame is available, plus one side-view frame that has no "_l" mirror.
        Set<String> available = new HashSet<>(new SpriteRepository(DB).get("player").decode("player").keySet());
        available.add("side_x");
        PlayerData bad = with("player", "walk_side", new PlayerData.Animation(List.of("side_x"), 5, true));
        DataException e = catchThrowableOfType(DataException.class,
                () -> AnimationValidator.check(bad, available));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/animations/walk_side/frames/0");
        assertThat(e.detail()).contains("side_x_l");
    }

    @Test
    void aHeldAnimationMustBeASingleFrame() {
        PlayerData bad = with("player", "die", new PlayerData.Animation(List.of("die0", "die1"), 0, false));
        DataException e = catchThrowableOfType(DataException.class,
                () -> AnimationValidator.check(bad, new SpriteRepository(DB)));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/animations/die/ticksPerFrame");
    }

    @Test
    void anUnknownSpriteIsReported() {
        PlayerData bad = with("ghost", "die", RULES.animation("die"));
        DataException e = catchThrowableOfType(DataException.class,
                () -> AnimationValidator.check(bad, new SpriteRepository(DB)));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/sprite");
    }
}
