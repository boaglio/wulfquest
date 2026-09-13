package wulf.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;

/** AGENTS.md §12.6 — biome counts on the real map, measured before M4's code existed. */
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
                "bonefields", 20, "hut", 21, "jungle", 72, "mountain", 16, "swamp", 65, "water", 2));
    }

    @Test
    void theBoundaryBiomesMatchTheMeasuredMap() {
        assertThat(count(false)).containsExactlyInAnyOrderEntriesOf(Map.of("jungle", 25, "swamp", 30, "water", 5));
    }

    @Test
    void landmarksResolveAsExpected() {
        assertThat(content.biomes().biomeOf(new RoomAddress(8, 10))).isEqualTo("jungle");
        for (String arch : new String[] {"7,3", "12,8", "1,10", "14,10", "6,11", "4,13", "6,13", "5,14"}) {
            assertThat(content.biomes().biomeOf(RoomAddress.parse(arch))).as(arch).isEqualTo("bonefields");
        }
        assertThat(content.biomes().biomeOf(new RoomAddress(4, 1))).as("a hut room").isEqualTo("hut");
        assertThat(content.biomes().inUse()).hasSize(6);
    }
}
