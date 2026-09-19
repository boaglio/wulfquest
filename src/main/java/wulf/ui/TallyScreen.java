package wulf.ui;

import wulf.data.Palette;
import wulf.render.FontSet;
import wulf.render.Framebuffer;
import wulf.sim.Quest;

/**
 * The {@code TALLY} state (AGENTS.md §14.7, §17.1): the escape, added up. Hi-score
 * entry and the title screen that follow it arrive in M8; until then fire starts a
 * new game.
 */
public final class TallyScreen {

    private final FontSet font;
    private final int ground;
    private final int heading;
    private final int label;
    private final int figure;

    public TallyScreen(FontSet font, Palette palette) {
        this.font = font;
        this.ground = palette.indexOf("black");
        this.heading = palette.indexOf("brightYellow");
        this.label = palette.indexOf("white");
        this.figure = palette.indexOf("brightWhite");
    }

    /** @param reveal ticks since the tally began: lines appear one at a time */
    public void paint(Framebuffer fb, Quest quest, long total, long best, int reveal) {
        fb.clear(ground);
        int w = fb.width();
        int y = 48;
        font.drawCentred(fb, "YOU ESCAPED THE JUNGLE", 0, w, y, heading);
        y += font.lineHeight() * 3;
        String[] labels = {"COLLECTED", "ESCAPE BONUS", "TIME BONUS", "LIVES BONUS"};
        long[] values = {quest.scoreBeforeBonuses(), quest.escapeBonus(), quest.timeBonus(), quest.livesBonus()};
        int left = w / 2 - 80;
        int right = w / 2 + 80;
        for (int i = 0; i < labels.length && reveal >= i * 25; i++) {
            line(fb, labels[i], values[i], left, right, y, label);
            y += font.lineHeight() * 2;
        }
        if (reveal >= labels.length * 25) {
            y += font.lineHeight();
            line(fb, "TOTAL", total, left, right, y, heading);
            y += font.lineHeight() * 2;
            if (total >= best) {
                font.drawCentred(fb, "A NEW BEST", 0, w, y, figure);
            }
            font.drawCentred(fb, "PRESS FIRE", 0, w, fb.height() - 24, figure);
        }
    }

    private void line(Framebuffer fb, String name, long value, int left, int right, int y, int ink) {
        font.draw(fb, name, left, y, ink);
        String v = Long.toString(value);
        font.draw(fb, v, right - font.widthOf(v), y, figure);
    }
}
