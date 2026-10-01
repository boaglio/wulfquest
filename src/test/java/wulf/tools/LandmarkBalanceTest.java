package wulf.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.LandmarksData;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/**
 * AGENTS.md §14.2 — every lair and the way out can be walked to, and the lairs are rooms the
 * original itself hid the amulet in.
 *
 * <p>This used to hold the four trips to within four rooms of each other. That was a design
 * wish measured on a map that turned out to be mis-gridded (§25 Q14); on the true map one
 * long maze makes every one of the game's own eight amulet sets spread by 50 rooms or more.
 */
class LandmarkBalanceTest {

    @Test
    void theLairsAreOneOfTheOriginalsAmuletSetsAndAllCanBeReached() {
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        int[] rooms = MapAudit.roomDistances(grid, c.map().startRoom());
        List<Integer> lairs = new ArrayList<>();
        for (RoomAddress lair : c.landmarks().lairRooms()) {
            int d = rooms[lair.index()];
            assertThat(d).as("lair %s reachable", lair).isLessThan(Integer.MAX_VALUE);
            lairs.add(d);
        }
        System.out.println("lair distances from " + c.map().startRoom() + ": " + lairs
                + ", way out " + rooms[c.landmarks().exitRoom().index()]);
        // Set 3 of the eight at $A29D (bytes $35 $C3 $4D $A9), one room in each quadrant.
        assertThat(c.landmarks().lairRooms().stream().map(RoomAddress::toString).toList())
                .containsExactlyInAnyOrder("5,3", "3,12", "13,4", "9,10");
        assertThat(rooms[c.landmarks().exitRoom().index()]).as("the way out").isLessThan(Integer.MAX_VALUE);
        for (String shrine : c.landmarks().shrines().rooms()) {
            assertThat(rooms[LandmarksData.room(shrine).index()]).as("shrine %s", shrine).isLessThan(Integer.MAX_VALUE);
        }
    }
}
