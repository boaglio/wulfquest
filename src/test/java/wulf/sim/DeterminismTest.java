package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.input.InputState;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §6.4 — same data, same input, same state, every tick. */
class DeterminismTest {

    /** A fixed, busy input script: walking, diagonals, swings, pauses. */
    private static InputState script(int t) {
        int phase = (t / 37) % 8;
        int dx = phase == 0 || phase == 1 || phase == 7 ? 1 : phase >= 3 && phase <= 5 ? -1 : 0;
        int dy = phase >= 1 && phase <= 3 ? 1 : phase >= 5 ? -1 : 0;
        boolean fire = t % 23 == 0;
        return InputState.of(dx, dy, fire, fire);
    }

    @Test
    void twoRunsOverTheRealMapAgreeOnEveryTick() {
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        Simulation a = Simulation.startingIn(c.player(), grid, c.game().transition().freezeTicks(), c.map().startRoom());
        Simulation b = Simulation.startingIn(c.player(), grid, c.game().transition().freezeTicks(), c.map().startRoom());
        for (int t = 0; t < 5000; t++) {
            InputState in = script(t);
            a.tick(in);
            b.tick(in);
            if (a.stateHash() != b.stateHash()) {
                throw new AssertionError("diverged at tick " + t);
            }
        }
        assertThat(a.tick()).isEqualTo(5000);
    }

    @Test
    void runsWithCreaturesAgreeOnEveryTick() {
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        Ecosystem eco = c.ecosystem();
        int freeze = c.game().transition().freezeTicks();
        // Start in a populated room: the start room is always empty, and this script barely leaves it.
        RoomAddress populated = new RoomAddress(7, 3);
        Simulation a = Simulation.startingIn(c.player(), grid, freeze, populated, eco, 77L);
        Simulation b = Simulation.startingIn(c.player(), grid, freeze, populated, eco, 77L);
        int mostCreatures = 0;
        for (int t = 0; t < 5000; t++) {
            InputState in = script(t);
            a.tick(in);
            b.tick(in);
            if (a.stateHash() != b.stateHash()) {
                throw new AssertionError("diverged at tick " + t);
            }
            mostCreatures = Math.max(mostCreatures, a.creatures().size());
        }
        assertThat(mostCreatures).as("the run really had creatures moving in it").isPositive();
    }

    @Test
    void theRunSeedChangesTheJungle() {
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        Simulation a = Simulation.startingIn(c.player(), grid, 6, c.map().startRoom(), c.ecosystem(), 77L);
        Simulation b = Simulation.startingIn(c.player(), grid, 6, c.map().startRoom(), c.ecosystem(), 78L);
        assertThat(a.stateHash()).isNotEqualTo(b.stateHash());
    }

    @Test
    void differentInputGivesADifferentState() {
        Simulation a = SimFixtures.at(SimFixtures.OPEN, 1000, 1000);
        Simulation b = SimFixtures.at(SimFixtures.OPEN, 1000, 1000);
        a.tick(SimFixtures.RIGHT);
        b.tick(SimFixtures.LEFT);
        assertThat(a.stateHash()).isNotEqualTo(b.stateHash());
    }

    @Test
    void theStartRoomSpawnIsFreeAndInsideTheStartRoom() {
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        Simulation sim = Simulation.startingIn(c.player(), grid, 6, c.map().startRoom());
        assertThat(sim.room()).isEqualTo(c.map().startRoom());
        assertThat(Collision.boxBlocked(grid, c.player().collisionBox(), sim.player().xFp(), sim.player().yFp()))
                .isFalse();
    }
}
