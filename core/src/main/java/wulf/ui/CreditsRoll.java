package wulf.ui;

import java.util.List;
import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.FontSet;
import wulf.render.Framebuffer;

/**
 * The credits rolled up the screen after a win (AGENTS.md §14.7, {@code [NEW]}): the same
 * heading and lines as the credits page, between {@code roll.before} and {@code roll.after},
 * a pixel every {@code ticksPerPx}, until the last row is gone off the top. Under it, on a
 * band of its own, {@code roll.hint}, or {@code roll.paused} while it stands still.
 */
final class CreditsRoll {

    /** Layout, not a game number: the space round the line under the roll. */
    private static final int BAND_PAD_PX = 3;

    private final ShellConfig.Credits credits;
    private final FontSet big;
    private final FontSet small;
    private final int ground;
    private final int heading;
    private final int body;

    CreditsRoll(ShellConfig.Credits credits, FontSet big, FontSet small, Palette palette) {
        this.credits = credits;
        this.big = big;
        this.small = small;
        this.ground = palette.indexOf("black");
        this.heading = palette.indexOf(credits.roll().headingColour());
        this.body = palette.indexOf(credits.roll().bodyColour());
    }

    void paint(Framebuffer fb, long t) {
        paint(fb, t, credits.roll().theEnd(), false);
    }

    /**
     * @param theEnd the first row: the job won's own (§14.7)
     * @param paused whether the player has stopped it to read
     */
    void paint(Framebuffer fb, long t, String theEnd, boolean paused) {
        List<String> rows = credits.rollRows(theEnd);
        fb.clear(ground);
        ShellConfig.Roll roll = credits.roll();
        int y = roll.fromY() - (int) (t / roll.ticksPerPx());
        // The rows from credits.lines are the body; the rest — before, the heading, after — are titles.
        int bodyFrom = 1 + roll.before().size() + 2;
        int bodyTo = bodyFrom + credits.lines().size();
        for (int i = 0; i < rows.size(); i++, y += roll.lineHeightPx()) {
            if (y < -roll.lineHeightPx() || y >= fb.height()) {
                continue;
            }
            boolean isBody = i >= bodyFrom && i < bodyTo;
            FontSet font = isBody ? small : big;
            font.drawCentred(fb, rows.get(i), 0, fb.width(), y, isBody ? body : heading);
        }
        int band = small.lineHeight() + 2 * BAND_PAD_PX;
        int top = fb.height() - band;
        fb.fillRect(0, top, fb.width(), band, ground);
        small.drawCentred(fb, paused ? roll.paused() : roll.hint(), 0, fb.width(), top + BAND_PAD_PX,
                paused ? heading : body);
    }
}
