package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AGENTS.md §5.2 and the PaletteValidator rule from §20.6. */
class PaletteTest {

    private final Palette palette = new JsonDb(Path.of("data")).load("art/palette", Palette.class);

    @Test
    void hasSixteenUniqueIndices() {
        assertThat(palette.entries()).hasSize(Palette.SIZE);
        Set<Integer> seen = new HashSet<>();
        for (Palette.Entry e : palette.entries()) {
            assertThat(e.i()).isBetween(0, 15);
            assertThat(seen.add(e.i())).as("index %d is unique", e.i()).isTrue();
            assertThat(e.rgb()).matches("#[0-9A-Fa-f]{6}");
        }
    }

    @Test
    void transparentIsMinusOne() {
        assertThat(palette.transparent()).isEqualTo(-1);
    }

    @Test
    void resolvesToOpaqueArgb() {
        int[] argb = palette.toArgb();
        assertThat(argb).hasSize(Palette.SIZE);
        for (int v : argb) {
            assertThat(v >>> 24).as("alpha").isEqualTo(0xFF);
        }
        assertThat(argb[0]).isEqualTo(0xFF000000);
        assertThat(argb[15]).isEqualTo(0xFFFFFFFF);
    }

    @Test
    void namesResolveToIndices() {
        assertThat(palette.indexOf("black")).isEqualTo(0);
        assertThat(palette.indexOf("brightWhite")).isEqualTo(15);
        assertThatThrownBy(() -> palette.indexOf("puce"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void brightVariantsAreBrighterThanTheirBase() {
        int[] argb = palette.toArgb();
        // Skip black, whose bright variant is deliberately identical.
        for (int i = 1; i < 8; i++) {
            assertThat(luma(argb[i + 8])).as("bright %d brighter than %d", i + 8, i)
                    .isGreaterThan(luma(argb[i]));
        }
    }

    private static int luma(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return 2126 * r + 7152 * g + 722 * b;
    }
}
