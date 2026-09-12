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

    public Framebuffer(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new byte[width * height];
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
    }

    public void set(int x, int y, int paletteIndex) {
        if (x < 0 || y < 0 || x >= width || y >= height) {
            return;
        }
        pixels[y * width + x] = (byte) paletteIndex;
    }

    public int get(int x, int y) {
        return pixels[y * width + x] & 0xFF;
    }

    public void fillRect(int x, int y, int w, int h, int paletteIndex) {
        int x0 = Math.max(0, x);
        int y0 = Math.max(0, y);
        int x1 = Math.min(width, x + w);
        int y1 = Math.min(height, y + h);
        byte v = (byte) paletteIndex;
        for (int yy = y0; yy < y1; yy++) {
            int row = yy * width;
            Arrays.fill(pixels, row + x0, row + x1, v);
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
