package wulf.input;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Per-tick input for replays (AGENTS.md §22.6), run-length encoded as
 * {@code [ticks, bits]} pairs. Only what the simulation obeys is kept: the
 * directions, fire and its edge, and the two dev actions a dev-mode recording may
 * contain. Pause, quit and the mask toggle never reach the simulation.
 */
public final class RecordedInput {

    public static final int UP = 1;
    public static final int DOWN = 2;
    public static final int LEFT = 4;
    public static final int RIGHT = 8;
    public static final int FIRE = 16;
    public static final int FIRE_PRESSED = 32;
    public static final int DEV_KILL = 64;
    public static final int DEV_WULF = 128;
    private static final int ALL_BITS = 255;

    private final List<int[]> runs = new ArrayList<>();
    private int ticks;

    public static int encode(InputState in) {
        return (in.up() ? UP : 0) | (in.down() ? DOWN : 0) | (in.left() ? LEFT : 0) | (in.right() ? RIGHT : 0)
                | (in.fire() ? FIRE : 0) | (in.firePressed() ? FIRE_PRESSED : 0)
                | (in.devKillPressed() ? DEV_KILL : 0) | (in.devWulfPressed() ? DEV_WULF : 0);
    }

    public static InputState decode(int bits) {
        return new InputState((bits & UP) != 0, (bits & DOWN) != 0, (bits & LEFT) != 0, (bits & RIGHT) != 0,
                (bits & FIRE) != 0, (bits & FIRE_PRESSED) != 0, false, false,
                (bits & DEV_KILL) != 0, false, (bits & DEV_WULF) != 0);
    }

    /** @param runs {@code [ticks >= 1, bits 0..255]} pairs, as stored in a replay file */
    public static RecordedInput of(List<int[]> runs) {
        RecordedInput r = new RecordedInput();
        for (int i = 0; i < runs.size(); i++) {
            int[] run = runs.get(i);
            if (run.length != 2 || run[0] < 1 || run[1] < 0 || run[1] > ALL_BITS) {
                throw new IllegalArgumentException("input run " + i + " must be [ticks >= 1, bits 0..255], got "
                        + Arrays.toString(run));
            }
            r.runs.add(run.clone());
            r.ticks += run[0];
        }
        return r;
    }

    public void append(InputState in) {
        int bits = encode(in);
        if (!runs.isEmpty() && runs.get(runs.size() - 1)[1] == bits) {
            runs.get(runs.size() - 1)[0]++;
        } else {
            runs.add(new int[] {1, bits});
        }
        ticks++;
    }

    public int ticks() {
        return ticks;
    }

    /** A copy of the runs. */
    public List<int[]> runs() {
        List<int[]> copy = new ArrayList<>(runs.size());
        for (int[] run : runs) {
            copy.add(run.clone());
        }
        return copy;
    }

    public Cursor cursor() {
        return new Cursor();
    }

    /** Plays the recording back one tick at a time. */
    public final class Cursor {

        private int run;
        private int used;

        private Cursor() {
        }

        public boolean hasNext() {
            return run < runs.size();
        }

        public InputState next() {
            if (!hasNext()) {
                throw new NoSuchElementException("the recording has " + ticks + " ticks");
            }
            int[] r = runs.get(run);
            InputState in = decode(r[1]);
            if (++used >= r[0]) {
                run++;
                used = 0;
            }
            return in;
        }
    }
}
