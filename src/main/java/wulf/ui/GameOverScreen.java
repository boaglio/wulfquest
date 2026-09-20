package wulf.ui;

import wulf.data.ShellConfig;
import wulf.render.Framebuffer;

/**
 * {@code GAME OVER} in the 8x8 font over the frozen playfield (AGENTS.md
 * §17.5). The jungle stays visible underneath — the point is that it is still
 * there, and you are not.
 */
public final class GameOverScreen {

    private final Chrome chrome;
    private final ShellConfig.GameOver config;

    public GameOverScreen(Chrome chrome, ShellConfig.GameOver config) {
        this.chrome = chrome;
        this.config = config;
    }

    /** Paints over whatever is already in the framebuffer; does not clear it. */
    public void paint(Framebuffer fb, int playfieldY, int playfieldH) {
        int textH = chrome.big().glyphH();
        int y = playfieldY + (playfieldH - textH) / 2;
        int band = textH + 8;
        fb.fillRect(0, y - 4, fb.width(), band, chrome.ground());
        chrome.big().drawCentred(fb, config.text(), 0, fb.width(), y, chrome.heading());
    }
}
