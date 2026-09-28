package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static wulf.sim.SimFixtures.FIRE;
import static wulf.sim.SimFixtures.HOLD_FIRE;
import static wulf.sim.SimFixtures.OPEN;
import static wulf.sim.SimFixtures.RIGHT;
import static wulf.sim.SimFixtures.RULES;
import static wulf.sim.SimFixtures.at;
import static wulf.sim.SimFixtures.run;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.engine.Fixed;
import wulf.input.InputState;

/** AGENTS.md §11.6 and §22.2 — the sabre swing. */
class SabreTest {

    private static Simulation facingEast() {
        Simulation sim = at(OPEN, 1000, 1000);
        sim.tick(RIGHT);
        sim.tick(InputState.NONE);
        return sim;
    }

    @Test
    void theBladeIsLiveOnEveryTickOfAStroke() {
        // The original has no windup and no recovery: while fighting, the blade is out ($AB0E's
        // fighting box, whatever pose the sprite is in). A tapped stroke is live from its first tick.
        Simulation sim = facingEast();
        sim.tick(FIRE);
        List<Integer> live = new ArrayList<>();
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 16 && (sim.player().swinging() || seen.isEmpty()); i++) {
            seen.add(sim.player().swingTick());
            if (!sim.sabre().isEmpty()) {
                live.add(sim.player().swingTick());
            }
            sim.tick(InputState.NONE);
        }
        assertThat(seen).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(live).containsExactly(0, 1, 2, 3, 4, 5, 6, 7);
        assertThat(sim.player().swinging()).isFalse();
    }

    @Test
    void theBladeReachesFourteenPixelsAlongTheFacing() {
        Simulation sim = facingEast();
        sim.tick(FIRE);
        run(sim, InputState.NONE, 3);
        PixelRect r = sim.sabre();
        int cx = Fixed.px(sim.player().xFp()) + RULES.collisionBox().x() + RULES.collisionBox().w() / 2;
        int cy = Fixed.px(sim.player().yFp()) + RULES.collisionBox().y() + RULES.collisionBox().h() / 2;
        assertThat(r).isEqualTo(new PixelRect(cx, cy - 6, 14, 12));
    }

    @Test
    void theBladeIsNotBlockedByScenery() {
        // Standing flush against a wall, the blade still has its full reach (§11.6).
        Simulation sim = at(SimFixtures.verticalWall(130), 1035, 1000);
        sim.tick(RIGHT);
        sim.tick(FIRE);
        run(sim, InputState.NONE, 3);
        assertThat(sim.sabre().w()).isEqualTo(RULES.sabre().reachPx());
    }

    @Test
    void holdingFireKeepsTheSabreWorking() {
        // Reported by the user from the original: keep the key down and the weapon keeps going.
        Simulation sim = SimFixtures.at(SimFixtures.OPEN, 1000, 1000);
        // Held, the strokes chain with no gap and each is live throughout, so the blade never
        // goes dark: the original's fighting mode lasts exactly as long as the key is down.
        List<Integer> dark = new ArrayList<>();
        for (int t = 0; t < 120; t++) {
            sim.tick(HOLD_FIRE);
            if (sim.sabre().isEmpty()) {
                dark.add(t);
            }
        }
        assertThat(dark).as("ticks with no blade while fire was held").isEmpty();
    }

    @Test
    void lettingGoCostsTheCooldown() {
        Simulation sim = SimFixtures.at(SimFixtures.OPEN, 1000, 1000);
        sim.tick(SimFixtures.FIRE);
        run(sim, InputState.NONE, RULES.sabre().totalTicks());
        assertThat(sim.player().swinging()).as("the swing runs to its end").isFalse();
        assertThat(sim.player().cooldown()).isEqualTo(RULES.sabre().cooldownTicks());
        run(sim, InputState.NONE, RULES.sabre().cooldownTicks() - 1);
        sim.tick(SimFixtures.FIRE);
        assertThat(sim.player().swinging()).as("and then it may swing again").isTrue();
    }

    @Test
    void aSecondSwingWaitsForTheCooldown() {
        Simulation sim = facingEast();
        sim.tick(FIRE);
        run(sim, InputState.NONE, RULES.sabre().totalTicks());
        assertThat(sim.player().swinging()).isFalse();
        assertThat(sim.player().cooldown()).isEqualTo(RULES.sabre().cooldownTicks());
        sim.tick(FIRE);
        assertThat(sim.player().swinging()).as("too soon").isFalse();
        run(sim, InputState.NONE, RULES.sabre().cooldownTicks() - 2);
        sim.tick(FIRE);
        assertThat(sim.player().swinging()).as("cooldown over").isTrue();
    }

    @Test
    void fightingSlowsThePlayerToTwoThirds() {
        // The original fights at a steady 2 px a frame ($ADD0) against a walking top speed of 3
        // ($AFC1): 171/256.
        assertThat(RULES.sabre().moveSpeedScaleFp()).isEqualTo(171);
        Simulation sim = facingEast();
        sim.tick(InputState.of(1, 0, true, true));
        int x = sim.player().xFp();
        sim.tick(RIGHT);
        assertThat(sim.player().swinging()).isTrue();
        assertThat(sim.player().xFp() - x).isEqualTo(Fixed.mul(RULES.speed().xFp(), RULES.sabre().moveSpeedScaleFp()));
    }

    @Test
    void diagonalSwingsShiftVertically() {
        Simulation sim = at(OPEN, 1000, 1000);
        sim.tick(SimFixtures.DOWN_RIGHT);
        sim.tick(FIRE);
        run(sim, InputState.NONE, 3);
        int cy = Fixed.px(sim.player().yFp()) + RULES.collisionBox().y() + RULES.collisionBox().h() / 2;
        assertThat(sim.sabre().y()).isEqualTo(cy - 6 + RULES.sabre().diagonalOffsetPx());
    }
}
