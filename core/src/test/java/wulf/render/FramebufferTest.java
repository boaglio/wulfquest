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
    void theClipLimitsSetAndFillRectButNotClear() {
        Framebuffer fb = new Framebuffer(8, 8);
        fb.clear(0);
        fb.setClip(2, 2, 4, 4);
        fb.fillRect(0, 0, 8, 8, 5);
        fb.set(0, 0, 7);
        fb.set(7, 7, 7);
        assertThat(fb.get(1, 1)).isZero();
        assertThat(fb.get(2, 2)).isEqualTo(5);
        assertThat(fb.get(5, 5)).isEqualTo(5);
        assertThat(fb.get(6, 6)).isZero();
        assertThat(fb.get(0, 0)).isZero();
        fb.clear(9);
        assertThat(fb.get(0, 0)).as("clear ignores the clip").isEqualTo(9);
        fb.clearClip();
        fb.set(0, 0, 4);
        assertThat(fb.get(0, 0)).isEqualTo(4);
    }

    @Test
    void aRectWhollyOutsideTheClipOrTheBufferDrawsNothingAndDoesNotThrow() {
        Framebuffer fb = new Framebuffer(8, 8);
        fb.clear(0);
        String before = fb.hash();
        fb.setClip(4, 4, 4, 4);
        fb.fillRect(0, 0, 2, 2, 5);     // left of and above the clip
        fb.fillRect(0, 5, 3, 1, 5);     // left of the clip only
        fb.clearClip();
        fb.fillRect(-5, 2, 3, 2, 5);    // left of the buffer
        fb.fillRect(9, 2, 3, 2, 5);     // right of the buffer
        fb.fillRect(2, -4, 2, 2, 5);    // above the buffer
        fb.fillRect(2, 2, 0, 3, 5);     // zero width
        assertThat(fb.hash()).isEqualTo(before);
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
}
