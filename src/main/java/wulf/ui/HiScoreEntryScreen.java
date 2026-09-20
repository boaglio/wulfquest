package wulf.ui;

import wulf.data.ShellConfig;
import wulf.render.Framebuffer;

/** The name being entered (AGENTS.md §21.2), drawn big with the cursor blinking under it. */
public final class HiScoreEntryScreen {

    /** The name is the thing on this page: drawn at this many times the 8x8 font. */
    private static final int NAME_SCALE = 3;

    private final Chrome chrome;
    private final ShellConfig.HiScores config;

    public HiScoreEntryScreen(Chrome chrome, ShellConfig.HiScores config) {
        this.chrome = chrome;
        this.config = config;
    }

    public void paint(Framebuffer fb, HiScoreEntry entry, long score, int place, long tick) {
        int y = chrome.page(fb, config.entryHeading());
        chrome.small().drawCentred(fb, "SCORE " + score + "   PLACE " + (place + 1), 0, fb.width(), y, chrome.body());

        String name = entry.name();
        int advance = chrome.big().advance() * NAME_SCALE;
        int x = (fb.width() - advance * name.length()) / 2;
        int nameY = y + chrome.small().lineHeight() * 6;
        chrome.big().drawScaled(fb, name, x, nameY, chrome.bright(), NAME_SCALE);

        // The cursor blinks so a space — a legal letter — is still visibly where you are.
        if (tick / config.cursorBlinkTicks() % 2 == 0) {
            fb.fillRect(x + entry.cursor() * advance, nameY + chrome.big().glyphH() * NAME_SCALE - NAME_SCALE,
                    advance - NAME_SCALE * 2, NAME_SCALE, chrome.heading());
        }
        chrome.footer(fb, config.entryPrompt());
    }
}
