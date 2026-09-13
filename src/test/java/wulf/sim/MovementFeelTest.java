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
 * whether Ranger Vale walks like the original: instant, weightless, faster
 * across than up and down, and sliding along walls.
 */
class MovementFeelTest {

    @Test
    void crossesARoomWidthIn171Ticks() {
        Simulation sim = at(OPEN, 1000, 1000);
        run(sim, RIGHT, 171);
        assertThat(Fixed.px(sim.player().xFp()) - 1000).isBetween(255, 257);
        assertThat(sim.player().yFp()).isEqualTo(Fixed.fp(1000));
    }

    @Test
    void crossesARoomHeightIn192Ticks() {
        Simulation sim = at(OPEN, 1000, 1000);
        run(sim, DOWN, 192);
        assertThat(Fixed.px(sim.player().yFp()) - 1000).isBetween(191, 193);
    }

    @Test
    void horizontalIsFasterThanVertical() {
        // §11.2: the asymmetry is what makes the jungle read as a perspective view. Not a bug.
        assertThat(SimFixtures.RULES.speed().xFp()).isGreaterThan(SimFixtures.RULES.speed().yFp());
    }

    @Test
    void thereIsNoInertia() {
        Simulation sim = at(OPEN, 1000, 1000);
        run(sim, RIGHT, 10);
        int x = sim.player().xFp();
        sim.tick(InputState.NONE);
        assertThat(sim.player().xFp()).as("velocity is zero on the tick the key is released").isEqualTo(x);
        assertThat(sim.player().walkTicks()).as("the gait snaps to frame 0 immediately").isZero();
    }

    @Test
    void thereIsNoAcceleration() {
        Simulation sim = at(OPEN, 1000, 1000);
        sim.tick(RIGHT);
        assertThat(sim.player().xFp() - Fixed.fp(1000))
                .as("full speed on the very first tick").isEqualTo(SimFixtures.RULES.speed().xFp());
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
        sim.tick(DOWN_RIGHT);
        int dx = sim.player().xFp() - Fixed.fp(1000);
        int dy = sim.player().yFp() - Fixed.fp(1000);
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
        a.tick(DOWN_RIGHT);
        b.tick(UP_LEFT);
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
        for (int i = 0; i < 20; i++) {
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
}
