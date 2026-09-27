package wulf.render;

import wulf.data.DisplayConfig;

/**
 * Spectrum colour attributes, on purpose (AGENTS.md §5.4, {@code [NEW]}, default
 * off): every 8x8 cell of the playfield gets one paper and one ink, and the ink
 * is whatever was drawn into the cell last — so Vale walking through the
 * undergrowth turns it his colour, as it would have in 1984. Faithful, ugly, fun.
 *
 * <p>The framebuffer must be {@link Framebuffer#trackInk tracking ink} with the
 * same paper before the frame is drawn.
 */
public final class AttributeClash {

    private final DisplayConfig.Playfield field;

    public AttributeClash(DisplayConfig.Playfield field) {
        this.field = field;
    }

    /** Repaints every non-paper pixel of each playfield cell in that cell's ink. */
    public void apply(Framebuffer fb) {
        int cell = field.cell();
        for (int cy = 0; cy < field.rows(); cy++) {
            for (int cx = 0; cx < field.cols(); cx++) {
                int left = field.x() + cx * cell;
                int top = field.y() + cy * cell;
                int ink = fb.ink(left >> 3, top >> 3);
                if (ink < 0) {
                    continue;   // nothing but paper was drawn here
                }
                for (int y = top; y < top + cell; y++) {
                    for (int x = left; x < left + cell; x++) {
                        if (!fb.paper(fb.get(x, y))) {
                            fb.set(x, y, ink);
                        }
                    }
                }
            }
        }
    }
}
