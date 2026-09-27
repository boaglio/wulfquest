package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.JsonDb;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/**
 * AGENTS.md §11.7 — a respawn puts the player somewhere they can walk to from
 * where they came in. A playtest found the opposite: die in the top strip every
 * room has (§25 Q14) and you came back in a sealed pocket below it, for good.
 */
class SpawnFinderTest {

    private static final int SLACK = 8;
    private static final int W = 256 + 2 * SLACK + 1;
    private static final int H = 192 + 2 * SLACK + 1;

    @Test
    void everyWayIntoEveryRoomRespawnsSomewhereConnectedToIt() {
        Content c = Content.load(new JsonDb(Path.of("data")));
        WorldGrid world = new WorldGrid(c.rooms());
        PlayerData rules = c.player();
        PlayerData.Box box = rules.collisionBox();
        int entries = 0;
        List<String> cutOff = new ArrayList<>();
        for (int row = 0; row < RoomAddress.GRID_H; row++) {
            for (int col = 0; col < RoomAddress.GRID_W; col++) {
                RoomAddress room = new RoomAddress(col, row);
                int ox = col * 256 - SLACK;
                int oy = row * 192 - SLACK;
                int[] region = regions(world, box, ox, oy);
                for (int[] e : entries()) {
                    int ex = col * 256 + e[0];
                    int ey = row * 192 + e[1];
                    int bx = ex + e[2];
                    int by = ey + e[3];
                    boolean crossing = Simulation.roomColOf(box, Fixed.fp(ex)) == col
                            && Simulation.roomRowOf(box, Fixed.fp(ey)) == row
                            && bx >= 0 && by >= 0 && bx < 16 * 256 && by < 16 * 192
                            && (Simulation.roomColOf(box, Fixed.fp(bx)) != col
                                || Simulation.roomRowOf(box, Fixed.fp(by)) != row)
                            && !blocked(world, box, ex, ey) && !blocked(world, box, bx, by);
                    if (!crossing) {
                        continue;
                    }
                    entries++;
                    int[] p = SpawnFinder.nearestFree(world, box, rules.spawn().insideRoomPx(), room,
                            Fixed.fp(ex), Fixed.fp(ey));
                    int sx = Fixed.px(p[0]) - ox;
                    int sy = Fixed.px(p[1]) - oy;
                    if (region[(ey - oy) * W + (ex - ox)] != region[sy * W + sx]) {
                        cutOff.add(room + " in at " + e[0] + "," + e[1] + " -> " + (sx - SLACK) + "," + (sy - SLACK));
                    }
                }
            }
        }
        assertThat(entries).as("the probe found the ways in").isGreaterThan(5000);
        assertThat(cutOff).as("respawns walled off from the way in").isEmpty();
    }

    /** Feet just past each edge, and a step back across it into the room they came from. */
    private static List<int[]> entries() {
        List<int[]> es = new ArrayList<>();
        for (int x = 8; x < 248; x += 2) {
            es.add(new int[] {x, 5, 0, -6});
            es.add(new int[] {x, 195, 0, 6});
        }
        for (int y = 12; y < 190; y += 2) {
            es.add(new int[] {1, y, -6, 0});
            es.add(new int[] {254, y, 6, 0});
        }
        return es;
    }

    /** Connected regions of standable feet, 1 px apart, over the room and a slack past its edges. */
    private static int[] regions(CollisionWorld world, PlayerData.Box box, int ox, int oy) {
        int[] label = new int[W * H];
        int[] queue = new int[W * H];
        int next = 0;
        for (int i = 0; i < W * H; i++) {
            if (label[i] != 0 || blocked(world, box, ox + i % W, oy + i / W)) {
                continue;
            }
            label[i] = ++next;
            int head = 0;
            int tail = 0;
            queue[tail++] = i;
            while (head < tail) {
                int j = queue[head++];
                for (int k = 0; k < 4; k++) {
                    int nx = j % W + (k == 0 ? 1 : k == 1 ? -1 : 0);
                    int ny = j / W + (k == 2 ? 1 : k == 3 ? -1 : 0);
                    if (nx >= 0 && ny >= 0 && nx < W && ny < H && label[ny * W + nx] == 0
                            && !blocked(world, box, ox + nx, oy + ny)) {
                        label[ny * W + nx] = next;
                        queue[tail++] = ny * W + nx;
                    }
                }
            }
        }
        return label;
    }

    private static boolean blocked(CollisionWorld world, PlayerData.Box box, int x, int y) {
        return Collision.boxBlocked(world, box, Fixed.fp(x), Fixed.fp(y));
    }
}
