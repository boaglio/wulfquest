package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §15.2 — the original's orchid: one a room, at its room type's spot ($D6A6). */
class OriginalOrchidSpotsTest {

    private static final Content C = Content.load(Path.of("data"));
    private static final WorldGrid WORLD = new WorldGrid(C.rooms());

    @Test
    void oneFlowerARoomAtItsTypesSpot() {
        OrchidField field = new OrchidField(C.orchids(), C.orchidSpots());
        List<List<Integer>> spots = C.jungle().beasts().spots();
        int top = C.jungle().beasts().screenTopPx();
        int none = 0;
        int blocked = 0;
        StringBuilder where = new StringBuilder();
        for (int row = 0; row < RoomAddress.GRID_H; row++) {
            for (int col = 0; col < RoomAddress.GRID_W; col++) {
                RoomAddress room = new RoomAddress(col, row);
                List<Integer> spot = spots.get(C.map().roomType(col, row));
                int[] a = field.anchors(room, WORLD, 1L);
                if (spot.get(0) == 0 && spot.get(1) == 0) {
                    assertThat(a).as("room %s: its type has no spot", room).isEmpty();
                    none++;
                    continue;
                }
                assertThat(a).as("room %s", room).containsExactly(spot.get(0) + 8, spot.get(1) - top);
                assertThat(field.anchors(room, WORLD, 99L)).as("the same whatever the seed").isEqualTo(a);
                if (blockedAt(room, a[0], a[1])) {
                    blocked++;
                    where.append(room).append(" type ").append(C.map().roomType(col, row)).append("; ");
                }
            }
        }
        assertThat(blocked).as("every spot on open ground: %s", where).isZero();
        assertThat(none).as("type 1, the one without a spot, is on no room of the map").isZero();
    }

    private static boolean blockedAt(RoomAddress room, int x, int y) {
        int left = room.col() * Simulation.ROOM_W_PX + x - 8;
        int topPx = room.row() * Simulation.ROOM_H_PX + y - 16;
        for (int py = topPx; py < topPx + 16; py += 4) {
            for (int px = left; px < left + 16; px += 4) {
                if (WORLD.isSolid(Math.floorDiv(px, Simulation.CELL_PX), Math.floorDiv(py, Simulation.CELL_PX))) {
                    return true;
                }
            }
        }
        return false;
    }
}
