package wulf.sim;

/**
 * A group moving as one: the members share a direction and reflect together
 * (AGENTS.md §12.5, HERD_BOUNCE).
 */
public final class Herd {

    /** For creatures that belong to no herd. Only herd behaviours mutate a herd, and they never get this one. */
    public static final Herd NONE = new Herd();

    private int dirX;
    private int dirY;
    private long lastRollTick = -1;

    Herd() {
    }

    public int dirX() {
        return dirX;
    }

    public int dirY() {
        return dirY;
    }

    public boolean hasDirection() {
        return dirX != 0 || dirY != 0;
    }

    public void direction(int dx, int dy) {
        dirX = dx;
        dirY = dy;
    }

    /** True for the first member to ask on a given tick: the herd rolls its random reversal once, not per member. */
    public boolean claimRoll(long tick) {
        if (lastRollTick == tick) {
            return false;
        }
        lastRollTick = tick;
        return true;
    }
}
