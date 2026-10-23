package wulf.ui;

import wulf.data.ShellConfig;
import wulf.render.Framebuffer;

/**
 * {@code GAME OVER} in the 8x8 font over the frozen playfield (AGENTS.md
 * §17.5), and under it how much of the adventure was done, as the original
 * showed it. The jungle stays visible underneath — the point is that it is
 * still there, and you are not.
 */
public final class GameOverScreen {

    private final Chrome chrome;
    private final ShellConfig.GameOver config;

    public GameOverScreen(Chrome chrome, ShellConfig.GameOver config) {
        this.chrome = chrome;
        this.config = config;
    }

    /** Paints over whatever is already in the framebuffer; does not clear it. */
    public void paint(Framebuffer fb, int playfieldY, int playfieldH, int percent) {
        paint(fb, playfieldY, playfieldH, percent, 0, 0);
    }

    /**
     * @param continuesLeft §17.5: how many more times the game may go on; with any, the offer
     *                      shows under the progress
     * @param secondsLeft   until the offer is gone
     */
    public void paint(Framebuffer fb, int playfieldY, int playfieldH, int percent, int continuesLeft,
                      int secondsLeft) {
        int textH = chrome.big().glyphH();
        int lineH = chrome.big().lineHeight();
        int lines = continuesLeft > 0 ? 6 : 2;
        int countdown = continuesLeft > 0 ? lineH + textH * config.countdownScale() : 0;
        int y = playfieldY + (playfieldH - textH - lines * lineH - countdown) / 2;
        fb.fillRect(0, y - 4, fb.width(), textH + lines * lineH + countdown + 8, chrome.ground());
        chrome.big().drawCentred(fb, config.text(), 0, fb.width(), y, chrome.heading());
        chrome.big().drawCentred(fb, progress(config, percent), 0, fb.width(), y + 2 * lineH, chrome.body());
        if (continuesLeft > 0) {
            chrome.big().drawCentred(fb, config.continuePrompt(), 0, fb.width(), y + 4 * lineH, chrome.bright());
            chrome.big().drawCentred(fb, left(config, continuesLeft), 0, fb.width(), y + 6 * lineH, chrome.body());
            // The seconds, big: the offer will not wait.
            chrome.big().drawScaledCentred(fb, Integer.toString(secondsLeft), 0, fb.width(), y + 6 * lineH + 2 * lineH,
                    chrome.heading(), config.countdownScale());
        }
    }

    static String left(ShellConfig.GameOver config, int continuesLeft) {
        return config.continuesLeft().replace("{n}", Integer.toString(continuesLeft));
    }

    static String progress(ShellConfig.GameOver config, int percent) {
        return config.progress().replace("{percent}", Integer.toString(percent));
    }

    /**
     * How much of the adventure is done (§17.5), the original's own sum ($9CC6): every
     * room visited and every quarter held, out of every room and every quarter, as a whole
     * percent rounded down. All 256 rooms and all four quarters make 100.
     */
    public static int percentDone(int roomsVisited, int piecesHeld, int rooms, int pieces) {
        return (roomsVisited + piecesHeld) * 100 / (rooms + pieces);
    }
}
