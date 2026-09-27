package wulf.render;

import java.util.Arrays;

/**
 * The screen, as palette indices (AGENTS.md §5.3). One byte per pixel.
 *
 * <p>No {@code Graphics2D} primitive ever touches game pixels: everything is
 * written here, and the whole buffer is upscaled once per frame. Index -1 in
 * source art means "skip", so it never appears in the buffer itself.
 */
public final class Framebuffer {

    private final int width;
    private final int height;
    private final byte[] pixels;

    // Drawing clip: set() and fillRect() never write outside it. clear() ignores it.
    private int clipX0;
    private int clipY0;
    private int clipX1;
    private int clipY1;

    // Attribute clash (§5.4): the last non-paper colour written into each 8x8 cell, or -1.
    // Null — and free — unless someone asked for it.
    private byte[] cellInk;
    private int paper = -1;

    public Framebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new byte[width * height];
        this.clipX1 = width;
        this.clipY1 = height;
    }

    /** Restricts drawing to a rectangle — e.g. the playfield, so sprites never paint the border. */
    public void setClip(int x, int y, int w, int h) {
        clipX0 = Math.max(0, x);
        clipY0 = Math.max(0, y);
        clipX1 = Math.min(width, x + w);
        clipY1 = Math.min(height, y + h);
    }

    public void clearClip() {
        clipX0 = 0;
        clipY0 = 0;
        clipX1 = width;
        clipY1 = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Direct access for the scaler; callers must not retain or mutate it. */
    public byte[] pixels() {
        return pixels;
    }

    public void clear(int paletteIndex) {
        Arrays.fill(pixels, (byte) paletteIndex);
        if (cellInk != null) {
            Arrays.fill(cellInk, (byte) -1);
        }
    }

    /**
     * Starts remembering, per 8x8 cell, the last colour drawn into it that is not
     * {@code paperIndex} (either brightness) — the ink a Spectrum attribute would
     * end up holding. Forgotten on every {@link #clear}.
     */
    public void trackInk(int paperIndex) {
        this.paper = paperIndex & 7;
        this.cellInk = new byte[cellsAcross() * cellsDown()];
        Arrays.fill(cellInk, (byte) -1);
    }

    public boolean tracksInk() {
        return cellInk != null;
    }

    /** Whether a colour is the tracked paper, in either brightness. */
    public boolean paper(int paletteIndex) {
        return (paletteIndex & 7) == paper;
    }

    /** The last ink drawn into cell ({@code cx}, {@code cy}) since the last clear, or -1. */
    public int ink(int cx, int cy) {
        return cellInk == null ? -1 : cellInk[cy * cellsAcross() + cx];
    }

    private int cellsAcross() {
        return (width + 7) / 8;
    }

    private int cellsDown() {
        return (height + 7) / 8;
    }

    public void set(int x, int y, int paletteIndex) {
        if (x < clipX0 || y < clipY0 || x >= clipX1 || y >= clipY1) {
            return;
        }
        pixels[y * width + x] = (byte) paletteIndex;
        if (cellInk != null && (paletteIndex & 7) != paper) {
            cellInk[(y >> 3) * cellsAcross() + (x >> 3)] = (byte) paletteIndex;
        }
    }

    public int get(int x, int y) {
        return pixels[y * width + x] & 0xFF;
    }

    public void fillRect(int x, int y, int w, int h, int paletteIndex) {
        int x0 = Math.max(clipX0, x);
        int y0 = Math.max(clipY0, y);
        int x1 = Math.min(clipX1, x + w);
        int y1 = Math.min(clipY1, y + h);
        if (x0 >= x1 || y0 >= y1) {
            // Wholly outside the clip. Arrays.fill would throw on the reversed range —
            // a blade drawn pixel by pixel off the playfield edge hit exactly this.
            return;
        }
        byte v = (byte) paletteIndex;
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * width;
            Arrays.fill(pixels, row + x0, row + x1, v);
        }
        if (cellInk != null && (paletteIndex & 7) != paper) {
            for (int cy = y0 >> 3; cy <= (y1 - 1) >> 3; cy++) {
                for (int cx = x0 >> 3; cx <= (x1 - 1) >> 3; cx++) {
                    cellInk[cy * cellsAcross() + cx] = v;
                }
            }
        }
    }

    /**
     * Drops a rectangle to the non-bright half of the palette — the dimmed
     * playfield behind the attract demo (AGENTS.md §17.2) and under PAUSE
     * (§17.4). Indexed, so this is a mask, not a blend.
     */
    public void dim(int x, int y, int w, int h) {
        int x0 = Math.max(clipX0, x);
        int y0 = Math.max(clipY0, y);
        int x1 = Math.min(clipX1, x + w);
        int y1 = Math.min(clipY1, y + h);
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                pixels[py * width + px] &= 7;
            }
        }
    }

    /** Paints every other row of a rectangle, which reads as a darker picture still. */
    public void stripe(int x, int y, int w, int h, int paletteIndex) {
        for (int py = y; py < y + h; py += 2) {
            fillRect(x, py, w, 1, paletteIndex);
        }
    }

    public void drawRect(int x, int y, int w, int h, int paletteIndex) {
        fillRect(x, y, w, 1, paletteIndex);
        fillRect(x, y + h - 1, w, 1, paletteIndex);
        fillRect(x, y, 1, h, paletteIndex);
        fillRect(x + w - 1, y, 1, h, paletteIndex);
    }

    /** FNV-1a over the whole buffer — the golden-frame hash (AGENTS.md §22.5). */
    public String hash() {
        long h = 0xcbf29ce484222325L;
        for (byte p : pixels) {
            h ^= (p & 0xFF);
            h *= 0x100000001b3L;
        }
        return String.format("%016x", h);
    }
}
