package wulf.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;

/**
 * AGENTS.md §12.6 — biome counts on the real map. First measured before M4's code existed;
 * re-measured 2026-09-27 on the game's own layout table (§25 Q14), which is why swamp now
 * dominates: the reed marker is in more of the true map's rooms.
 */
class BiomeResolverTest {

    private static Content content;

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
    }

    private static Map<String, Integer> count(boolean interior) {
        Map<String, Integer> counts = new TreeMap<>();
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                RoomAddress r = new RoomAddress(col, row);
                if (r.isBoundary() != interior) {
                    counts.merge(content.biomes().biomeOf(r), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    @Test
    void theInteriorBiomesMatchTheMeasuredMap() {
        assertThat(count(true)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "bonefields", 4, "hut", 5, "jungle", 10, "swamp", 159, "water", 18));
    }

    @Test
    void theBoundaryBiomesMatchTheMeasuredMap() {
        assertThat(count(false)).containsExactlyInAnyOrderEntriesOf(Map.of("jungle", 49, "mountain", 2, "water", 9));
    }

    @Test
    void landmarksResolveAsExpected() {
        assertThat(content.biomes().biomeOf(new RoomAddress(8, 10))).isEqualTo("jungle");
        assertThat(content.biomes().biomeOf(new RoomAddress(8, 8))).as("the arch").isEqualTo("bonefields");
        assertThat(content.biomes().biomeOf(new RoomAddress(2, 5))).as("a hut room").isEqualTo("hut");
        assertThat(content.biomes().inUse()).hasSize(6);
    }
}
