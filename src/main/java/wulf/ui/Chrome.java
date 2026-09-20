package wulf.ui;

import wulf.data.Palette;
import wulf.render.FontSet;
import wulf.render.Fonts;
import wulf.render.Framebuffer;

/**
 * What every shell page shares (AGENTS.md §17): the two fonts, the four inks,
 * and a heading drawn in the same place each time so the pages feel like one
 * screen rather than seven.
 */
public final class Chrome {

    /** The 8x8 set (§10.4), used for headings and the wordmark. */
    public static final String BIG = "big";

    private final FontSet big;
    private final FontSet small;
    private final int ground;
    private final int heading;
    private final int body;
    private final int bright;
    private final int dim;

    public Chrome(Fonts fonts, Palette palette) {
        this.big = fonts.get(BIG);
        this.small = fonts.small();
        this.ground = palette.indexOf("black");
        this.heading = palette.indexOf("brightYellow");
        this.body = palette.indexOf("white");
        this.bright = palette.indexOf("brightWhite");
        this.dim = palette.indexOf("cyan");
    }

    public FontSet big() {
        return big;
    }

    public FontSet small() {
        return small;
    }

    public int ground() {
        return ground;
    }

    public int heading() {
        return heading;
    }

    public int body() {
        return body;
    }

    public int bright() {
        return bright;
    }

    public int dim() {
        return dim;
    }

    /** Clears to the ground and draws a heading; returns the first free row under it. */
    public int page(Framebuffer fb, String title) {
        fb.clear(ground);
        int y = 16;
        big.drawCentred(fb, title, 0, fb.width(), y, heading);
        return y + big.lineHeight() + 8;
    }

    /** The line every page ends on. */
    public void footer(Framebuffer fb, String text) {
        small.drawCentred(fb, text, 0, fb.width(), fb.height() - 14, bright);
    }
}
