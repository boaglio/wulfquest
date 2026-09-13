package wulf.sim;

import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/**
 * Where the player's feet can get to inside one room without leaving it, on a 4 px
 * lattice (AGENTS.md §12.6). Ground creatures are placed only near it, so none waits
 * in a strip behind the scenery that only a neighbouring room opens onto.
 *
 * <p>A 4 px step is exact: it is shorter than the feet box and than a cell, so two
 * neighbouring places that both fit have nothing solid between them. Built once per
 * population, never per tick.
 */
final class Reach {

    static final int STEP_PX = 4;
    private static final int COLS = Simulation.ROOM_W_PX / STEP_PX + 1;
    private static final int ROWS = Simulation.ROOM_H_PX / STEP_PX + 1;
    private static final Reach EVERYWHERE = new Reach(null);

    private final boolean[] reached;

    private Reach(boolean[] reached) {
        this.reached = reached;
    }

    /**
     * @param roomSolid the scenery, with the room's edges as walls
     * @param startXFp  feet that fit wholly inside the room, world fixed point; when they
     *                  do not, nothing is ruled out
     */
    static Reach from(CollisionWorld roomSolid, PlayerData.Box box, RoomAddress room, int startXFp, int startYFp) {
        int roomX = room.col() * Simulation.ROOM_W_PX;
        int roomY = room.row() * Simulation.ROOM_H_PX;
        int sx = clamp(Math.floorDiv(Fixed.px(startXFp) - roomX + STEP_PX / 2, STEP_PX), 0, COLS - 1);
        int sy = clamp(Math.floorDiv(Fixed.px(startYFp) - roomY + STEP_PX / 2, STEP_PX), 0, ROWS - 1);
        if (!fits(roomSolid, box, roomX, roomY, sx, sy)) {
            return EVERYWHERE;
        }
        boolean[] reached = new boolean[COLS * ROWS];
        int[] queue = new int[COLS * ROWS];
        int head = 0;
        int tail = 0;
        reached[sy * COLS + sx] = true;
        queue[tail++] = sy * COLS + sx;
        while (head < tail) {
            int i = queue[head++];
            int nx0 = i % COLS;
            int ny0 = i / COLS;
            for (int k = 0; k < 4; k++) {
                int nx = nx0 + (k == 0 ? 1 : k == 1 ? -1 : 0);
                int ny = ny0 + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= COLS || ny >= ROWS || reached[ny * COLS + nx]) {
                    continue;
                }
                if (fits(roomSolid, box, roomX, roomY, nx, ny)) {
                    reached[ny * COLS + nx] = true;
                    queue[tail++] = ny * COLS + nx;
                }
            }
        }
        return new Reach(reached);
    }

    /** Whether reachable feet lie within {@code radiusPx} of a room-local point on both axes. */
    boolean near(int localXPx, int localYPx, int radiusPx) {
        if (reached == null) {
            return true;
        }
        int x0 = clamp(Math.floorDiv(localXPx - radiusPx + STEP_PX - 1, STEP_PX), 0, COLS - 1);
        int x1 = clamp(Math.floorDiv(localXPx + radiusPx, STEP_PX), 0, COLS - 1);
        int y0 = clamp(Math.floorDiv(localYPx - radiusPx + STEP_PX - 1, STEP_PX), 0, ROWS - 1);
        int y1 = clamp(Math.floorDiv(localYPx + radiusPx, STEP_PX), 0, ROWS - 1);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if (reached[y * COLS + x]) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean fits(CollisionWorld roomSolid, PlayerData.Box box, int roomX, int roomY, int nx, int ny) {
        return !Collision.boxBlocked(roomSolid, box, Fixed.fp(roomX + nx * STEP_PX), Fixed.fp(roomY + ny * STEP_PX));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
