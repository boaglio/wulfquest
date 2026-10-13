package wulf.ui;

import java.util.List;
import wulf.data.DisplayConfig;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;
import wulf.render.Sprite;

/**
 * What a quarter brings up (AGENTS.md §14.6, the original's own interlude): the
 * playfield cleared, the amulet so far drawn large — only the quarters held, as
 * the original drew it, so the gaps show what is still out there — and under it
 * the verse for how many
 * that is. The panel stays, and so does the game beneath, standing still.
 */
final class AmuletRevealScreen {

    private final Chrome chrome;
    private final DisplayConfig.Playfield field;
    private final Sprite amulet;
    private final List<String> framesBySlot;
    private final int scale;

    AmuletRevealScreen(Chrome chrome, DisplayConfig display, Sprite amulet, List<String> framesBySlot,
                       ShellConfig.AmuletReveal reveal) {
        this.chrome = chrome;
        this.field = display.playfield();
        this.amulet = amulet;
        this.framesBySlot = List.copyOf(framesBySlot);
        this.scale = reveal.scale();
    }

    /** @param prompt "press fire", once fire will close it; null before then */
    void paint(Framebuffer fb, int slotMask, List<String> verse, String prompt) {
        paint(fb, slotMask, verse, prompt, framesBySlot);
    }

    /** @param frames the amulet's frame for each slot: a later job's own (§14.9) */
    void paint(Framebuffer fb, int slotMask, List<String> verse, String prompt, List<String> frames) {
        fb.fillRect(field.x(), field.y(), field.w(), field.h(), chrome.ground());
        // Four quarters, two across and two down, as the panel lays them out (§17.3).
        int piece = amulet.w() * scale;
        int size = 2 * piece;
        int lineH = chrome.big().lineHeight();
        // The amulet and the verse as one block, centred in the playfield.
        int block = size + lineH + (verse.size() + 1) * lineH;
        int top = field.y() + (field.h() - block) / 2;
        int left = field.x() + (field.w() - size) / 2;
        for (int slot = 0; slot < frames.size(); slot++) {
            if ((slotMask & (1 << slot)) != 0) {
                amulet.blitScaled(fb, frames.get(slot), left + (slot % 2) * piece, top + (slot / 2) * piece, scale);
            }
        }
        int y = top + size + lineH;
        for (String line : verse) {
            chrome.big().drawCentred(fb, line, field.x(), field.w(), y, chrome.body());
            y += lineH;
        }
        if (prompt != null) {
            chrome.big().drawCentred(fb, prompt, field.x(), field.w(), y, chrome.bright());
        }
    }
}
