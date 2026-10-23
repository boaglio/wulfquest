package wulf.world;

/**
 * All 256 room masks stitched into one 512x384 cell grid.
 *
 * <p>Because a flip-screen transition preserves the cross-edge coordinate
 * exactly (AGENTS.md §7.5), walking between rooms is continuous movement on
 * this grid. Outside the map is solid: the world edge is a wall.
 */
public final class WorldGrid implements CollisionWorld {

    public static final int COLS = RoomAddress.GRID_W * CollisionMask.COLS;
    public static final int ROWS = RoomAddress.GRID_H * CollisionMask.ROWS;

    private final boolean[] solid = new boolean[COLS * ROWS];

    public WorldGrid(RoomBaker baker) {
        for (int row = 0; row < RoomAddress.GRID_H; row++) {
            for (int col = 0; col < RoomAddress.GRID_W; col++) {
                CollisionMask mask = baker.room(new RoomAddress(col, row)).mask();
                for (int cy = 0; cy < CollisionMask.ROWS; cy++) {
                    for (int cx = 0; cx < CollisionMask.COLS; cx++) {
                        int gx = col * CollisionMask.COLS + cx;
                        int gy = row * CollisionMask.ROWS + cy;
                        solid[gy * COLS + gx] = mask.isSolid(cx, cy);
                    }
                }
            }
        }
    }

    @Override
    public boolean isSolid(int gx, int gy) {
        return gx < 0 || gy < 0 || gx >= COLS || gy >= ROWS || solid[gy * COLS + gx];
    }

    /**
     * Whether the player's feet box fits with its top-left in this cell. The box
     * is 10x9 px (§7.4): wider and taller than one 8 px cell, never more than two,
     * so a 2x2 cell clearance is exactly the requirement.
     */
    public boolean blockPassable(int gx, int gy) {
        return !isSolid(gx, gy) && !isSolid(gx + 1, gy) && !isSolid(gx, gy + 1) && !isSolid(gx + 1, gy + 1);
    }
}
