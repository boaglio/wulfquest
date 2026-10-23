package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.Content;

/** AGENTS.md §14.6, §17.3 — the amulet assembling in the panel. */
class PanelAmuletTest {

    @Test
    void heldQuartersShowTheirColourAndMissingOnesAWhiteSilhouette() {
        Content c = Content.load(Path.of("data"));
        SpriteBank sprites = new SpriteBank(c.sprites());
        PanelPainter panel = new PanelPainter(c.display(), new Fonts("art/font/font.json", c.font()), c.palette(),
                sprites.get(c.landmarks().amulet().sprite()), QuestPainter.framesBySlot(c.landmarks()));
        Framebuffer fb = new Framebuffer(c.display().canvas().w(), c.display().canvas().h());
        panel.paint(fb, 0, 0, 3, 0b0001, -1, 0, null);

        int white = c.palette().indexOf("white");
        int black = c.palette().indexOf("black");
        assertThat(inks(fb, panel.slotX(0), panel.slotY(0))).as("slot 0 held").anyMatch(i -> i != white && i != black);
        for (int slot = 1; slot < 4; slot++) {
            assertThat(inks(fb, panel.slotX(slot), panel.slotY(slot))).as("slot %d missing", slot)
                    .containsOnly(white, black).contains(white);
        }
    }

    private static java.util.List<Integer> inks(Framebuffer fb, int x0, int y0) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        for (int y = y0; y < y0 + 8; y++) {
            for (int x = x0; x < x0 + 8; x++) {
                out.add(fb.get(x, y));
            }
        }
        return out;
    }
}
