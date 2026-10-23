package wulf.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.LandmarksData;

/** AGENTS.md §14.10 — the way a looking glass shows is one Vale can walk. */
class WayfinderTest {

    private static WorldGrid grid;
    private static Wayfinder finder;
    private static Content c;

    @BeforeAll
    static void load() {
        c = Content.load(Path.of("data"));
        grid = new WorldGrid(c.rooms());
        finder = new Wayfinder(grid);
    }

    private static int centre(RoomAddress room) {
        return finder.nearest(null, room.col() * CollisionMask.COLS + CollisionMask.COLS / 2,
                room.row() * CollisionMask.ROWS + CollisionMask.ROWS / 2, 8);
    }

    @Test
    void fromTheStartEveryRoomOfEverySetHasAWayThatIsWalkedACellAtATime() {
        int from = centre(c.map().startRoom());
        for (var set : c.landmarks().amuletSets().sets()) {
            for (String key : set) {
                int target = centre(LandmarksData.room(key));
                int[] dist = finder.distances(new int[] {target});
                int[] way = finder.path(dist, from);
                assertThat(way).as(key).isNotEmpty();
                assertThat(way.length).isEqualTo(dist[from] + 1);
                assertThat(way[0]).isEqualTo(from);
                assertThat(way[way.length - 1]).isEqualTo(target);
                for (int i = 0; i < way.length; i++) {
                    assertThat(grid.blockPassable(Wayfinder.gx(way[i]), Wayfinder.gy(way[i]))).isTrue();
                    if (i > 0) {
                        int step = Math.abs(Wayfinder.gx(way[i]) - Wayfinder.gx(way[i - 1]))
                                + Math.abs(Wayfinder.gy(way[i]) - Wayfinder.gy(way[i - 1]));
                        assertThat(step).as("one cell at a time").isEqualTo(1);
                    }
                }
            }
        }
    }

    @Test
    void theNearestOfSeveralTargetsWins() {
        int from = centre(c.map().startRoom());
        int near = centre(new RoomAddress(c.map().startRoom().col(), c.map().startRoom().row() + 1));
        int far = centre(new RoomAddress(0, 0));
        int[] dist = finder.distances(new int[] {far, near});
        int[] way = finder.path(dist, from);
        assertThat(way[way.length - 1]).isEqualTo(near);
        assertThat(finder.distances(new int[] {near})[from]).isEqualTo(dist[from]);
    }

    @Test
    void aStartNothingReachesHasNoWay() {
        int[] dist = finder.distances(new int[0]);
        assertThat(finder.path(dist, centre(c.map().startRoom()))).isEmpty();
        assertThat(finder.path(dist, -1)).isEmpty();
    }
}
