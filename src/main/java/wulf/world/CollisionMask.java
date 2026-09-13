package wulf.world;

/**
 * One room's impassable cells: 32x24, one flag per 8x8 cell (AGENTS.md §7.2).
 * Built by {@link RoomBaker}; read-only to everyone else.
 */
public final class CollisionMask {

    public static final int COLS = 32;
    public static final int ROWS = 24;

    private final boolean[] solid = new boolean[COLS * ROWS];

    CollisionMask() {
    }

    void setSolid(int cx, int cy) {
        if (cx >= 0 && cy >= 0 && cx < COLS && cy < ROWS) {
            solid[cy * COLS + cx] = true;
        }
    }

    /** Room-local query; off-room cells are not solid here — the world edge is {@link WorldGrid}'s job. */
    public boolean isSolid(int cx, int cy) {
        return cx >= 0 && cy >= 0 && cx < COLS && cy < ROWS && solid[cy * COLS + cx];
    }

    public int solidCount() {
        int n = 0;
        for (boolean s : solid) {
            if (s) {
                n++;
            }
        }
        return n;
    }

    public int walkableCount() {
        return COLS * ROWS - solidCount();
    }

    /** Walkable share of the room, 0..100, rounded down. */
    public int walkablePercent() {
        return walkableCount() * 100 / (COLS * ROWS);
    }

    public String toAscii() {
        StringBuilder sb = new StringBuilder((COLS + 1) * ROWS);
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                sb.append(solid[y * COLS + x] ? '#' : '.');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
