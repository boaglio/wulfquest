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
    void theHitboxIsLiveOnSwingTicksThreeToEightOnly() {
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
        assertThat(seen).startsWith(0, 1, 2, 3).contains(11);
        assertThat(live).containsExactly(3, 4, 5, 6, 7, 8);
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
        // Held, the swings chain with no gap, so the player is never not swinging: count the
        // blade instead, which is live only through each swing's ACTIVE ticks.
        int strokes = 0;
        int liveTicks = 0;
        boolean wasLive = false;
        for (int t = 0; t < 120; t++) {
            sim.tick(HOLD_FIRE);
            boolean live = !sim.sabre().isEmpty();
            if (live) {
                liveTicks++;
                if (!wasLive) {
                    strokes++;
                }
            }
            wasLive = live;
        }
        int perSwing = RULES.sabre().totalTicks() + RULES.sabre().holdRepeatTicks();
        assertThat(strokes).as("a stroke every %d ticks while held", perSwing).isEqualTo(120 / perSwing);
        assertThat(liveTicks).as("and the blade lives its full ACTIVE each time")
                .isEqualTo(strokes * RULES.sabre().activeTicks());
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
    void thePlayerMovesAtFullSpeedWhileSwinging() {
        Simulation sim = facingEast();
        sim.tick(InputState.of(1, 0, true, true));
        int x = sim.player().xFp();
        sim.tick(RIGHT);
        assertThat(sim.player().swinging()).isTrue();
        assertThat(sim.player().xFp() - x).isEqualTo(RULES.speed().xFp());
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
