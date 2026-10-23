package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §17.5 — the game-over screen says how much of the adventure was done, as the original did. */
class GameOverScreenTest {

    @Test
    void theSumIsTheOriginals() {
        // $9CC6: rooms visited plus quarters held, out of 256 + 4, rounded down.
        assertThat(GameOverScreen.percentDone(1, 0, 256, 4)).as("the start room alone").isZero();
        assertThat(GameOverScreen.percentDone(13, 0, 256, 4)).isEqualTo(5);
        assertThat(GameOverScreen.percentDone(129, 1, 256, 4)).isEqualTo(50);
        assertThat(GameOverScreen.percentDone(255, 4, 256, 4)).isEqualTo(99);
        assertThat(GameOverScreen.percentDone(256, 4, 256, 4)).as("everything").isEqualTo(100);
    }

    @Test
    void theLineCarriesThePercent() {
        Content c = Content.load(Path.of("data"));
        assertThat(GameOverScreen.progress(c.shell().gameOver(), 37)).contains("37%");
    }

    @Test
    void theRunCountsEveryRoomItHasBeenIn() {
        Content c = Content.load(Path.of("data"));
        Simulation sim = Simulation.startingIn(c.player(), new WorldGrid(c.rooms()), 0, new RoomAddress(8, 10),
                c.ecosystem(), 1L);
        assertThat(sim.roomsVisited()).as("the start room counts, as it did in the original").isEqualTo(1);
    }
}
