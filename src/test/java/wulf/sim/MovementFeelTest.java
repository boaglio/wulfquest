package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static wulf.sim.SimFixtures.DOWN;
import static wulf.sim.SimFixtures.DOWN_RIGHT;
import static wulf.sim.SimFixtures.LEFT;
import static wulf.sim.SimFixtures.OPEN;
import static wulf.sim.SimFixtures.RIGHT;
import static wulf.sim.SimFixtures.UP_LEFT;
import static wulf.sim.SimFixtures.at;
import static wulf.sim.SimFixtures.run;

import org.junit.jupiter.api.Test;
import wulf.engine.Fixed;
import wulf.input.InputState;

/**
 * AGENTS.md §22.2 — the movement-feel contract. These are the tests that decide
 * whether Ranger Vale walks like the original: a short run-up and a glide to a
 * stop (§11.3, the original's own inertia), faster across than up and down, and
 * sliding along walls. Speeds are measured at the top of the run-up.
 */
class MovementFeelTest {

    /** Long enough to reach top speed from rest: eight of the original's frames, and some. */
    private static final int RUN_UP = 24;

    @Test
    void atTopSpeedCrossesARoomWidthIn171Ticks() {
        Simulation sim = at(OPEN, 1000, 1000);
        run(sim, RIGHT, RUN_UP);
        sim = rebase(sim);
        run(sim, RIGHT, 171);
        assertThat(Fixed.px(sim.player().xFp()) - 1000).isBetween(255, 257);
        assertThat(sim.player().yFp()).isEqualTo(Fixed.fp(1000));
    }

    @Test
    void atTopSpeedCrossesARoomHeightIn176Ticks() {
        Simulation sim = at(OPEN, 1000, 1000);
        run(sim, DOWN, RUN_UP);
        sim = rebase(sim);
        run(sim, DOWN, Simulation.ROOM_H_PX);
        assertThat(Fixed.px(sim.player().yFp()) - 1000).isBetween(175, 177);
    }

    @Test
    void horizontalIsFasterThanVertical() {
        // §11.2: the asymmetry is what makes the jungle read as a perspective view. Not a bug.
        assertThat(SimFixtures.RULES.speed().xFp()).isGreaterThan(SimFixtures.RULES.speed().yFp());
    }

    @Test
    void itSpeedsUpOverAFewFrames() {
        // §11.3, the original's own numbers: the first frame adds less than one speed step, so
        // nothing moves; within eight frames he is at the full speed of §11.2.
        Simulation sim = at(OPEN, 1000, 1000);
        int frame = SimFixtures.RULES.momentum().ticksPerFrame();
        run(sim, RIGHT, frame);
        assertThat(sim.player().xFp()).as("no movement on the first frame").isEqualTo(Fixed.fp(1000));
        run(sim, RIGHT, 8 * frame);
        int x = sim.player().xFp();
        sim.tick(RIGHT);
        assertThat(sim.player().xFp() - x).as("full speed after the run-up").isEqualTo(SimFixtures.RULES.speed().xFp());
    }

    @Test
    void itGlidesToAStopAfterRelease() {
        // Let go at full speed and he keeps going, slowing a step at a time, then stands still.
        Simulation sim = at(OPEN, 1000, 1000);
        run(sim, RIGHT, RUN_UP);
        int released = sim.player().xFp();
        int lastStep = Integer.MAX_VALUE;
        int ticks = 0;
        while (ticks < 200) {
            int x = sim.player().xFp();
            sim.tick(InputState.NONE);
            ticks++;
            int step = sim.player().xFp() - x;
            assertThat(step).as("never speeds up while gliding, tick %d", ticks).isLessThanOrEqualTo(lastStep);
            lastStep = step;
            if (step == 0) {
                break;
            }
        }
        int glidePx = Fixed.px(sim.player().xFp() - released);
        assertThat(glidePx).as("a real glide, not a skid").isBetween(24, 64);
        assertThat(ticks).as("and it ends").isLessThan(120);
        assertThat(sim.player().walkTicks()).as("the gait stops when he does").isZero();
        int still = sim.player().xFp();
        run(sim, InputState.NONE, 20);
        assertThat(sim.player().xFp()).isEqualTo(still);
    }

    @Test
    void opposingKeysCancel() {
        Simulation sim = at(OPEN, 1000, 1000);
        sim.tick(new InputState(false, false, true, true, false, false, false, false, false, false, false));
        assertThat(sim.player().xFp()).isEqualTo(Fixed.fp(1000));
    }

    @Test
    void diagonalsAreSlightlyFasterButNotDominant() {
        Simulation sim = at(OPEN, 1000, 1000);
        run(sim, DOWN_RIGHT, RUN_UP);
        int x0 = sim.player().xFp();
        int y0 = sim.player().yFp();
        sim.tick(DOWN_RIGHT);
        int dx = sim.player().xFp() - x0;
        int dy = sim.player().yFp() - y0;
        int cardinalX = SimFixtures.RULES.speed().xFp();
        int cardinalY = SimFixtures.RULES.speed().yFp();
        long euclidSquared = (long) dx * dx + (long) dy * dy;
        assertThat(euclidSquared).as("more ground than a straight line").isGreaterThan((long) cardinalX * cardinalX);
        assertThat(dx + dy).as("but not both axes at full speed").isLessThan(cardinalX + cardinalY);
    }

    @Test
    void diagonalsAreExactMirrors() {
        Simulation a = at(OPEN, 1000, 1000);
        Simulation b = at(OPEN, 1000, 1000);
        run(a, DOWN_RIGHT, RUN_UP);
        run(b, UP_LEFT, RUN_UP);
        assertThat(a.player().xFp() - Fixed.fp(1000)).isEqualTo(Fixed.fp(1000) - b.player().xFp());
        assertThat(a.player().yFp() - Fixed.fp(1000)).isEqualTo(Fixed.fp(1000) - b.player().yFp());
    }

    @Test
    void facingPersistsAfterStopping() {
        Simulation sim = at(OPEN, 1000, 1000);
        sim.tick(LEFT);
        run(sim, InputState.NONE, 5);
        assertThat(sim.player().facing()).isEqualTo(Direction8.W);
    }

    @Test
    void slidesDownAVerticalWallAtTheFullVerticalRate() {
        // Wall cells at world cell x = 130, i.e. pixels 1040..1047. The box's right
        // edge is origin + 4, so an origin at 1035 is touching it.
        Simulation sim = at(SimFixtures.verticalWall(130), 1035, 1000);
        run(sim, DOWN_RIGHT, RUN_UP);
        int x = sim.player().xFp();
        for (int i = 1; i <= 20; i++) {
            int y = sim.player().yFp();
            sim.tick(DOWN_RIGHT);
            assertThat(sim.player().xFp()).as("x pinned against the wall, tick %d", i).isEqualTo(x);
            assertThat(sim.player().yFp() - y).as("never stops, full rate, tick %d", i)
                    .isEqualTo(SimFixtures.RULES.speed().yFp());
        }
    }

    @Test
    void slidesAlongAHorizontalWallAtTheFullHorizontalRate() {
        // Wall row at world cell y = 130 (pixels 1040..1047); feet box bottom is origin - 1.
        Simulation sim = at(SimFixtures.horizontalWall(130), 1000, 1040);
        run(sim, DOWN_RIGHT, RUN_UP);
        int y = sim.player().yFp();
        int x = sim.player().xFp();
        sim.tick(DOWN_RIGHT);
        assertThat(sim.player().yFp()).isEqualTo(y);
        assertThat(sim.player().xFp() - x).isEqualTo(SimFixtures.RULES.speed().xFp());
    }

    @Test
    void aSubPixelMoverPinnedAgainstAWallIsStill() {
        // Vertical speed below a pixel a tick, pushing down into a wall: after at most one
        // last fractional step it must not change at all, not even by a fraction.
        Simulation sim = SimFixtures.at(SimFixtures.horizontalWall(130), 1000, 1030);
        for (int i = 0; i < 60; i++) {   // the run-up and the ten pixels to the wall (§11.3)
            sim.tick(InputState.of(0, 1, false, false));
        }
        int y = sim.player().yFp();
        for (int i = 0; i < 20; i++) {
            sim.tick(InputState.of(1, 1, false, false));   // diagonal: scaled below a pixel on y until touching
            assertThat(sim.player().yFp()).as("tick %d", i).isEqualTo(y);
        }
    }

    @Test
    void stopsFlushAgainstAWallAndStaysStable() {
        Simulation sim = at(SimFixtures.verticalWall(130), 1000, 1000);
        run(sim, RIGHT, 60);
        int rightEdge = Fixed.px(sim.player().xFp()) + SimFixtures.RULES.collisionBox().x()
                + SimFixtures.RULES.collisionBox().w() - 1;
        assertThat(rightEdge).as("never inside the wall").isLessThan(1040);
        assertThat(rightEdge).as("and within a pixel of it").isGreaterThanOrEqualTo(1038);
        int x = sim.player().xFp();
        run(sim, RIGHT, 10);
        assertThat(sim.player().xFp()).as("pushing on does not jitter").isEqualTo(x);
    }

    /** The same player, at top speed, as if he were standing at (1000, 1000): crossings measured from there. */
    private static Simulation rebase(Simulation sim) {
        int dxFp = sim.player().xFp() - Fixed.fp(1000);
        int dyFp = sim.player().yFp() - Fixed.fp(1000);
        sim.player().xFp -= dxFp;
        sim.player().yFp -= dyFp;
        return sim;
    }
}
