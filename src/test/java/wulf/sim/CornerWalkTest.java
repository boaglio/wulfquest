package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import wulf.Content;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/**
 * M3's acceptance criterion, made executable (AGENTS.md §24): Ranger Vale can
 * walk from the start room to all four corners of the map.
 *
 * <p>A route is planned over 2x2-cell clearances — exactly the feet box — then
 * driven through the real {@link Simulation} with ordinary per-tick input, on
 * the real map, flips and all. If collision, sliding or room transitions are
 * wrong anywhere along the way, the bot gets stuck and the test says where.
 */
class CornerWalkTest {

    private static Content content;
    private static WorldGrid grid;

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
        grid = new WorldGrid(content.rooms());
    }

    @ParameterizedTest(name = "8,10 to {0},{1}")
    @CsvSource({"1,1", "14,1", "1,14", "14,14"})
    void rangerValeWalksFromTheStartToEveryCorner(int col, int row) {
        RoomAddress target = new RoomAddress(col, row);
        Simulation sim = Simulation.startingIn(content.player(), grid, content.game().transition().freezeTicks(),
                content.map().startRoom());
        List<int[]> route = route(sim, target);
        assertThat(route).as("a 2x2-cell route exists to %s", target).isNotEmpty();

        drive(sim, route);

        assertThat(sim.room()).isEqualTo(target);
        assertThat(sim.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(sim.roomsEntered()).isPositive();
    }

    /** Breadth-first over 2x2-cell blocks from the player's block to any block whose centre is in the target. */
    private static List<int[]> route(Simulation sim, RoomAddress target) {
        PlayerData.Box box = content.player().collisionBox();
        int cols = WorldGrid.COLS;
        int startX = Math.floorDiv(Fixed.px(sim.player().xFp()) + box.x(), Simulation.CELL_PX);
        int startY = Math.floorDiv(Fixed.px(sim.player().yFp()) + box.y(), Simulation.CELL_PX);
        int[] parent = new int[cols * WorldGrid.ROWS];
        Arrays.fill(parent, -2);
        int[] queue = new int[parent.length];
        int head = 0;
        int tail = 0;
        int start = startY * cols + startX;
        parent[start] = -1;
        queue[tail++] = start;
        int goal = -1;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int i = queue[head++];
            int x = i % cols;
            int y = i / cols;
            // A full cell inside the room. The first block over the edge can sit exactly on it,
            // where stopping within the 1 px steering tolerance leaves the feet on the old side.
            int centreX = x * 8 + 8 - target.col() * Simulation.ROOM_W_PX;
            int centreY = y * 8 + 8 - target.row() * Simulation.ROOM_H_PX;
            if (centreX >= 8 && centreX < Simulation.ROOM_W_PX - 8
                    && centreY >= 8 && centreY < Simulation.ROOM_H_PX - 8) {
                goal = i;
                break;
            }
            for (int[] s : steps) {
                int nx = x + s[0];
                int ny = y + s[1];
                if (nx < 0 || ny < 0 || nx >= cols || ny >= WorldGrid.ROWS) {
                    continue;
                }
                int j = ny * cols + nx;
                if (parent[j] != -2 || !grid.blockPassable(nx, ny)) {
                    continue;
                }
                parent[j] = i;
                queue[tail++] = j;
            }
        }
        LinkedList<int[]> path = new LinkedList<>();
        for (int i = goal; i >= 0; i = parent[i]) {
            path.addFirst(new int[] {i % cols, i / cols});
        }
        return path;
    }

    /** Steers from block to block with plain directional input, as a player would. */
    private static void drive(Simulation sim, List<int[]> route) {
        int i = 0;
        int sinceProgress = 0;
        while (i < route.size()) {
            // Feet position whose 10x9 box sits inside this 16x16 block with margin to spare.
            int tx = Fixed.fp(route.get(i)[0] * 8 + 8);
            int ty = Fixed.fp(route.get(i)[1] * 8 + 13);
            int ddx = tx - sim.player().xFp();
            int ddy = ty - sim.player().yFp();
            if (Math.abs(ddx) <= Fixed.ONE && Math.abs(ddy) <= Fixed.ONE) {
                i++;
                sinceProgress = 0;
                continue;
            }
            int dx = Math.abs(ddx) > Fixed.ONE ? Integer.signum(ddx) : 0;
            int dy = Math.abs(ddy) > Fixed.ONE ? Integer.signum(ddy) : 0;
            sim.tick(InputState.of(dx, dy, false, false));
            if (++sinceProgress > 600) {
                throw new AssertionError("stuck heading for block " + i + " of " + route.size()
                        + " at world px " + Fixed.px(sim.player().xFp()) + "," + Fixed.px(sim.player().yFp())
                        + " in room " + sim.room());
            }
        }
    }
}
