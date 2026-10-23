package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import wulf.world.RoomAddress;

/**
 * Regression lock on the extracted map (AGENTS.md §22.1).
 *
 * <p>Every assertion here is a fact from the §8 table. If one of these fails,
 * someone edited canon data — the fix is to restore the data, not to update
 * the test.
 *
 * <p>The one deliberate exception so far (2026-09-27, §25 Q14): the room grid was
 * replaced by the game's own layout table at $6066. The shipped grid was a
 * consistently relabelled copy of it while the templates kept the game's own
 * numbering, so every room was drawn with the wrong template. The facts below
 * are re-measured from the corrected grid.
 */
class MapDataTest {

    private static OriginalMapRepository map;

    @BeforeAll
    static void load() {
        JsonDb db = new JsonDb(java.nio.file.Path.of("data"));
        map = new OriginalMapRepository(db.load("world/original_map", OriginalMap.class));
    }

    @Test
    @DisplayName("the map is 16x16 = 256 rooms")
    void gridSize() {
        assertThat(map.gridW()).isEqualTo(16);
        assertThat(map.gridH()).isEqualTo(16);
        assertThat(map.roomCount()).isEqualTo(256);
    }

    @Test
    @DisplayName("the start room is col 8, row 10")
    void startRoom() {
        assertThat(map.startRoom()).isEqualTo(new RoomAddress(8, 10));
        assertThat(map.startRoom().toString()).isEqualTo("8,10");
        assertThat(map.startRoom().isBoundary()).isFalse();
    }

    @Test
    @DisplayName("48 templates are defined, ids 0..47")
    void templatesDefined() {
        assertThat(map.templateCount()).isEqualTo(48);
        assertThat(map.templateIds()).isEqualTo(rangeSet(0, 47));
    }

    @Test
    @DisplayName("45 templates are used; ids 1, 19 and 36 are unused")
    void templatesUsed() {
        Set<Integer> used = map.usedTemplateIds();
        assertThat(used).hasSize(45);
        Set<Integer> expected = rangeSet(0, 47);
        expected.removeAll(Set.of(1, 19, 36));   // the three templates the game never places
        assertThat(used).isEqualTo(expected);
        assertThat(used).doesNotContain(1, 19, 36);
    }

    @Test
    @DisplayName("41 distinct scenery objects, each a 4-digit hex id")
    void sceneryObjects() {
        Set<String> ids = map.sceneryIds();
        assertThat(ids).hasSize(41);
        assertThat(ids).allMatch(id -> id.matches("[0-9A-F]{4}"), "4-digit uppercase hex");
        // Spot-check the landmarks called out in §8.2 and §9.
        assertThat(ids).contains("7298", "8E18", "85C8", "93C4", "90A8", "8382");
    }

    @Test
    @DisplayName("919 placements across the templates, 5105 across all 256 rooms")
    void placementCounts() {
        assertThat(map.totalTemplatePlacements()).isEqualTo(919);
        assertThat(map.totalRoomPlacements()).isEqualTo(4920);
    }

    @Test
    @DisplayName("template sizes run from 13 to 26 objects")
    void templateSizes() {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int id : map.templateIds()) {
            int n = map.placements(id).size();
            min = Math.min(min, n);
            max = Math.max(max, n);
        }
        assertThat(min).isEqualTo(13);
        assertThat(max).isEqualTo(26);
        assertThat(map.placements(18)).hasSize(13);
        assertThat(map.placements(15)).hasSize(26);
        assertThat(map.placements(16)).hasSize(26);
    }

    @Test
    @DisplayName("no template is empty")
    void noEmptyTemplates() {
        for (int id : map.templateIds()) {
            assertThat(map.placements(id)).as("template " + id).isNotEmpty();
        }
    }

    @Test
    @DisplayName("every placement origin sits inside the 32x22 cell playfield, below the banner")
    void placementsInBounds() {
        for (int id : map.templateIds()) {
            for (Placement p : map.placements(id)) {
                assertThat(p.x()).as("template %d, %s x", id, p.graphic()).isBetween(0, 31);
                // The JSON counts from the top of the screen; the repository from the top of the room.
                assertThat(p.y()).as("template %d, %s y", id, p.graphic()).isBetween(0, 21);
            }
        }
    }

    @Test
    @DisplayName("the boundary ring uses only boundary room types")
    void boundaryRing() {
        Set<Integer> interior = new TreeSet<>();
        Set<Integer> boundary = new TreeSet<>();
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                RoomAddress room = new RoomAddress(col, row);
                (room.isBoundary() ? boundary : interior).add(map.roomType(room));
            }
        }
        // §8.2: the two sets never overlap — the ring is its own vocabulary.
        assertThat(interior).doesNotContainAnyElementsOf(boundary);
        assertThat(interior).isEqualTo(new TreeSet<>(
                List.of(5, 6, 7, 8, 10, 11, 12, 13, 14, 15, 16, 17, 18, 21, 23, 24, 25, 28, 29, 31,
                        32, 33, 45, 46, 47)));
        assertThat(boundary).isEqualTo(new TreeSet<>(
                List.of(0, 2, 3, 4, 9, 20, 22, 26, 27, 30, 34, 35, 37, 38, 39, 40, 41, 42, 43, 44)));
    }

    @Test
    @DisplayName("196 interior rooms")
    void interiorCount() {
        long interior = 0;
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                if (!new RoomAddress(col, row).isBoundary()) {
                    interior++;
                }
            }
        }
        assertThat(interior).isEqualTo(196);
    }

    @Test
    @DisplayName("the central landmark is mirror-symmetric about column 8")
    void centralLandmark() {
        // §8.2: unique types 46, 15, 7, 6 on the axis; 10, 16 and 14 as mirrored pairs.
        assertThat(map.roomType(7, 6)).isEqualTo(46);
        assertThat(map.roomType(8, 7)).isEqualTo(15);
        assertThat(map.roomType(8, 8)).isEqualTo(7);
        assertThat(map.roomType(8, 9)).isEqualTo(6);
        assertThat(map.roomType(7, 7)).isEqualTo(10);
        assertThat(map.roomType(9, 7)).isEqualTo(10);
        assertThat(map.roomType(7, 8)).isEqualTo(16);
        assertThat(map.roomType(9, 8)).isEqualTo(16);
        assertThat(map.roomType(7, 9)).isEqualTo(14);
        assertThat(map.roomType(9, 9)).isEqualTo(14);

        assertThat(map.roomsOfType(46)).hasSize(1);
        assertThat(map.roomsOfType(15)).hasSize(1);
        assertThat(map.roomsOfType(7)).hasSize(1);
        assertThat(map.roomsOfType(6)).hasSize(1);
        assertThat(map.roomsOfType(10)).hasSize(2);
        assertThat(map.roomsOfType(16)).hasSize(2);
        assertThat(map.roomsOfType(14)).hasSize(2);
    }

    @Test
    @DisplayName("the water object is scattered, not central: 27 rooms of five templates")
    void water() {
        int rooms = 0;
        for (int type : map.usedTemplateIds()) {
            if (graphicsOf(type).contains("93C4")) {
                rooms += map.roomsOfType(type).size();
            }
        }
        assertThat(rooms).isEqualTo(27);
        assertThat(graphicsOf(map.roomType(8, 7))).doesNotContain("93C4");
    }

    @Test
    @DisplayName("the hut stands in exactly 5 rooms, all of template 5")
    void hutRooms() {
        assertThat(graphicsOf(5)).contains("8E18");
        List<RoomAddress> rooms = map.roomsOfType(5);
        assertThat(rooms.stream().map(RoomAddress::toString).toList())
                .containsExactlyInAnyOrder("4,3", "2,5", "1,9", "2,13", "3,13");
    }

    @Test
    @DisplayName("the stone arch stands in exactly one room: the heart of the central landmark")
    void archRooms() {
        assertThat(graphicsOf(7)).contains("85C8");
        assertThat(map.roomsOfType(7).stream().map(RoomAddress::toString).toList()).containsExactly("8,8");
    }

    @Test
    @DisplayName("the lone rock spur 8382 is placed in exactly one room")
    void loneSpur() {
        int rooms = 0;
        for (int type : map.usedTemplateIds()) {
            if (graphicsOf(type).contains("8382")) {
                rooms += map.roomsOfType(type).size();
            }
        }
        assertThat(rooms).isEqualTo(1);
        assertThat(graphicsOf(map.roomType(0, 15))).contains("8382");
    }

    @Test
    @DisplayName("scenery usage weighted by room matches the §9 work-order table")
    void sceneryUsageWeights() {
        Map<String, Integer> perRoom = new LinkedHashMap<>();
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                for (Placement p : map.placements(new RoomAddress(col, row))) {
                    perRoom.merge(p.graphic(), 1, Integer::sum);
                }
            }
        }
        assertThat(perRoom.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(4920);
        // The top of the art priority order, and the tail.
        assertThat(perRoom).containsEntry("7298", 609);
        assertThat(perRoom).containsEntry("78F2", 530);
        assertThat(perRoom).containsEntry("7947", 418);
        assertThat(perRoom).containsEntry("7462", 196);
        assertThat(perRoom).containsEntry("8E18", 5);
        assertThat(perRoom).containsEntry("85C8", 1);
        assertThat(perRoom).containsEntry("93C4", 27);
        assertThat(perRoom).containsEntry("8382", 1);
    }

    @Test
    @DisplayName("every room resolves to a template that exists")
    void everyRoomResolves() {
        for (int row = 0; row < 16; row++) {
            for (int col = 0; col < 16; col++) {
                RoomAddress room = new RoomAddress(col, row);
                assertThat(map.placements(room)).as("room %s", room).isNotEmpty();
            }
        }
    }

    private static Set<String> graphicsOf(int roomType) {
        Set<String> ids = new TreeSet<>();
        for (Placement p : map.placements(roomType)) {
            ids.add(p.graphic());
        }
        return ids;
    }

    private static Set<Integer> rangeSet(int from, int toInclusive) {
        Set<Integer> s = new TreeSet<>();
        for (int i = from; i <= toInclusive; i++) {
            s.add(i);
        }
        return s;
    }
}
