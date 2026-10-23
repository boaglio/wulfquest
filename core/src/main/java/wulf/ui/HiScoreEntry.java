package wulf.ui;

import wulf.data.ShellConfig;
import wulf.input.InputState;

/**
 * The classic three-letter selector (AGENTS.md §21.2): up and down walk the
 * alphabet, left and right move between letters, fire sets one and, on the
 * last, commits the name.
 *
 * <p>Headless and deterministic — no clock, no AWT — so the whole thing is
 * testable by feeding it {@link InputState}s.
 */
public final class HiScoreEntry {

    private final ShellConfig.HiScores config;
    private final int[] letters;
    private int cursor;
    private int held;
    private int repeatDir;
    private boolean prevLeft;
    private boolean prevRight;
    private boolean done;

    public HiScoreEntry(ShellConfig.HiScores config) {
        this.config = config;
        this.letters = new int[config.nameLength()];
    }

    /** One tick of the selector. Returns true once the name is committed. */
    public boolean tick(InputState in) {
        if (done) {
            return true;
        }
        int dir = (in.down() ? 1 : 0) - (in.up() ? 1 : 0);
        if (dir != 0 && dir == repeatDir) {
            held++;
            if (held >= config.repeatDelayTicks() && (held - config.repeatDelayTicks()) % config.repeatEveryTicks() == 0) {
                step(dir);
            }
        } else if (dir != 0) {
            repeatDir = dir;
            held = 0;
            step(dir);
        } else {
            repeatDir = 0;
            held = 0;
        }
        if (in.left() && !prevLeft) {
            moveCursor(-1);
        }
        if (in.right() && !prevRight) {
            moveCursor(1);
        }
        prevLeft = in.left();
        prevRight = in.right();
        if (in.firePressed()) {
            if (++cursor >= letters.length) {
                cursor = letters.length - 1;
                done = true;
            }
        }
        return done;
    }

    /** Left and right move the cursor without setting a letter. */
    public void moveCursor(int delta) {
        cursor = Math.max(0, Math.min(letters.length - 1, cursor + delta));
    }

    private void step(int dir) {
        int n = config.alphabet().length();
        letters[cursor] = Math.floorMod(letters[cursor] + dir, n);
    }

    public String name() {
        StringBuilder sb = new StringBuilder(letters.length);
        for (int letter : letters) {
            sb.append(config.alphabet().charAt(letter));
        }
        return sb.toString();
    }

    public int cursor() {
        return cursor;
    }

    public boolean done() {
        return done;
    }
}
