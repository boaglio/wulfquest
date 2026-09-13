package wulf.sim;

/** The eight directions the player can face (AGENTS.md §11.4). */
public enum Direction8 {
    N(0, -1), NE(1, -1), E(1, 0), SE(1, 1), S(0, 1), SW(-1, 1), W(-1, 0), NW(-1, -1);

    private static final Direction8[] BY_DELTA = new Direction8[9];

    static {
        for (Direction8 d : values()) {
            BY_DELTA[(d.dy + 1) * 3 + (d.dx + 1)] = d;
        }
    }

    private final int dx;
    private final int dy;

    Direction8(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    public int dx() {
        return dx;
    }

    public int dy() {
        return dy;
    }

    public boolean diagonal() {
        return dx != 0 && dy != 0;
    }

    /** The direction for a unit delta; both components in -1..1 and not both zero. */
    public static Direction8 of(int dx, int dy) {
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || (dx == 0 && dy == 0)) {
            throw new IllegalArgumentException("no direction for " + dx + "," + dy);
        }
        return BY_DELTA[(dy + 1) * 3 + (dx + 1)];
    }
}
