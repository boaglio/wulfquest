package wulf.ui;

import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.FontSet;
import wulf.render.Framebuffer;
import wulf.sim.Quest;

/**
 * The {@code TALLY} state (AGENTS.md §14.7, §17.1): the escape, added up. It comes after
 * the closing scene and before the credits roll; every word and colour is
 * {@code shell.json → tally}'s.
 */
public final class TallyScreen {

    /** Layout, not a game number: the heading's line, the columns either side of centre, the prompt's line. */
    private static final int TOP_PX = 48;
    private static final int HALF_COLUMNS_PX = 80;
    private static final int PROMPT_FROM_BOTTOM_PX = 24;

    private final FontSet font;
    private final ShellConfig.Tally config;
    private final int ground;
    private final int heading;
    private final int label;
    private final int figure;

    public TallyScreen(FontSet font, Palette palette, ShellConfig.Tally config) {
        this.font = font;
        this.config = config;
        this.ground = palette.indexOf("black");
        this.heading = palette.indexOf(config.headingColour());
        this.label = palette.indexOf(config.labelColour());
        this.figure = palette.indexOf(config.figureColour());
    }

    /** @param reveal ticks since the tally began: lines appear one at a time */
    public void paint(Framebuffer fb, Quest quest, long total, long best, int reveal) {
        fb.clear(ground);
        int w = fb.width();
        int y = TOP_PX;
        font.drawCentred(fb, config.heading(), 0, w, y, heading);
        y += font.lineHeight() * 3;
        long[] values = {quest.scoreBeforeBonuses(), quest.escapeBonus(), quest.timeBonus(), quest.livesBonus()};
        int left = w / 2 - HALF_COLUMNS_PX;
        int right = w / 2 + HALF_COLUMNS_PX;
        int every = config.lineEveryTicks();
        for (int i = 0; i < values.length && reveal >= i * every; i++) {
            line(fb, config.labels().get(i), values[i], left, right, y, label);
            y += font.lineHeight() * 2;
        }
        if (reveal >= values.length * every) {
            y += font.lineHeight();
            line(fb, config.total(), total, left, right, y, heading);
            y += font.lineHeight() * 2;
            if (total >= best) {
                font.drawCentred(fb, config.newBest(), 0, w, y, figure);
            }
            font.drawCentred(fb, config.prompt(), 0, w, fb.height() - PROMPT_FROM_BOTTOM_PX, figure);
        }
    }

    private void line(Framebuffer fb, String name, long value, int left, int right, int y, int ink) {
        font.draw(fb, name, left, y, ink);
        String v = Long.toString(value);
        font.draw(fb, v, right - font.widthOf(v), y, figure);
    }
}
