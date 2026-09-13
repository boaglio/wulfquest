package wulf.sim;

/**
 * Ranger Vale's simulation state (AGENTS.md §11). Mutated only by
 * {@link Simulation}; everyone else reads it.
 *
 * <p>Positions are the sprite origin — bottom-centre, the feet — in 8.8 fixed
 * point, in <em>world</em> pixels (0..4096 by 0..3072). Using world rather than
 * room coordinates is what makes a room flip preserve the cross-edge coordinate
 * exactly (§7.5): the player never moves when the screen changes.
 */
public final class Player {

    public enum Mode { ALIVE, DYING, DOWN, GAME_OVER }

    int xFp;
    int yFp;
    Direction8 facing = Direction8.S;
    int walkTicks;
    int swingTick = -1;
    int cooldown;
    int lives;
    int invulnTicks;
    Mode mode = Mode.ALIVE;
    int modeTick;
    int entryXFp;
    int entryYFp;

    Player() {
    }

    public int xFp() {
        return xFp;
    }

    public int yFp() {
        return yFp;
    }

    public Direction8 facing() {
        return facing;
    }

    /** Consecutive ticks of actual movement; zero the tick the player stops (§11.5). */
    public int walkTicks() {
        return walkTicks;
    }

    /** Ticks into the current swing, or -1 when not swinging (§11.6). */
    public int swingTick() {
        return swingTick;
    }

    public boolean swinging() {
        return swingTick >= 0;
    }

    public int cooldown() {
        return cooldown;
    }

    public int lives() {
        return lives;
    }

    public int invulnTicks() {
        return invulnTicks;
    }

    public Mode mode() {
        return mode;
    }

    /** Ticks spent in the current non-ALIVE mode. */
    public int modeTick() {
        return modeTick;
    }

    /** Where the player stood on entering the current room: the respawn anchor (§11.7). */
    public int entryXFp() {
        return entryXFp;
    }

    public int entryYFp() {
        return entryYFp;
    }
}
