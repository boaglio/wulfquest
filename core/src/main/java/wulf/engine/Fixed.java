package wulf.engine;

/**
 * 8.8 fixed-point arithmetic (AGENTS.md §6.2): one pixel is 256. There is no
 * float or double anywhere in the simulation, so a run is bit-identical on
 * every JVM and platform.
 */
public final class Fixed {

    public static final int SHIFT = 8;
    public static final int ONE = 1 << SHIFT;

    private Fixed() {
    }

    /** Whole pixels, rounding toward negative infinity. */
    public static int px(int fp) {
        return fp >> SHIFT;
    }

    public static int fp(int pixels) {
        return pixels << SHIFT;
    }

    /** a * b for two fixed-point values. */
    public static int mul(int a, int b) {
        return (int) (((long) a * b) >> SHIFT);
    }
}
