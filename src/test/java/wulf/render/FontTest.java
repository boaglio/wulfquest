package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.data.FontData;
import wulf.data.JsonDb;

/** AGENTS.md §10.4 and the SpriteSizeValidator / AnimationValidator rules of §20.6. */
class FontTest {

    private final JsonDb db = new JsonDb(Path.of("data"));
    private final Fonts fonts = new Fonts("font.json", db.load("art/font/font", FontData.class));

    @Test
    void panelFontHasTheExpectedMetrics() {
        FontSet f = fonts.small();
        assertThat(f.name()).isEqualTo("panel-4x6");
        assertThat(f.glyphW()).isEqualTo(4);
        assertThat(f.glyphH()).isEqualTo(6);
        assertThat(f.advance()).isEqualTo(4);
        assertThat(f.widthOf("SCORE")).isEqualTo(20);
        assertThat(f.widthOf("")).isZero();
    }

    @Test
    void coversEverythingThePanelAndErrorScreenNeed() {
        FontSet f = fonts.small();
        Framebuffer fb = new Framebuffer(320, 8);
        String needed = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:!?'\"-+()/%";
        for (char c : needed.toCharArray()) {
            fb.clear(0);
            f.draw(fb, String.valueOf(c), 0, 0, 1);
            boolean blank = " ".indexOf(c) >= 0;
            assertThat(anyLit(fb)).as("glyph '%c' is drawn", c).isNotEqualTo(blank);
        }
    }

    @Test
    void unknownCharactersFallBack() {
        FontSet f = fonts.small();
        Framebuffer a = new Framebuffer(8, 8);
        Framebuffer b = new Framebuffer(8, 8);
        a.clear(0);
        b.clear(0);
        f.draw(a, "ç", 0, 0, 1);   // c-cedilla: not in the set
        f.draw(b, "?", 0, 0, 1);
        assertThat(a.hash()).isEqualTo(b.hash());
    }

    @Test
    void isCaseInsensitive() {
        FontSet f = fonts.small();
        Framebuffer upper = new Framebuffer(32, 8);
        Framebuffer lower = new Framebuffer(32, 8);
        upper.clear(0);
        lower.clear(0);
        f.draw(upper, "SCORE", 0, 0, 1);
        f.draw(lower, "score", 0, 0, 1);
        assertThat(upper.hash()).isEqualTo(lower.hash());
    }

    @Test
    void drawsInTheRequestedColourOnly() {
        FontSet f = fonts.small();
        Framebuffer fb = new Framebuffer(8, 8);
        fb.clear(0);
        f.draw(fb, "I", 0, 0, 12);
        boolean sawInk = false;
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int v = fb.get(x, y);
                assertThat(v).isIn(0, 12);
                sawInk |= v == 12;
            }
        }
        assertThat(sawInk).isTrue();
    }

    @Test
    void glyphsStayWithinTheirCellSoTextDoesNotCollide() {
        FontSet f = fonts.small();
        Framebuffer fb = new Framebuffer(16, 8);
        fb.clear(0);
        f.draw(fb, "M", 0, 0, 1);
        // The 4th column and 6th row are the gutters: nothing may be drawn there.
        for (int y = 0; y < 6; y++) {
            assertThat(fb.get(3, y)).as("right gutter at row %d", y).isZero();
        }
        for (int x = 0; x < 4; x++) {
            assertThat(fb.get(x, 5)).as("bottom gutter at col %d", x).isZero();
        }
    }

    private static boolean anyLit(Framebuffer fb) {
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                if (fb.get(x, y) != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void theDisplayFontHasTheExpectedMetrics() {
        FontSet f = fonts.get("big");
        assertThat(f.name()).isEqualTo("display-8x8");
        assertThat(f.glyphW()).isEqualTo(8);
        assertThat(f.glyphH()).isEqualTo(8);
        assertThat(f.advance()).isEqualTo(8);
        assertThat(f.widthOf("WULF QUEST")).isEqualTo(80);
    }

    @Test
    void theDisplayFontCoversEverythingTheShellShows() {
        FontSet f = fonts.get("big");
        Framebuffer fb = new Framebuffer(16, 16);
        String needed = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 .,:!?'-/()";
        for (char c : needed.toCharArray()) {
            fb.clear(0);
            f.draw(fb, String.valueOf(c), 0, 0, 1);
            assertThat(anyLit(fb)).as("glyph '%c' is drawn", c).isNotEqualTo(c == ' ');
        }
    }

    @Test
    void displayGlyphsStayInsideTheirCell() {
        FontSet f = fonts.get("big");
        Framebuffer fb = new Framebuffer(16, 16);
        for (char c = 'A'; c <= 'Z'; c++) {
            fb.clear(0);
            f.draw(fb, String.valueOf(c), 0, 0, 1);
            for (int y = 0; y < 8; y++) {
                assertThat(fb.get(7, y)).as("'%c' right gutter at row %d", c, y).isZero();
            }
            for (int x = 0; x < 8; x++) {
                assertThat(fb.get(x, 7)).as("'%c' bottom gutter at column %d", c, x).isZero();
            }
        }
    }
}
