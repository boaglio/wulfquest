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
 */
public final class Scaler {

    private final int[] argbByIndex;
    private final int[] dimmedByIndex = new int[16];
    private boolean scanlines;
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
        return image;
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
