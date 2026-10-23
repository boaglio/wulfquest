package wulf.sim;

import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.world.CollisionWorld;

/** Box versus scenery, and axis-separated resolution, for the player and creatures alike (AGENTS.md §7.4). */
final class Collision {

    private Collision() {
    }

    static boolean boxBlocked(CollisionWorld world, PlayerData.Box box, int xFp, int yFp) {
        return blocked(world, box.x(), box.y(), box.w(), box.h(), xFp, yFp);
    }

    /** Whether a box, offset (bx, by) from an origin at (xFp, yFp), overlaps any solid cell. */
    static boolean blocked(CollisionWorld world, int bx, int by, int bw, int bh, int xFp, int yFp) {
        int left = Fixed.px(xFp) + bx;
        int top = Fixed.px(yFp) + by;
        int cx0 = Math.floorDiv(left, Simulation.CELL_PX);
        int cy0 = Math.floorDiv(top, Simulation.CELL_PX);
        int cx1 = Math.floorDiv(left + bw - 1, Simulation.CELL_PX);
        int cy1 = Math.floorDiv(top + bh - 1, Simulation.CELL_PX);
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (world.isSolid(cx, cy)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * One axis of movement. Free: the target. Blocked: the nearest free <em>whole</em>
     * pixel between the current position and the target — and if there is none, the
     * current position itself, fraction and all.
     *
     * <p>Both halves matter. Keeping the target's fraction lets a pinned box creep
     * forward inside the last free pixel (found in M3). Settling back to a pixel
     * boundary behind the current position makes a sub-pixel mover — most creatures
     * move under a pixel a tick vertically — step in and snap back forever, so it is
     * never still against a wall (found in M4).
     *
     * @return the new coordinate on that axis
     */
    static int resolve(CollisionWorld world, int bx, int by, int bw, int bh, int xFp, int yFp, int delta,
                       boolean alongX) {
        int pos = alongX ? xFp : yFp;
        if (delta == 0) {
            return pos;
        }
        int target = pos + delta;
        if (!blockedAlong(world, bx, by, bw, bh, xFp, yFp, target, alongX)) {
            return target;
        }
        int from = Fixed.fp(Fixed.px(target));
        if (delta > 0) {
            for (int cand = from; cand > pos; cand -= Fixed.ONE) {
                if (!blockedAlong(world, bx, by, bw, bh, xFp, yFp, cand, alongX)) {
                    return cand;
                }
            }
        } else {
            for (int cand = from + Fixed.ONE; cand < pos; cand += Fixed.ONE) {
                if (!blockedAlong(world, bx, by, bw, bh, xFp, yFp, cand, alongX)) {
                    return cand;
                }
            }
        }
        return pos;
    }

    private static boolean blockedAlong(CollisionWorld world, int bx, int by, int bw, int bh, int xFp, int yFp,
                                        int value, boolean alongX) {
        return alongX ? blocked(world, bx, by, bw, bh, value, yFp) : blocked(world, bx, by, bw, bh, xFp, value);
    }
}
