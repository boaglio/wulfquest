package wulf.ui;

import wulf.data.HighScores;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;

/**
 * The hi-score table (AGENTS.md §21.2). A row just entered is drawn bright, so
 * a player coming off a good run can see where they landed.
 */
public final class HiScoreScreen {

    /** Where the quarters column sits, measured in from the right edge of the score column. */
    private static final int PIECES_FROM_RIGHT = 60;

    private final Chrome chrome;
    private final ShellConfig.HiScores config;
    private final String easyMark;
    private final int easyColour;

    public HiScoreScreen(Chrome chrome, ShellConfig.HiScores config) {
        this(chrome, config, ShellConfig.Difficulty.NONE.hiScoreMark(), chrome.body());
    }

    /** @param easyMark drawn after the name of a run in Easy mode (§17.8), in {@code easyColour} */
    public HiScoreScreen(Chrome chrome, ShellConfig.HiScores config, String easyMark, int easyColour) {
        this.chrome = chrome;
        this.config = config;
        this.easyMark = easyMark;
        this.easyColour = easyColour;
    }

    /** @param highlight the row to draw bright, or -1 */
    public void paint(Framebuffer fb, HighScores scores, int highlight) {
        int y = chrome.page(fb, config.heading());
        int left = fb.width() / 2 - 88;
        int right = fb.width() / 2 + 88;
        int i = 0;
        for (HighScores.Entry e : scores.ranked(config.size()).entries()) {
            int ink = i == highlight ? chrome.bright() : chrome.body();
            chrome.small().draw(fb, rank(i + 1), left, y, chrome.dim());
            chrome.small().draw(fb, e.name(), left + 20, y, ink);
            if (e.easy()) {
                // A column of its own after the longest name, so the marks line up (§17.8).
                int at = left + 20 + chrome.small().widthOf("W".repeat(config.nameLength())) + 6;
                chrome.small().draw(fb, easyMark, at, y, easyColour);
            }
            String score = Long.toString(e.score());
            chrome.small().draw(fb, score, right - chrome.small().widthOf(score), y, ink);
            // A fixed column: the quarters must not shuffle sideways with the score's width.
            chrome.small().draw(fb, e.pieces() + "/4", right - PIECES_FROM_RIGHT, y, chrome.dim());
            y += chrome.small().lineHeight() * 2;
            i++;
        }
        chrome.footer(fb, "PRESS FIRE");
    }

    private static String rank(int place) {
        return (place < 10 ? " " : "") + place + ".";
    }
}
