package wulf.world;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;

/** AGENTS.md §9 footprints and §7.3 collision derivation. */
class SceneryCatalogTest {

    private static Content content;

    /** The §9 work order: canon footprints in cells. A failure here means the art no longer fits the map. */
    private static final Map<String, int[]> FOOTPRINTS = Map.ofEntries(
            entry("7298", new int[] {2, 5}), entry("78F2", new int[] {3, 3}), entry("7947", new int[] {2, 3}),
            entry("7462", new int[] {3, 7}), entry("71B3", new int[] {5, 5}), entry("72F6", new int[] {8, 5}),
            entry("7523", new int[] {8, 7}), entry("7981", new int[] {4, 11}), entry("771F", new int[] {5, 7}),
            entry("70BC", new int[] {9, 3}), entry("8F2A", new int[] {6, 7}), entry("872A", new int[] {4, 6}),
            entry("785E", new int[] {4, 4}), entry("95CD", new int[] {6, 3}), entry("955D", new int[] {4, 3}),
            entry("8702", new int[] {4, 1}), entry("847C", new int[] {8, 3}), entry("90A8", new int[] {8, 11}),
            entry("7C0C", new int[] {7, 3}), entry("7E4B", new int[] {8, 7}), entry("86DA", new int[] {4, 1}),
            entry("7BB7", new int[] {3, 3}), entry("7CCD", new int[] {7, 6}), entry("8047", new int[] {7, 6}),
            entry("81C5", new int[] {7, 7}), entry("8558", new int[] {4, 3}), entry("7B11", new int[] {6, 3}),
            entry("9673", new int[] {1, 3}), entry("8B80", new int[] {4, 6}), entry("8D3C", new int[] {4, 6}),
            entry("83D2", new int[] {3, 3}), entry("8427", new int[] {3, 3}), entry("8E18", new int[] {6, 5}),
            entry("8806", new int[] {7, 7}), entry("89C3", new int[] {7, 7}), entry("8C5C", new int[] {4, 3}),
            entry("8CCC", new int[] {4, 3}), entry("83AA", new int[] {1, 4}), entry("85C8", new int[] {6, 5}),
            entry("93C4", new int[] {9, 5}), entry("8382", new int[] {1, 4}));

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
    }

    @Test
    void holdsExactlyTheFortyOneObjectsOfTheMap() {
        assertThat(content.scenery().ids()).hasSize(41);
        assertThat(content.scenery().ids()).containsExactlyInAnyOrderElementsOf(content.map().sceneryIds());
    }

    @Test
    void everyFootprintMatchesTheWorkOrder() {
        assertThat(FOOTPRINTS).hasSize(41);
        FOOTPRINTS.forEach((id, wh) -> {
            SceneryCatalog.Piece p = content.scenery().piece(id);
            assertThat(p.w()).as("%s width", id).isEqualTo(wh[0]);
            assertThat(p.h()).as("%s height", id).isEqualTo(wh[1]);
            assertThat(content.sprites().get(p.sprite()).size().w()).as("%s px width", id).isEqualTo(wh[0] * 8);
            assertThat(content.sprites().get(p.sprite()).size().h()).as("%s px height", id).isEqualTo(wh[1] * 8);
        });
    }

    @Test
    void everyObjectBlocksSomething() {
        // An object with no solid cell would be pure decoration on a maze wall.
        for (String id : content.scenery().ids()) {
            assertThat(content.scenery().piece(id).solidCells()).as(id).isPositive();
        }
    }

    @Test
    void derivationUsesTheCoverageThresholdExactly() {
        // One 8x8 cell; at 25% the boundary is 16 painted pixels.
        assertThat(SceneryCatalog.derive(cellWithPainted(15), 1, 1, 25)[0]).isFalse();
        assertThat(SceneryCatalog.derive(cellWithPainted(16), 1, 1, 25)[0]).isTrue();
        assertThat(SceneryCatalog.derive(cellWithPainted(64), 1, 1, 100)[0]).isTrue();
        assertThat(SceneryCatalog.derive(cellWithPainted(63), 1, 1, 100)[0]).isFalse();
    }

    private static byte[] cellWithPainted(int n) {
        byte[] px = new byte[64];
        java.util.Arrays.fill(px, (byte) -1);
        for (int i = 0; i < n; i++) {
            px[i] = 4;
        }
        return px;
    }
}
