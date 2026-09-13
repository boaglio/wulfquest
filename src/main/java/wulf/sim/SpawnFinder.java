package wulf.sim;

import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/**
 * The nearest place the player can stand, searched breadth-first across a
 * room's cells from a preferred point (AGENTS.md §11.7). Runs only at start and
 * at respawn, never per tick.
 */
final class SpawnFinder {

    private SpawnFinder() {
    }

    /**
     * @param inside how far the feet must be from each room edge: an entry point
     *               straddles the edge, and respawning there would leave the sprite
     *               half-hidden by the flip-screen clip
     * @return {xFp, yFp}; the preferred point itself when it already qualifies
     */
    static int[] nearestFree(CollisionWorld world, PlayerData.Box box, PlayerData.Margin inside, RoomAddress room,
                             int xFp, int yFp) {
        if (fits(world, box, inside, room, xFp, yFp)) {
            return new int[] {xFp, yFp};
        }
        int cols = Simulation.ROOM_W_PX / Simulation.CELL_PX;
        int rows = Simulation.ROOM_H_PX / Simulation.CELL_PX;
        int roomX = room.col() * Simulation.ROOM_W_PX;
        int roomY = room.row() * Simulation.ROOM_H_PX;
        int startCx = clamp((Fixed.px(xFp) - roomX) / Simulation.CELL_PX, 0, cols - 1);
        int startCy = clamp((Fixed.px(yFp) - 1 - roomY) / Simulation.CELL_PX, 0, rows - 1);

        boolean[] seen = new boolean[cols * rows];
        int[] queue = new int[cols * rows];
        int head = 0;
        int tail = 0;
        seen[startCy * cols + startCx] = true;
        queue[tail++] = startCy * cols + startCx;
        while (head < tail) {
            int i = queue[head++];
            int cx = i % cols;
            int cy = i / cols;
            // Feet at the bottom-centre of the cell.
            int candX = Fixed.fp(roomX + cx * Simulation.CELL_PX + Simulation.CELL_PX / 2);
            int candY = Fixed.fp(roomY + cy * Simulation.CELL_PX + Simulation.CELL_PX);
            if (fits(world, box, inside, room, candX, candY)) {
                return new int[] {candX, candY};
            }
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] s : steps) {
                int nx = cx + s[0];
                int ny = cy + s[1];
                if (nx >= 0 && ny >= 0 && nx < cols && ny < rows && !seen[ny * cols + nx]) {
                    seen[ny * cols + nx] = true;
                    queue[tail++] = ny * cols + nx;
                }
            }
        }
        // Unreachable when MapAudit passes: every room has a 2x2 clearance.
        return new int[] {xFp, yFp};
    }

    private static boolean fits(CollisionWorld world, PlayerData.Box box, PlayerData.Margin inside, RoomAddress room,
                                int xFp, int yFp) {
        int x = Fixed.px(xFp) - room.col() * Simulation.ROOM_W_PX;
        int y = Fixed.px(yFp) - room.row() * Simulation.ROOM_H_PX;
        return x >= inside.left() && x <= Simulation.ROOM_W_PX - inside.right()
                && y >= inside.top() && y <= Simulation.ROOM_H_PX - inside.bottom()
                && Simulation.roomColOf(box, xFp) == room.col()
                && Simulation.roomRowOf(box, yFp) == room.row()
                && !Collision.boxBlocked(world, box, xFp, yFp);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
