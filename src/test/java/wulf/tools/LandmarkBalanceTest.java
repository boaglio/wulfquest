package wulf.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.LandmarksData;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §14.2 — every lair and the way out can be walked to, and the four trips are fair. */
class LandmarkBalanceTest {

    @Test
    void theFourLairsAreWithinFourRoomsOfEachOtherFromTheStart() {
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
        assertThat(Collections.max(lairs) - Collections.min(lairs)).as("spread of %s", lairs).isLessThanOrEqualTo(4);
        assertThat(rooms[c.landmarks().exitRoom().index()]).as("the way out").isLessThan(Integer.MAX_VALUE);
        for (String mouth : c.landmarks().caveMouths()) {
            assertThat(rooms[LandmarksData.room(mouth).index()]).as("shrine %s", mouth).isLessThan(Integer.MAX_VALUE);
        }
    }
}
