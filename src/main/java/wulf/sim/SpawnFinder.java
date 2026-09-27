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

    /** How far past the room's edges the walk from the entry point may stray: an entry straddles the edge. */
    private static final int SLACK_PX = 8;
    private static final PlayerData.Margin ANYWHERE_INSIDE = new PlayerData.Margin(0, 0, 0, 0);

    /**
     * The nearest place to stand that the player can <em>walk to</em> from the
     * preferred point, when the preferred point is itself somewhere they stood.
     *
     * <p>Nearest by cells alone once put Vale behind a wall: the top strip every
     * room has (§25 Q14) is shallower than {@code inside.top}, so a death there
     * respawned him in whatever pocket lay nearest below it, sealed off from every
     * exit. Now a spot must be reachable on foot; if none keeps the full margin,
     * the margin gives way before the wall does.
     *
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
        boolean[] walk = walkable(world, box, room, xFp, yFp);
        if (walk == null) {
            return nearest(world, box, inside, room, xFp, yFp, null);   // nowhere to walk from: cells alone
        }
        int[] p = nearest(world, box, inside, room, xFp, yFp, walk);
        if (p == null) {
            p = nearest(world, box, ANYWHERE_INSIDE, room, xFp, yFp, walk);
        }
        return p != null ? p : new int[] {xFp, yFp};   // where they stood is at least not a cell
    }

    /**
     * Breadth-first across the room's cells from the preferred point.
     *
     * @param walk if not null, only spots in it count, and null comes back when none does
     */
    private static int[] nearest(CollisionWorld world, PlayerData.Box box, PlayerData.Margin inside, RoomAddress room,
                                 int xFp, int yFp, boolean[] walk) {
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
            if (fits(world, box, inside, room, candX, candY)
                    && (walk == null || walk[at(Fixed.px(candX) - roomX, Fixed.px(candY) - roomY)])) {
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
        if (walk != null) {
            return null;
        }
        // Unreachable when MapAudit passes: every room has a 2x2 clearance.
        return new int[] {xFp, yFp};
    }

    private static final int WALK_W = Simulation.ROOM_W_PX + 2 * SLACK_PX + 1;
    private static final int WALK_H = Simulation.ROOM_H_PX + 2 * SLACK_PX + 1;

    /** Index of room-local feet in the walk grid. */
    private static int at(int localX, int localY) {
        return (localY + SLACK_PX) * WALK_W + localX + SLACK_PX;
    }

    /**
     * Every whole-pixel foot position the player can walk to from the preferred
     * point, in the room and a slack's width past its edges; null when the
     * preferred point is not somewhere they could stand. Runs at start, at
     * respawn and once per room entry — never per tick.
     */
    private static boolean[] walkable(CollisionWorld world, PlayerData.Box box, RoomAddress room, int xFp, int yFp) {
        int roomX = room.col() * Simulation.ROOM_W_PX;
        int roomY = room.row() * Simulation.ROOM_H_PX;
        int sx = Fixed.px(xFp) - roomX;
        int sy = Fixed.px(yFp) - roomY;
        if (sx < -SLACK_PX || sy < -SLACK_PX || sx > Simulation.ROOM_W_PX + SLACK_PX
                || sy > Simulation.ROOM_H_PX + SLACK_PX
                || Collision.boxBlocked(world, box, Fixed.fp(roomX + sx), Fixed.fp(roomY + sy))) {
            return null;
        }
        boolean[] reached = new boolean[WALK_W * WALK_H];
        int[] queue = new int[WALK_W * WALK_H];
        int head = 0;
        int tail = 0;
        reached[at(sx, sy)] = true;
        queue[tail++] = at(sx, sy);
        while (head < tail) {
            int i = queue[head++];
            int gx = i % WALK_W;
            int gy = i / WALK_W;
            for (int k = 0; k < 4; k++) {
                int nx = gx + (k == 0 ? 1 : k == 1 ? -1 : 0);
                int ny = gy + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || ny < 0 || nx >= WALK_W || ny >= WALK_H || reached[ny * WALK_W + nx]) {
                    continue;
                }
                if (!Collision.boxBlocked(world, box, Fixed.fp(roomX + nx - SLACK_PX), Fixed.fp(roomY + ny - SLACK_PX))) {
                    reached[ny * WALK_W + nx] = true;
                    queue[tail++] = ny * WALK_W + nx;
                }
            }
        }
        return reached;
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
