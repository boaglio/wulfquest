package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** AGENTS.md §10.1: sprite decoding, mirroring, and readable failures. */
class SpriteDataTest {

    private static SpriteData sprite(List<String> rows, Map<String, SpriteData.Mirror> mirror) {
        return new SpriteData(1, "t", new SpriteData.Size(3, 2), new SpriteData.Origin(0, 0),
                Map.of(".", -1, "a", 3, "b", 12), List.of(new SpriteData.Frame("f0", rows)), mirror);
    }

    @Test
    void decodesLegendCharsToPaletteIndices() {
        byte[] px = sprite(List.of("ab.", "..a"), Map.of()).decode("t").get("f0");
        assertThat(px).containsExactly(3, 12, -1, -1, -1, 3);
    }

    @Test
    void mirrorsFramesWithoutDuplicatingPixelData() {
        Map<String, byte[]> frames = sprite(List.of("ab.", "..a"),
                Map.of("f0_l", new SpriteData.Mirror("f0", true))).decode("t");
        assertThat(frames.get("f0_l")).containsExactly(-1, 12, 3, 3, -1, -1);
    }

    @Test
    void aRowOfTheWrongWidthNamesTheRow() {
        DataException e = catchThrowableOfType(DataException.class,
                () -> sprite(List.of("ab.", "..aa"), Map.of()).decode("x.sprite.json"));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/frames/0/rows/1");
        assertThat(e.detail()).contains("4 characters").contains("size.w is 3");
    }

    @Test
    void anUnknownLegendCharNamesTheColumn() {
        DataException e = catchThrowableOfType(DataException.class,
                () -> sprite(List.of("aZ.", "..a"), Map.of()).decode("x.sprite.json"));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/frames/0/rows/0");
        assertThat(e.detail()).contains("'Z'").contains("column 1");
    }

    @Test
    void aMirrorOfAMissingFrameIsReported() {
        DataException e = catchThrowableOfType(DataException.class,
                () -> sprite(List.of("ab.", "..a"), Map.of("x", new SpriteData.Mirror("nope", true))).decode("t"));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/mirror/x/from");
    }
}
