package wulf.render;

import wulf.data.DisplayConfig;
import wulf.data.Palette;
import java.util.List;

/**
 * The status panel, drawn in the lower border (AGENTS.md §17.3).
 *
 * <p>Redrawn in full every tick — it is cheap, and dirty-rect logic is
 * explicitly forbidden (§23.3).
 */
public final class PanelPainter {

    private static final int SCORE_DIGITS = 7;
    /** A quarter is drawn at half size in the panel: 8x8, so the whole amulet fits rows 10-26 (§17.3). */
    private static final int AMULET_SLOT = 8;
    private static final int AMULET_TOP = 11;

    private final DisplayConfig.Rect panel;
    private final FontSet font;
    private final int inkNormal;
    private final int inkBright;
    private final int inkDim;
    private final int ground;
    private final Sprite amulet;
    private final List<String> frameBySlot;

    /**
     * @param amulet      the amulet sprite; may be null when there is no quest, and then the
     *                    slots are drawn as outlines
     * @param frameBySlot the amulet frame for each of the four slots, 0..3 reading across
     */
    public PanelPainter(DisplayConfig display, Fonts fonts, Palette palette, Sprite amulet, List<String> frameBySlot) {
        this.amulet = amulet;
        this.frameBySlot = List.copyOf(frameBySlot);
        this.panel = display.panel();
        this.font = fonts.small();
        this.inkNormal = palette.indexOf("white");
        this.inkBright = palette.indexOf("brightWhite");
        // Not "brightBlack": that is #000000, invisible on the panel ground.
        this.inkDim = palette.indexOf("white");
        this.ground = palette.indexOf("black");
    }

    /**
     * @param slotMask      bit {@code slot} set for every amulet quarter held
     * @param effectColour  palette index of the active orchid effect, or -1
     * @param effectFrac    remaining effect fraction, 0..1000 (per-mille, no floats)
     */
    public void paint(Framebuffer fb, long score, long hiScore, int lives,
                      int slotMask, int effectColour, int effectFrac, String message) {
        fb.fillRect(panel.x(), panel.y(), panel.w(), panel.h(), ground);

        int top = panel.y() + 2;
        font.draw(fb, "SCORE " + pad(score), panel.x() + 2, top, inkBright);
        String hi = "HI " + pad(hiScore);
        font.draw(fb, hi, panel.x() + panel.w() - 2 - font.widthOf(hi), top, inkNormal);

        drawLives(fb, lives, top + 10);
        drawAmulet(fb, slotMask);

        int barY = panel.y() + panel.h() - 10;
        if (message != null && !message.isEmpty()) {
            font.drawCentred(fb, message, panel.x(), panel.w(), barY, inkBright);
        } else if (effectColour >= 0) {
            drawEffectBar(fb, barY, effectColour, effectFrac);
        }
    }

    private void drawLives(Framebuffer fb, int lives, int y) {
        String label = "LIVES";
        int x = panel.x() + 2;
        font.draw(fb, label, x, y, inkNormal);
        x += font.widthOf(label) + 3;
        int icons = Math.min(lives, 5);
        for (int i = 0; i < icons; i++) {
            // A head: 4x5 dot of ink, spaced 6px. Real sprite arrives in M3.
            fb.fillRect(x, y, 4, 5, inkBright);
            fb.set(x + 1, y + 1, ground);
            fb.set(x + 2, y + 1, ground);
            x += 6;
        }
        if (lives > 5) {
            font.draw(fb, "X" + lives, x, y, inkNormal);
        }
    }

    /**
     * The amulet assembling (§14.6, §17.3): held quarters in their own colours, missing
     * ones as silhouettes in {@code white} — never {@code brightBlack}, which is black.
     */
    private void drawAmulet(Framebuffer fb, int slotMask) {
        for (int slot = 0; slot < 4; slot++) {
            boolean held = (slotMask & (1 << slot)) != 0;
            if (amulet == null || slot >= frameBySlot.size()) {
                fb.drawRect(slotX(slot), slotY(slot), AMULET_SLOT, AMULET_SLOT, held ? inkBright : inkDim);
            } else {
                amulet.blitHalf(fb, frameBySlot.get(slot), slotX(slot), slotY(slot), held ? -1 : inkDim);
            }
        }
    }

    /** Top-left of a quarter's slot, in canvas pixels: where a quarter flies to (§14.6). */
    public int slotX(int slot) {
        return panel.x() + (panel.w() - 2 * AMULET_SLOT) / 2 + (slot % 2) * AMULET_SLOT;
    }

    public int slotY(int slot) {
        return panel.y() + AMULET_TOP + (slot / 2) * AMULET_SLOT;
    }

    private void drawEffectBar(Framebuffer fb, int y, int colour, int perMille) {
        int w = 160;
        int x = panel.x() + (panel.w() - w) / 2;
        fb.drawRect(x, y, w, 6, inkDim);
        int fill = Math.max(0, Math.min(w - 2, (w - 2) * perMille / 1000));
        fb.fillRect(x + 1, y + 1, fill, 4, colour);
    }

    private static String pad(long value) {
        String s = Long.toString(Math.max(0, value));
        return "0".repeat(Math.max(0, SCORE_DIGITS - s.length())) + s;
    }
}
