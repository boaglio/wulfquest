package wulf.sim;

/** A thrown spear (AGENTS.md §12.5, PATROL_THROW). Its position is its point, in world 8.8 fixed point. */
public final class Spear {

    private final int id;
    private final int dirX;
    private final int dirY;
    int xFp;
    int yFp;
    int ticks;
    boolean alive = true;

    Spear(int id, int xFp, int yFp, int dirX, int dirY) {
        this.id = id;
        this.xFp = xFp;
        this.yFp = yFp;
        this.dirX = dirX;
        this.dirY = dirY;
    }

    public int id() {
        return id;
    }

    public int xFp() {
        return xFp;
    }

    public int yFp() {
        return yFp;
    }

    public int dirX() {
        return dirX;
    }

    public int dirY() {
        return dirY;
    }

    public boolean alive() {
        return alive;
    }
}
