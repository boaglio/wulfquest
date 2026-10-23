package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AGENTS.md §5.3 and §5.4: the window's picture, scaled up, with scanlines and glow. */
class ScalerTest {

    private static int[] greyRamp() {
        int[] argb = new int[16];
        for (int i = 0; i < 16; i++) {
            argb[i] = 0xFF000000 | (i * 16) << 16 | (i * 16) << 8 | (i * 16);
        }
        argb[15] = 0xFFFFFFFF;
        return argb;
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

    // ------------------------------------------------------------------ glow

    @Test
    void glowLightsTheDarkAroundALitPixelAndLeavesThePixelItself() {
        Framebuffer fb = new Framebuffer(9, 9);
        fb.clear(0);
        fb.set(4, 4, 15);
        Scaler scaler = new Scaler(greyRamp(), 9, 9, 3);
        var off = scaler.render(fb);
        assertThat(off.getRGB(4 * 3 + 4, 4 * 3 + 1) & 0xFFFFFF).as("off: the dark stays dark").isZero();
        scaler.setGlow(true, 80);
        var on = scaler.render(fb);
        assertThat(on.getRGB(4 * 3 + 1, 4 * 3 + 1)).as("the pixel keeps its colour").isEqualTo(0xFFFFFFFF);
        int next = on.getRGB(5 * 3 + 1, 4 * 3 + 1) & 0xFF;
        int far = on.getRGB(7 * 3 + 1, 4 * 3 + 1) & 0xFF;
        assertThat(next).as("beside it, a halo").isGreaterThan(0);
        assertThat(far).as("fainter further off").isLessThan(next);
        assertThat(on.getRGB(0, 0) & 0xFFFFFF).as("and nothing far away").isZero();
    }

    @Test
    void glowNeverDarkensAPixel() {
        Framebuffer fb = new Framebuffer(6, 6);
        for (int i = 0; i < 36; i++) {
            fb.set(i % 6, i / 6, i % 16);
        }
        Scaler scaler = new Scaler(greyRamp(), 6, 6, 2);
        var plain = scaler.render(fb);
        int[] before = plain.getRGB(0, 0, 12, 12, null, 0, 12);
        scaler.setGlow(true, 80);
        int[] after = scaler.render(fb).getRGB(0, 0, 12, 12, null, 0, 12);
        for (int i = 0; i < before.length; i++) {
            assertThat(after[i] & 0xFF).isGreaterThanOrEqualTo(before[i] & 0xFF);
        }
    }

    // ------------------------------------------------------------------ scanlines

    @Test
    void scanlinesDarkenEveryOddOutputRowAndNothingElse() {
        Framebuffer fb = new Framebuffer(2, 2);
        fb.clear(15);
        Scaler scaler = new Scaler(greyRamp(), 2, 2, 3);
        scaler.setScanlines(true, 82);
        var img = scaler.render(fb);
        int dim = Scaler.dim(0xFFFFFFFF, 82);
        assertThat(dim).isEqualTo(0xFFD1D1D1);   // 255 * 82 / 100 = 209
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                assertThat(img.getRGB(x, y)).as("(%d,%d)", x, y).isEqualTo(y % 2 == 1 ? dim : 0xFFFFFFFF);
            }
        }
    }

    @Test
    void scanlinesAreOneOutputRowTallAtScaleOneToo() {
        Framebuffer fb = new Framebuffer(1, 4);
        fb.clear(15);
        Scaler scaler = new Scaler(greyRamp(), 1, 4, 1);
        scaler.setScanlines(true, 50);
        var img = scaler.render(fb);
        assertThat(img.getRGB(0, 0)).isEqualTo(0xFFFFFFFF);
        assertThat(img.getRGB(0, 1)).isEqualTo(0xFF7F7F7F);
        assertThat(img.getRGB(0, 2)).isEqualTo(0xFFFFFFFF);
        assertThat(img.getRGB(0, 3)).isEqualTo(0xFF7F7F7F);
    }

    @Test
    void turningScanlinesOffRestoresTheCleanPicture() {
        Framebuffer fb = new Framebuffer(2, 2);
        fb.clear(15);
        Scaler scaler = new Scaler(greyRamp(), 2, 2, 2);
        scaler.setScanlines(true, 82);
        scaler.render(fb);
        scaler.setScanlines(false, 82);
        assertThat(scaler.render(fb).getRGB(0, 1)).isEqualTo(0xFFFFFFFF);
    }
}
