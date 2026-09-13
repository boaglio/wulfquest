package wulf.input;

/**
 * One tick's input, snapshotted once and immutable (AGENTS.md §19.2). The
 * simulation reads only this — never an AWT event — so recorded input replays
 * identically.
 *
 * <p>{@code *Pressed} fields are key-down edges since the previous sample;
 * the others are levels.
 */
public record InputState(
        boolean up,
        boolean down,
        boolean left,
        boolean right,
        boolean fire,
        boolean firePressed,
        boolean pausePressed,
        boolean quitPressed,
        boolean devKillPressed,
        boolean devMaskPressed,
        boolean devWulfPressed) {

    public static final InputState NONE = new InputState(false, false, false, false, false, false, false, false,
            false, false, false);

    /** Horizontal intent: -1, 0 or 1. Opposing keys cancel (§11.3). */
    public int dx() {
        return (right ? 1 : 0) - (left ? 1 : 0);
    }

    /** Vertical intent: -1 up, 0, 1 down. */
    public int dy() {
        return (down ? 1 : 0) - (up ? 1 : 0);
    }

    /** Directions held, plus fire level and fire edge — for tests and replays. */
    public static InputState of(int dx, int dy, boolean fire, boolean firePressed) {
        return new InputState(dy < 0, dy > 0, dx < 0, dx > 0, fire, firePressed, false, false, false, false, false);
    }
}
