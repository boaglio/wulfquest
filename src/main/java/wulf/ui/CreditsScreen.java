package wulf.ui;

import wulf.data.ShellConfig;
import wulf.render.Framebuffer;

/**
 * The credits page (AGENTS.md §17.2), which carries the attribution of §2.5
 * verbatim. Every line comes from {@code shell.json}: nothing about who made
 * this game is compiled in.
 */
public final class CreditsScreen {

    private final Chrome chrome;
    private final ShellConfig.Credits credits;

    public CreditsScreen(Chrome chrome, ShellConfig.Credits credits) {
        this.chrome = chrome;
        this.credits = credits;
    }

    public void paint(Framebuffer fb) {
        int y = chrome.page(fb, credits.heading());
        for (String line : credits.lines()) {
            chrome.small().drawCentred(fb, line, 0, fb.width(), y, chrome.body());
            y += chrome.small().lineHeight();
        }
    }
}
