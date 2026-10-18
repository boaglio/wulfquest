package wulf.render;

import wulf.data.DisplayConfig;

/**
 * Spectrum colour attributes, the way the original used them (AGENTS.md §5.4,
 * {@code [NEW]}, default off): every 8x8 cell of the playfield shows two colours.
 *
 * <p><b>The scenery</b> ({@link #scenery}, once the room is drawn): the original's room data
 * gives each cell of foliage a paper colour and black ink (the attribute tables of
 * {@code Room_*}: {@code $60} bright green paper, {@code $70} yellow, {@code $58} magenta…),
 * so its jungle is blocks of colour with black detail. Here a cell's paper is the colour most
 * of its scenery pixels are; the pixels of that hue, bright or plain (one attribute holds
 * either), show it, and every other pixel of the cell is black.
 *
 * <p><b>The sprites</b> ({@link #apply}, once everything is drawn): the original XORs them
 * into the bitmap ({@code $BA14}) and writes their colour only into cells whose paper is
 * black ({@code $BB45}: {@code AND %00111000}, {@code JR NZ}). So in the open a cell takes
 * the sprite's colour — the one most of its pixels there are drawn in — and over the
 * foliage the sprite is a black shape cut out of the foliage's colour, which keeps it.
 * A sprite's own black outline is not drawn: the original's sprites have none.
 */
public final class AttributeClash {

    private static final int COLOURS = 16;

    private final DisplayConfig.Playfield field;
    private final int black;
    /** The scenery as drawn, before any sprite: a palette index per playfield pixel. */
    private byte[] room = new byte[0];
    /** Each cell's paper, or -1 for open ground (black paper). */
    private int[] paper = new int[0];

    public AttributeClash(DisplayConfig.Playfield field, int blackIndex) {
        this.field = field;
        this.black = blackIndex;
    }

    /** Takes the room as it stands — scenery and flowers, nothing that moves — and gives each cell its paper. */
    public void scenery(Framebuffer fb) {
        int w = field.w();
        int h = field.h();
        if (room.length != w * h) {
            room = new byte[w * h];
            paper = new int[field.cols() * field.rows()];
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                room[y * w + x] = (byte) fb.get(field.x() + x, field.y() + y);
            }
        }
        int[] count = new int[COLOURS];
        for (int cy = 0; cy < field.rows(); cy++) {
            for (int cx = 0; cx < field.cols(); cx++) {
                java.util.Arrays.fill(count, 0);
                forCell(cx, cy, (x, y) -> count[colour(room[y * w + x])]++);
                paper[cy * field.cols() + cx] = mostHue(count);
            }
        }
    }

    /** Repaints the playfield in two colours a cell, over the scenery {@link #scenery} took. */
    public void apply(Framebuffer fb) {
        if (paper.length == 0) {
            return;
        }
        int w = field.w();
        int cell = field.cell();
        int[] count = new int[COLOURS];
        for (int cy = 0; cy < field.rows(); cy++) {
            for (int cx = 0; cx < field.cols(); cx++) {
                int p = paper[cy * field.cols() + cx];
                int left = field.x() + cx * cell;
                int top = field.y() + cy * cell;
                // The ink an open cell takes: what most of the sprites' pixels in it are.
                java.util.Arrays.fill(count, 0);
                for (int y = 0; y < cell; y++) {
                    for (int x = 0; x < cell; x++) {
                        int now = fb.get(left + x, top + y);
                        if (sprite(now, room[(top - field.y() + y) * w + left - field.x() + x])) {
                            count[colour(now)]++;
                        }
                    }
                }
                int ink = p < 0 ? most(count) : black;
                for (int y = 0; y < cell; y++) {
                    for (int x = 0; x < cell; x++) {
                        int was = colour(room[(top - field.y() + y) * w + left - field.x() + x]);
                        boolean set = p >= 0 && (was & 7) != (p & 7);   // the scenery's black detail
                        set ^= sprite(fb.get(left + x, top + y), was);  // XORed by a sprite
                        fb.set(left + x, top + y, set ? (ink < 0 ? black : ink) : (p < 0 ? black : p));
                    }
                }
            }
        }
    }

    /** Whether a sprite drew this pixel, in a colour: something other than the room, and not black. */
    private boolean sprite(int now, int room) {
        return colour(now) != colour(room) && (colour(now) & 7) != (black & 7);
    }

    /**
     * The hue of the most pixels counted, plain and bright together, black aside — in whichever
     * of its two brightnesses has more of them; -1 for none.
     */
    private int mostHue(int[] count) {
        int best = -1;
        int bestTotal = 0;
        for (int hue = 0; hue < COLOURS / 2; hue++) {
            int total = count[hue] + count[hue + COLOURS / 2];
            if (hue != (black & 7) && total > bestTotal) {
                best = count[hue + COLOURS / 2] >= count[hue] ? hue + COLOURS / 2 : hue;
                bestTotal = total;
            }
        }
        return best;
    }

    /** The colour of the most pixels counted, black aside; -1 for none. */
    private int most(int[] count) {
        int best = -1;
        for (int c = 0; c < COLOURS; c++) {
            if ((c & 7) != (black & 7) && count[c] > 0 && (best < 0 || count[c] > count[best])) {
                best = c;
            }
        }
        return best;
    }

    private static int colour(int index) {
        return index & (COLOURS - 1);
    }

    private void forCell(int cx, int cy, java.util.function.BiConsumer<Integer, Integer> each) {
        int cell = field.cell();
        for (int y = cy * cell; y < (cy + 1) * cell; y++) {
            for (int x = cx * cell; x < (cx + 1) * cell; x++) {
                each.accept(x, y);
            }
        }
    }
}
