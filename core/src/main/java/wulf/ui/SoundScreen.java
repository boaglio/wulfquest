package wulf.ui;

import wulf.data.Settings;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;

/**
 * The sound page (AGENTS.md §21.3): on or off, and how loud. Two settings, and
 * both are written the moment they change, so the next run starts the way the
 * last one ended.
 */
public final class SoundScreen {

    /** The volume bar's size on screen; the number it shows comes from settings.json. */
    private static final int BAR_W = 120;
    private static final int BAR_H = 8;

    private final Chrome chrome;
    private final ShellConfig.Sound config;

    public SoundScreen(Chrome chrome, ShellConfig.Sound config) {
        this.chrome = chrome;
        this.config = config;
    }

    public void paint(Framebuffer fb, Settings settings) {
        int y = chrome.page(fb, config.heading());
        boolean on = settings.audio().enabled();
        chrome.small().drawCentred(fb, on ? config.onLabel() : config.offLabel(), 0, fb.width(), y,
                on ? chrome.bright() : chrome.body());

        y += chrome.small().lineHeight() * 4;
        String label = config.volumeLabel() + " " + settings.audio().volumePercent() + "%";
        chrome.small().drawCentred(fb, label, 0, fb.width(), y, on ? chrome.body() : chrome.dim());

        y += chrome.small().lineHeight() * 2;
        int x = (fb.width() - BAR_W) / 2;
        fb.drawRect(x, y, BAR_W, BAR_H, on ? chrome.body() : chrome.dim());
        int fill = (BAR_W - 2) * settings.audio().volumePercent() / 100;
        fb.fillRect(x + 1, y + 1, fill, BAR_H - 2, on ? chrome.heading() : chrome.dim());

        chrome.footer(fb, config.prompt());
    }
}
