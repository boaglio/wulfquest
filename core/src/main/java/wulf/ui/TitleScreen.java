package wulf.ui;

import java.util.List;
import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;
import wulf.render.Sprite;

/**
 * The title screen (AGENTS.md §17.2): the wordmark in the 8x8 font, a word to a line and
 * its colour cycling down the rows, its tagline, a short menu, and the attribution line.
 *
 * <p>The cycle walks one pixel row at a time through the colours named in
 * {@code shell.json}, so the wordmark reads as a single banded object rather
 * than letters blinking independently.
 */
public final class TitleScreen {

    /** The wordmark is drawn at this many times the 8x8 font, which is all the art it needs. */
    private static final int WORDMARK_SCALE = 5;
    /** Layout, not game numbers: where the wordmark and the menu start, and the gap between its words. */
    private static final int WORDMARK_Y = 18;
    private static final int WORDMARK_GAP = 6;
    private static final int MENU_Y = 132;

    private final Chrome chrome;
    private final ShellConfig.Title title;
    private final int[] cycle;
    private final int jobColour;
    private final ShellConfig.Difficulty difficulty;
    private final int easyColour;
    private final Sprite marker;

    public TitleScreen(Chrome chrome, ShellConfig.Title title, Palette palette, ShellConfig.NewGamePlus jobs,
                       ShellConfig.Difficulty difficulty, Sprite marker) {
        this.marker = marker;
        this.difficulty = difficulty;
        this.easyColour = palette.indexOf(difficulty.easyColour());
        this.chrome = chrome;
        this.title = title;
        this.jobColour = palette.indexOf(jobs.labelColour());
        this.cycle = new int[title.cycle().colours().size()];
        for (int i = 0; i < cycle.length; i++) {
            cycle[i] = palette.indexOf(title.cycle().colours().get(i));
        }
    }

    /** The wordmark and tagline alone, with no menu: what shows when a demo cannot. */
    public void paint(Framebuffer fb, long tick, String notice) {
        paint(fb, tick, notice, List.of(), -1, null, false, null);
    }

    /**
     * @param tick   ticks the title has been up, which drives the cycle
     * @param notice a one-off line about the player database, or null (§20.8)
     * @param lines  the menu as the shell has it ({@link Shell#titleLines()}), and {@code cursor} the
     *               line chosen
     * @param saved  the saved game {@code continue} goes on with, as its slot reads on the load page,
     *               or null; it goes under that line
     * @param easy   whether the next game is in Easy mode (§17.8): said under {@code start}
     * @param job    the job {@code start} begins, once a later one is open (§14.9): under the
     *               difficulty, between arrows that say left and right change it, and its tagline,
     *               if it has one, replaces the title's; null before
     */
    public void paint(Framebuffer fb, long tick, String notice, List<ShellConfig.Entry> lines, int cursor,
                      String saved, boolean easy, ShellConfig.Job job) {
        fb.clear(chrome.ground());
        int w = fb.width();
        int y = WORDMARK_Y;
        for (String word : title.wordmark().split(" ")) {
            drawWordmark(fb, word, y, tick);
            y += chrome.big().glyphH() * WORDMARK_SCALE + WORDMARK_GAP;
        }
        y += 4;
        String tagline = job != null && job.tagline() != null ? job.tagline() : title.tagline();
        chrome.small().drawCentred(fb, tagline, 0, w, y, chrome.dim());

        y = MENU_Y;
        int small = chrome.small().lineHeight() + 2;
        for (int i = 0; i < lines.size(); i++) {
            ShellConfig.Entry line = lines.get(i);
            boolean chosen = i == cursor;
            MenuScreen.drawLine(fb, chrome.big(), chosen ? marker : null, title.marker().frame(), line.label(),
                    0, w, y, chosen ? chrome.bright() : chrome.body());
            y += chrome.big().lineHeight() + 2;
            if (line.id().equals("continue") && saved != null) {
                chrome.small().drawCentred(fb, saved, 0, w, y, chrome.dim());
                y += small;
            } else if (line.id().equals("start")) {
                chrome.small().drawCentred(fb, easy ? difficulty.easy() : difficulty.hard(), 0, w, y,
                        easy ? easyColour : chrome.dim());
                y += small;
                if (job != null) {
                    chrome.small().drawCentred(fb, "< " + job.label() + " >", 0, w, y, jobColour);
                    y += small;
                }
            }
            y += 4;
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
