package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AGENTS.md §5.3: the framebuffer is palette indices, and it clips. */
class FramebufferTest {

    @Test
    void clearFillsEveryPixel() {
        Framebuffer fb = new Framebuffer(8, 4);
        fb.clear(6);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 8; x++) {
                assertThat(fb.get(x, y)).isEqualTo(6);
            }
        }
    }

    @Test
    void fillRectClipsToBounds() {
        Framebuffer fb = new Framebuffer(8, 8);
        fb.clear(0);
        fb.fillRect(-4, -4, 6, 6, 3);
        assertThat(fb.get(0, 0)).isEqualTo(3);
        assertThat(fb.get(1, 1)).isEqualTo(3);
        assertThat(fb.get(2, 2)).isEqualTo(0);
        fb.fillRect(6, 6, 10, 10, 5);
        assertThat(fb.get(7, 7)).isEqualTo(5);
    }

    @Test
    void setOutsideBoundsIsIgnored() {
        Framebuffer fb = new Framebuffer(4, 4);
        fb.clear(0);
        fb.set(-1, 0, 9);
        fb.set(0, -1, 9);
        fb.set(4, 0, 9);
        fb.set(0, 4, 9);
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                assertThat(fb.get(x, y)).isEqualTo(0);
            }
        }
    }

    @Test
    void drawRectOutlinesOnly() {
        Framebuffer fb = new Framebuffer(6, 6);
        fb.clear(0);
        fb.drawRect(1, 1, 4, 4, 7);
        assertThat(fb.get(1, 1)).isEqualTo(7);
        assertThat(fb.get(4, 4)).isEqualTo(7);
        assertThat(fb.get(2, 2)).isEqualTo(0);
    }

    @Test
    void hashIsStableAndSensitive() {
        Framebuffer a = new Framebuffer(16, 16);
        Framebuffer b = new Framebuffer(16, 16);
        a.clear(1);
        b.clear(1);
        assertThat(a.hash()).isEqualTo(b.hash()).hasSize(16);
        b.set(3, 3, 2);
        assertThat(a.hash()).isNotEqualTo(b.hash());
    }

    @Test
    void scalerProducesIntegerScaledArgb() {
        Framebuffer fb = new Framebuffer(4, 2);
        fb.clear(0);
        fb.set(1, 0, 15);
        int[] argb = new int[16];
        argb[0] = 0xFF000000;
        argb[15] = 0xFFFFFFFF;
        Scaler scaler = new Scaler(argb, 4, 2, 3);
        var img = scaler.render(fb);
        assertThat(img.getWidth()).isEqualTo(12);
        assertThat(img.getHeight()).isEqualTo(6);
        // The single lit pixel became a 3x3 block at (3,0).
        assertThat(img.getRGB(3, 0)).isEqualTo(0xFFFFFFFF);
        assertThat(img.getRGB(5, 2)).isEqualTo(0xFFFFFFFF);
        assertThat(img.getRGB(6, 0)).isEqualTo(0xFF000000);
    }
}
