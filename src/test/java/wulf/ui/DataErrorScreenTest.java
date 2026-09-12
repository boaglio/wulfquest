package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.data.DataException;
import wulf.data.FontData;
import wulf.data.JsonDb;
import wulf.data.Palette;
import wulf.render.Fonts;
import wulf.render.Framebuffer;

/** AGENTS.md §17.1: a data error must be readable on screen. */
class DataErrorScreenTest {

    private final JsonDb db = new JsonDb(Path.of("data"));

    @Test
    void wrapsOnWordsThenOnCharacters() {
        assertThat(DataErrorScreen.wrap("ONE TWO THREE", 7)).containsExactly("ONE TWO", "THREE");
        assertThat(DataErrorScreen.wrap("UNBREAKABLETOKEN", 5))
                .containsExactly("UNBRE", "AKABL", "ETOKE", "N");
    }

    @Test
    void keepsTheIdentifyingTailOfALongPath() {
        assertThat(DataErrorScreen.shortenPath("/home/someone/deep/tree/data/config/game.json"))
                .isEqualTo("data/config/game.json");
        assertThat(DataErrorScreen.shortenPath("classpath:data/config/game.json"))
                .isEqualTo("classpath:data/config/game.json");
    }

    @Test
    void paintsTheFilePointerAndReason() {
        Palette palette = db.load("art/palette", Palette.class);
        Fonts fonts = new Fonts("font.json", db.load("art/font/font", FontData.class));
        Framebuffer fb = new Framebuffer(320, 256);
        DataException error = new DataException(
                "/x/data/art/palette.json", "/entries/3/rgb", "fails its schema: not a colour");

        new DataErrorScreen(fonts.small(), palette).paint(fb, error);

        // Something was drawn, in more than one colour, over the whole panel area.
        assertThat(fb.get(0, 0)).isEqualTo(palette.indexOf("blue"));
        int distinct = 0;
        boolean[] seen = new boolean[16];
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                int v = fb.get(x, y);
                if (!seen[v]) {
                    seen[v] = true;
                    distinct++;
                }
            }
        }
        assertThat(distinct).isGreaterThanOrEqualTo(4);
    }
}
