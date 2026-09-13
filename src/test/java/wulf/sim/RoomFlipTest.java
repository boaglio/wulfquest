package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static wulf.sim.SimFixtures.LEFT;
import static wulf.sim.SimFixtures.OPEN;
import static wulf.sim.SimFixtures.RIGHT;
import static wulf.sim.SimFixtures.RULES;
import static wulf.sim.SimFixtures.run;

import org.junit.jupiter.api.Test;
import wulf.engine.Fixed;
import wulf.world.RoomAddress;

/** AGENTS.md §7.5 and §22.2 — flip-screen transitions. */
class RoomFlipTest {

    private static final int FREEZE = 6;

    private static Simulation nearEastEdgeOf(RoomAddress room, int yPx) {
        // The feet-box centre is the origin x (box -5..+4); 6 px short of the edge.
        int x = (room.col() + 1) * Simulation.ROOM_W_PX - 6;
        return Simulation.at(RULES, OPEN, FREEZE, Fixed.fp(x), Fixed.fp(room.row() * Simulation.ROOM_H_PX + yPx));
    }

    @Test
    void walkingOffTheEastEdgeEntersTheNextRoomAtTheSameHeight() {
        Simulation sim = nearEastEdgeOf(new RoomAddress(8, 10), 100);
        int y = sim.player().yFp();
        int ticks = 0;
        while (sim.room().equals(new RoomAddress(8, 10)) && ticks++ < 20) {
            sim.tick(RIGHT);
        }
        assertThat(sim.room()).isEqualTo(new RoomAddress(9, 10));
        assertThat(sim.player().yFp()).as("the cross-edge coordinate is preserved exactly").isEqualTo(y);
        assertThat(sim.freezeRemaining()).isEqualTo(FREEZE);
        assertThat(sim.roomsEntered()).isEqualTo(1);
    }

    @Test
    void theHitchFreezesMovementThenPlayResumes() {
        Simulation sim = nearEastEdgeOf(new RoomAddress(8, 10), 100);
        while (sim.room().col() == 8) {
            sim.tick(RIGHT);
        }
        int x = sim.player().xFp();
        run(sim, RIGHT, FREEZE);
        assertThat(sim.player().xFp()).as("frozen for the hitch").isEqualTo(x);
        assertThat(sim.freezeRemaining()).isZero();
        sim.tick(RIGHT);
        assertThat(sim.player().xFp()).as("moving again").isGreaterThan(x);
    }

    @Test
    void walkingBackReturnsToTheOriginalRoom() {
        Simulation sim = nearEastEdgeOf(new RoomAddress(8, 10), 100);
        int y = sim.player().yFp();
        while (sim.room().col() == 8) {
            sim.tick(RIGHT);
        }
        run(sim, RIGHT, FREEZE);
        int guard = 0;
        while (sim.room().col() == 9 && guard++ < 40) {
            sim.tick(LEFT);
        }
        assertThat(sim.room()).isEqualTo(new RoomAddress(8, 10));
        assertThat(sim.player().yFp()).isEqualTo(y);
        assertThat(sim.roomsEntered()).isEqualTo(2);
    }

    @Test
    void theEdgeOfTheMapIsAWallNotATransition() {
        Simulation sim = nearEastEdgeOf(new RoomAddress(15, 7), 100);
        run(sim, RIGHT, 30);
        assertThat(sim.room()).isEqualTo(new RoomAddress(15, 7));
        int rightEdge = Fixed.px(sim.player().xFp()) + RULES.collisionBox().x() + RULES.collisionBox().w() - 1;
        assertThat(rightEdge).isLessThanOrEqualTo(16 * Simulation.ROOM_W_PX - 1);
        assertThat(sim.roomsEntered()).isZero();
    }

    @Test
    void theEntryPointBecomesTheRespawnAnchor() {
        Simulation sim = nearEastEdgeOf(new RoomAddress(3, 3), 80);
        while (sim.room().col() == 3) {
            sim.tick(RIGHT);
        }
        assertThat(sim.player().entryXFp()).isEqualTo(sim.player().xFp());
        assertThat(sim.player().entryYFp()).isEqualTo(sim.player().yFp());
    }
}
