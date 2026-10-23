package wulf.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Turns a {@link Framebuffer} of palette indices into an ARGB image at an
 * integer scale, nearest-neighbour (AGENTS.md §5.3).
 *
 * <p>Allocates its output image once and reuses it.
 *
 * <p>With scanlines on (§5.4, {@code [NEW]}, default off) every odd output row is
 * drawn from a second, darker copy of the palette — after the upscale, so the
 * lines are one output pixel tall at any scale.
 *
 * <p>With glow on (§5.4, {@code [NEW]}, default off) every lit pixel throws a soft halo
 * onto the darker ones around it, as a television's phosphor did. The halo is the picture
 * blurred at the canvas's own size (a five-tap binomial, across then down: about one
 * canvas pixel of spread), smoothed up to the output and taken where it is brighter, so
 * the pixels themselves keep their colour and their detail.
 */
public final class Scaler {

    private final int[] argbByIndex;
    private final int[] dimmedByIndex = new int[16];
    private boolean scanlines;
    private boolean glow;
    private int glowPercent;
    private int[] haloA;
    private int[] haloB;
    private final int srcW;
    private final int srcH;
    private int scale;
    private BufferedImage image;
    private int[] target;

    public Scaler(int[] argbByIndex, int srcW, int srcH, int scale) {
        this.argbByIndex = argbByIndex.clone();
        this.srcW = srcW;
        this.srcH = srcH;
        setScale(scale);
    }

    public void setScale(int scale) {
        if (scale == this.scale && image != null) {
            return;
        }
        this.scale = Math.max(1, scale);
        this.image = new BufferedImage(srcW * this.scale, srcH * this.scale, BufferedImage.TYPE_INT_RGB);
        this.target = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    /** Turns scanlines on or off; {@code luminancePercent} is what an odd row keeps of its brightness. */
    public void setScanlines(boolean on, int luminancePercent) {
        this.scanlines = on;
        int keep = Math.max(0, Math.min(100, luminancePercent));
        for (int i = 0; i < dimmedByIndex.length; i++) {
            dimmedByIndex[i] = dim(argbByIndex[i], keep);
        }
    }

    /** Turns glow on or off; {@code percent} is how bright the halo is of the pixel throwing it. */
    public void setGlow(boolean on, int percent) {
        this.glow = on;
        this.glowPercent = Math.max(0, Math.min(100, percent));
    }

    static int dim(int argb, int keepPercent) {
        int r = ((argb >> 16) & 0xFF) * keepPercent / 100;
        int g = ((argb >> 8) & 0xFF) * keepPercent / 100;
        int b = (argb & 0xFF) * keepPercent / 100;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    public int scale() {
        return scale;
    }

    public BufferedImage render(Framebuffer fb) {
        byte[] src = fb.pixels();
        int dstW = srcW * scale;
        for (int y = 0; y < srcH; y++) {
            int srcRow = y * srcW;
            int dstRowStart = y * scale * dstW;
            // Expand one source row, then copy it (scale - 1) more times.
            for (int x = 0; x < srcW; x++) {
                int argb = argbByIndex[src[srcRow + x] & 0x0F];
                int at = dstRowStart + x * scale;
                for (int i = 0; i < scale; i++) {
                    target[at + i] = argb;
                }
            }
            for (int r = 1; r < scale; r++) {
                System.arraycopy(target, dstRowStart, target, dstRowStart + r * dstW, dstW);
            }
            if (scanlines) {
                darkenOddRows(src, srcRow, y * scale, dstW);
            }
        }
        if (glow && glowPercent > 0) {
            addGlow(src);
        }
        return image;
    }

    /** The halo: blurred at the canvas's size, then smoothed up and lit in where it is brighter. */
    private void addGlow(byte[] src) {
        int n = srcW * srcH;
        if (haloA == null) {
            haloA = new int[n];
            haloB = new int[n];
        }
        for (int i = 0; i < n; i++) {
            haloA[i] = argbByIndex[src[i] & 0x0F] & 0xFFFFFF;
        }
        blur(haloA, haloB, 1, srcW);        // across
        blur(haloB, haloA, srcW, srcH);     // down
        int dstW = srcW * scale;
        int dstH = srcH * scale;
        for (int oy = 0; oy < dstH; oy++) {
            // Each output pixel's place on the canvas, in 256ths, from pixel centre to centre.
            int sy = Math.max(0, (oy * 2 + 1) * 128 / scale - 128);
            int y0 = Math.min(srcH - 1, sy >> 8);
            int y1 = Math.min(srcH - 1, y0 + 1);
            int wy = sy & 0xFF;
            int row = oy * dstW;
            for (int ox = 0; ox < dstW; ox++) {
                int sx = Math.max(0, (ox * 2 + 1) * 128 / scale - 128);
                int x0 = Math.min(srcW - 1, sx >> 8);
                int x1 = Math.min(srcW - 1, x0 + 1);
                int wx = sx & 0xFF;
                int a = haloA[y0 * srcW + x0];
                int b = haloA[y0 * srcW + x1];
                int c = haloA[y1 * srcW + x0];
                int d = haloA[y1 * srcW + x1];
                int px = target[row + ox];
                int out = 0xFF000000;
                for (int shift = 16; shift >= 0; shift -= 8) {
                    int top = ((a >> shift) & 0xFF) * (256 - wx) + ((b >> shift) & 0xFF) * wx;
                    int bottom = ((c >> shift) & 0xFF) * (256 - wx) + ((d >> shift) & 0xFF) * wx;
                    int h = (top * (256 - wy) + bottom * wy) >> 16;
                    h = h * glowPercent / 100;
                    out |= Math.max((px >> shift) & 0xFF, h) << shift;
                }
                target[row + ox] = out;
            }
        }
    }

    /** One pass of the 1-4-6-4-1 blur along lines {@code step} apart, {@code len} long, edges held. */
    private void blur(int[] from, int[] to, int step, int len) {
        int lines = srcW * srcH / len;
        for (int line = 0; line < lines; line++) {
            int base = step == 1 ? line * srcW : line;
            for (int i = 0; i < len; i++) {
                int p2 = from[base + Math.max(0, i - 2) * step];
                int p1 = from[base + Math.max(0, i - 1) * step];
                int p0 = from[base + i * step];
                int n1 = from[base + Math.min(len - 1, i + 1) * step];
                int n2 = from[base + Math.min(len - 1, i + 2) * step];
                int out = 0;
                for (int shift = 16; shift >= 0; shift -= 8) {
                    int v = ((p2 >> shift) & 0xFF) + 4 * ((p1 >> shift) & 0xFF) + 6 * ((p0 >> shift) & 0xFF)
                            + 4 * ((n1 >> shift) & 0xFF) + ((n2 >> shift) & 0xFF);
                    out |= (v >> 4) << shift;
                }
                to[base + i * step] = out;
            }
        }
    }

    /** Redraws the odd output rows of one source row's block from the darker palette. */
    private void darkenOddRows(byte[] src, int srcRow, int firstOutRow, int dstW) {
        for (int out = firstOutRow | 1; out < firstOutRow + scale; out += 2) {
            int at = out * dstW;
            for (int x = 0; x < srcW; x++) {
                int argb = dimmedByIndex[src[srcRow + x] & 0x0F];
                int from = at + x * scale;
                for (int i = 0; i < scale; i++) {
                    target[from + i] = argb;
                }
            }
        }
    }
}
