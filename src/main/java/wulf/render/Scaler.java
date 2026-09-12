package wulf.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Turns a {@link Framebuffer} of palette indices into an ARGB image at an
 * integer scale, nearest-neighbour (AGENTS.md §5.3).
 *
 * <p>Allocates its output image once and reuses it.
 */
public final class Scaler {

    private final int[] argbByIndex;
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
        }
        return image;
    }
}
