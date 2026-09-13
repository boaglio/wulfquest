package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static wulf.sim.SimFixtures.OPEN;
import static wulf.sim.SimFixtures.RIGHT;
import static wulf.sim.SimFixtures.RULES;
import static wulf.sim.SimFixtures.at;
import static wulf.sim.SimFixtures.run;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.CollisionWorld;

/** AGENTS.md §11.7 — death, the diorama, and respawn. */
class DeathRespawnTest {

    private static final int DYING = RULES.death().animTicks();
    private static final int DOWN = RULES.death().freezeTicks();

    @Test
    void aHitCostsALifeAndStartsTheDeathSequence() {
        Simulation sim = at(OPEN, 1000, 1000);
        assertThat(sim.player().lives()).isEqualTo(RULES.lives().start());
        assertThat(sim.kill()).isTrue();
        assertThat(sim.player().lives()).isEqualTo(RULES.lives().start() - 1);
        assertThat(sim.player().mode()).isEqualTo(Player.Mode.DYING);
    }

    @Test
    void theSequenceRunsDyingThenDownThenRespawnsInvulnerable() {
        Simulation sim = at(OPEN, 1000, 1000);
        sim.kill();
        run(sim, RIGHT, DYING - 1);
        assertThat(sim.player().mode()).isEqualTo(Player.Mode.DYING);
        assertThat(sim.player().xFp()).as("no movement while dying").isEqualTo(Fixed.fp(1000));
        sim.tick(RIGHT);
        assertThat(sim.player().mode()).isEqualTo(Player.Mode.DOWN);
        run(sim, RIGHT, DOWN);
        assertThat(sim.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(sim.player().invulnTicks()).isEqualTo(RULES.spawn().invulnTicks());
        assertThat(sim.player().xFp()).as("back at the room's entry point").isEqualTo(Fixed.fp(1000));
    }

    @Test
    void invulnerabilityAbsorbsHitsThenWearsOff() {
        Simulation sim = at(OPEN, 1000, 1000);
        sim.kill();
        run(sim, InputState.NONE, DYING + DOWN);
        int lives = sim.player().lives();
        assertThat(sim.kill()).isFalse();
        assertThat(sim.player().lives()).isEqualTo(lives);
        run(sim, InputState.NONE, RULES.spawn().invulnTicks());
        assertThat(sim.kill()).isTrue();
    }

    @Test
    void theBorderStrobesAtTheStartOfDeath() {
        Simulation sim = at(OPEN, 1000, 1000);
        sim.kill();
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            pattern.append(sim.borderAlarm() ? '#' : '.');
            sim.tick(InputState.NONE);
        }
        assertThat(pattern.toString()).isEqualTo("##..##..##..##......");
    }

    @Test
    void theLastLifeEndsTheGame() {
        Simulation sim = at(OPEN, 1000, 1000);
        for (int life = 0; life < RULES.lives().start(); life++) {
            run(sim, InputState.NONE, RULES.spawn().invulnTicks());
            assertThat(sim.kill()).isTrue();
            run(sim, InputState.NONE, DYING + DOWN);
        }
        assertThat(sim.player().lives()).isZero();
        assertThat(sim.player().mode()).isEqualTo(Player.Mode.GAME_OVER);
        long hash = sim.stateHash();
        run(sim, RIGHT, 10);
        assertThat(sim.player().xFp()).isEqualTo(Fixed.fp(1000));
        assertThat(sim.stateHash()).as("only the tick counter advances").isNotEqualTo(hash);
    }

    @Test
    void respawnAtARoomEdgeKeepsValeWhollyOnScreen() {
        // Entered room 9,10 from the west: the entry point straddles the edge, where the
        // flip-screen clip would cut the sprite in half (found by eye in M3).
        Simulation sim = at(OPEN, 9 * Simulation.ROOM_W_PX + 1, 10 * Simulation.ROOM_H_PX + 100);
        assertThat(sim.room()).isEqualTo(new wulf.world.RoomAddress(9, 10));
        sim.kill();
        run(sim, InputState.NONE, DYING + DOWN);
        wulf.data.PlayerData.Margin inside = RULES.spawn().insideRoomPx();
        int x = Fixed.px(sim.player().xFp()) - 9 * Simulation.ROOM_W_PX;
        int y = Fixed.px(sim.player().yFp()) - 10 * Simulation.ROOM_H_PX;
        assertThat(sim.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(sim.room()).isEqualTo(new wulf.world.RoomAddress(9, 10));
        assertThat(x).isBetween(inside.left(), Simulation.ROOM_W_PX - inside.right());
        assertThat(y).isBetween(inside.top(), Simulation.ROOM_H_PX - inside.bottom());
        assertThat(x - inside.left()).as("nudged in only as far as needed").isLessThan(Simulation.CELL_PX);
    }

    @Test
    void aBlockedEntryPointRespawnsAtTheNearestFreeSpot() {
        AtomicBoolean blocked = new AtomicBoolean(false);
        // Once armed, the cells around (1000,1000) become solid, as if the spot were now occupied.
        CollisionWorld world = (gx, gy) -> OPEN.isSolid(gx, gy)
                || (blocked.get() && gx >= 123 && gx <= 126 && gy >= 122 && gy <= 125);
        Simulation sim = at(world, 1000, 1000);
        sim.kill();
        blocked.set(true);
        run(sim, InputState.NONE, DYING + DOWN);
        assertThat(sim.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(Collision.boxBlocked(world, RULES.collisionBox(), sim.player().xFp(), sim.player().yFp()))
                .as("respawned somewhere it can actually stand").isFalse();
        assertThat(sim.room()).isEqualTo(new wulf.world.RoomAddress(3, 5));
    }
}
