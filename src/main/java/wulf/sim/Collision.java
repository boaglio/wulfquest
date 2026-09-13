package wulf.sim;

import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.world.CollisionWorld;

/** Feet-box versus scenery (AGENTS.md §7.4). */
final class Collision {

    private Collision() {
    }

    /** Whether the feet box, with its origin at (xFp, yFp), overlaps any solid cell. */
    static boolean boxBlocked(CollisionWorld world, PlayerData.Box box, int xFp, int yFp) {
        int left = Fixed.px(xFp) + box.x();
        int top = Fixed.px(yFp) + box.y();
        int cx0 = Math.floorDiv(left, Simulation.CELL_PX);
        int cy0 = Math.floorDiv(top, Simulation.CELL_PX);
        int cx1 = Math.floorDiv(left + box.w() - 1, Simulation.CELL_PX);
        int cy1 = Math.floorDiv(top + box.h() - 1, Simulation.CELL_PX);
        for (int cy = cy0; cy <= cy1; cy++) {
            for (int cx = cx0; cx <= cx1; cx++) {
                if (world.isSolid(cx, cy)) {
                    return true;
                }
            }
        }
        return false;
    }
}
