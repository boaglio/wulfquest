package wulf.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.LandmarksData;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/**
 * AGENTS.md §14.2 — every room the original may hide a quarter in, and the way out, can be
 * walked to; and the game hides them in one of its eight sets, a set picked afresh each game.
 *
 * <p>This used to hold the four trips to within four rooms of each other. That was a design
 * wish measured on a map that turned out to be mis-gridded (§25 Q14); on the true map one
 * long maze makes every one of the game's own eight amulet sets spread by 50 rooms or more.
 */
class LandmarkBalanceTest {

    @Test
    void everySetCanBeReachedAndTheGamePicksAmongAllEight() {
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        int[] rooms = MapAudit.roomDistances(grid, c.map().startRoom());
        LandmarksData marks = c.landmarks();
        // $A29D: eight sets of four. Set 3 is $35 $C3 $4D $A9.
        assertThat(marks.amuletSets().sets()).hasSize(8);
        assertThat(marks.withAmuletSet(3).lairRooms().stream().map(RoomAddress::toString).toList())
                .containsExactly("5,3", "13,4", "3,12", "9,10");
        for (int k = 0; k < marks.amuletSets().sets().size(); k++) {
            List<Integer> lairs = new ArrayList<>();
            for (RoomAddress lair : marks.withAmuletSet(k).lairRooms()) {
                int d = rooms[lair.index()];
                assertThat(d).as("set %d: lair %s reachable", k, lair).isLessThan(Integer.MAX_VALUE);
                lairs.add(d);
            }
            System.out.println("set " + k + ": lair distances from " + c.map().startRoom() + ": " + lairs);
        }
        assertThat(rooms[marks.exitRoom().index()]).as("the way out").isLessThan(Integer.MAX_VALUE);
        for (String shrine : marks.shrines().rooms()) {
            assertThat(rooms[LandmarksData.room(shrine).index()]).as("shrine %s", shrine).isLessThan(Integer.MAX_VALUE);
        }
        Set<Integer> picked = new HashSet<>();
        for (long seed = 0; seed < 200; seed++) {
            Simulation game = Simulation.startingIn(c.player(), grid, 0, c.map().startRoom(), c.ecosystem(), seed);
            int set = game.landmarks().amuletSet();
            assertThat(set).as("seed %d hides the amulet in one of the sets", seed).isBetween(0, 7);
            Simulation again = Simulation.startingIn(c.player(), grid, 0, c.map().startRoom(), c.ecosystem(), seed);
            assertThat(again.landmarks().amuletSet()).as("the same seed, the same set").isEqualTo(set);
            picked.add(set);
        }
        assertThat(picked).as("every set comes up").hasSize(8);
        Simulation named = Simulation.startingIn(c.player(), grid, 0, c.map().startRoom(),
                c.withAmuletSet(5).ecosystem(), 1L);
        assertThat(named.landmarks().amuletSet()).as("--amulet-set names one").isEqualTo(5);
    }
}
