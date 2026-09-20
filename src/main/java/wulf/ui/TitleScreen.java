package wulf.ui;

import java.util.List;
import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;

/**
 * The title screen (AGENTS.md §17.2): the wordmark in the 8x8 font with its
 * colour cycling down the rows, the prompt, the menu, and the attribution line.
 *
 * <p>The cycle walks one pixel row at a time through the colours named in
 * {@code shell.json}, so the wordmark reads as a single banded object rather
 * than letters blinking independently.
 */
public final class TitleScreen {

    /** The wordmark is drawn at this many times the 8x8 font, which is all the art it needs. */
    private static final int WORDMARK_SCALE = 2;

    private final Chrome chrome;
    private final ShellConfig.Title title;
    private final int[] cycle;

    public TitleScreen(Chrome chrome, ShellConfig.Title title, Palette palette) {
        this.chrome = chrome;
        this.title = title;
        this.cycle = new int[title.cycle().colours().size()];
        for (int i = 0; i < cycle.length; i++) {
            cycle[i] = palette.indexOf(title.cycle().colours().get(i));
        }
    }

    /**
     * @param tick   ticks the title has been up, which drives the cycle
     * @param notice a one-off line about the player database, or null (§20.8)
     */
    public void paint(Framebuffer fb, long tick, long best, String notice) {
        fb.clear(chrome.ground());
        int w = fb.width();
        int y = 36;
        drawWordmark(fb, title.wordmark(), y, tick);
        y += chrome.big().glyphH() * WORDMARK_SCALE + 10;
        chrome.small().drawCentred(fb, title.tagline(), 0, w, y, chrome.dim());

        y += chrome.small().lineHeight() * 4;
        chrome.small().drawCentred(fb, title.prompt(), 0, w, y, chrome.bright());

        y += chrome.small().lineHeight() * 2;
        for (ShellConfig.MenuItem item : title.menu()) {
            y += chrome.small().lineHeight() + 3;
            chrome.small().drawCentred(fb, item.label(), 0, w, y, chrome.body());
        }

        y += chrome.small().lineHeight() * 3;
        if (best > 0) {
            chrome.small().drawCentred(fb, "BEST " + best, 0, w, y, chrome.heading());
        }
        if (notice != null) {
            chrome.small().drawCentred(fb, notice, 0, w, fb.height() - 28, chrome.heading());
        }
        chrome.small().drawCentred(fb, title.footer(), 0, w, fb.height() - 14, chrome.dim());
    }

    /**
     * One colour per band of rows, scrolling down the letters: the wordmark is
     * drawn once per band through a one-row clip, so it reads as a single object
     * with light moving over it rather than letters blinking on their own.
     */
    private void drawWordmark(Framebuffer fb, String text, int top, long tick) {
        int h = chrome.big().glyphH() * WORDMARK_SCALE;
        int step = Math.floorMod((int) (tick / title.cycle().ticksPerStep()), cycle.length);
        for (int row = 0; row < h; row++) {
            int colour = cycle[Math.floorMod(row / WORDMARK_SCALE + step, cycle.length)];
            fb.setClip(0, top + row, fb.width(), 1);
            chrome.big().drawScaledCentred(fb, text, 0, fb.width(), top, colour, WORDMARK_SCALE);
        }
        fb.clearClip();
    }

    /** The first menu screen whose key was typed, or null. */
    public static String screenFor(List<ShellConfig.MenuItem> menu, wulf.input.MenuInput keys) {
        for (ShellConfig.MenuItem item : menu) {
            if (keys.typed(item.key())) {
                return item.screen();
            }
        }
        return null;
    }
}
