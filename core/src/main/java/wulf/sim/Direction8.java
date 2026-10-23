package wulf.sim;

/** The eight directions the player can face (AGENTS.md §11.4). */
public enum Direction8 {
    N(0, -1), NE(1, -1), E(1, 0), SE(1, 1), S(0, 1), SW(-1, 1), W(-1, 0), NW(-1, -1);

    private static final Direction8[] VALUES = values();
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

    /** This direction turned by {@code steps} eighths of a turn; positive is clockwise. */
    public Direction8 rotate(int steps) {
        return VALUES[Math.floorMod(ordinal() + steps, VALUES.length)];
    }

    /**
     * The direction that best matches a delta of any size: a pure axis when one
     * component is more than twice the other, otherwise the diagonal. A zero delta
     * gives {@link #S}.
     */
    public static Direction8 toward(int dx, int dy) {
        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        if (ax == 0 && ay == 0) {
            return S;
        }
        if (ax > 2 * ay) {
            return of(Integer.signum(dx), 0);
        }
        if (ay > 2 * ax) {
            return of(0, Integer.signum(dy));
        }
        return of(Integer.signum(dx), Integer.signum(dy));
    }

    /** The direction for a unit delta; both components in -1..1 and not both zero. */
    public static Direction8 of(int dx, int dy) {
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || (dx == 0 && dy == 0)) {
            throw new IllegalArgumentException("no direction for " + dx + "," + dy);
        }
        return BY_DELTA[(dy + 1) * 3 + (dx + 1)];
    }
}
