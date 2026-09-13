package wulf.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.DataException;
import wulf.data.Placement;
import wulf.data.SceneryData;

/** AGENTS.md §7.2 room baking and the §20.6 map-reference checks. */
class RoomBakerTest {

    private static Content content;

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
    }

    @Test
    void bakesTheStartRoom() {
        Room room = content.rooms().room(new RoomAddress(8, 10));
        assertThat(room.type()).isEqualTo(1);
        assertThat(room.placements()).isNotEmpty();
        assertThat(room.mask().walkablePercent()).isBetween(12, 99);
    }

    @Test
    void roomsOfOneTemplateShareOneMask() {
        // Template 5 (the hut) is used by 21 rooms.
        Room a = content.rooms().room(new RoomAddress(4, 1));
        Room b = content.rooms().room(new RoomAddress(10, 13));
        assertThat(a.type()).isEqualTo(5).isEqualTo(b.type());
        assertThat(a.mask()).isSameAs(b.mask());
    }

    /**
     * The invariant that makes the art safe to change: collision never reaches
     * outside the footprints the extracted map placed. Because the footprint maze
     * is fully connected, no art can ever make a room unreachable.
     */
    @Test
    void collisionNeverExtendsBeyondThePlacedFootprints() {
        for (int type : content.map().templateIds()) {
            boolean[] footprint = new boolean[CollisionMask.COLS * CollisionMask.ROWS];
            for (Placement p : content.map().placements(type)) {
                SceneryCatalog.Piece piece = content.scenery().piece(p.graphic());
                for (int y = p.y(); y < p.y() + piece.h(); y++) {
                    for (int x = p.x(); x < p.x() + piece.w(); x++) {
                        footprint[y * CollisionMask.COLS + x] = true;
                    }
                }
            }
            CollisionMask mask = content.rooms().mask(type);
            for (int cy = 0; cy < CollisionMask.ROWS; cy++) {
                for (int cx = 0; cx < CollisionMask.COLS; cx++) {
                    if (mask.isSolid(cx, cy)) {
                        assertThat(footprint[cy * CollisionMask.COLS + cx])
                                .as("template %d cell %d,%d is solid outside every footprint", type, cx, cy)
                                .isTrue();
                    }
                }
            }
        }
    }

    @Test
    void aMapGraphicMissingFromSceneryIsReportedWithAPointer() {
        SceneryData full = content.db().load("world/scenery", SceneryData.class);
        Map<String, SceneryData.SceneryObject> without = new TreeMap<>(full.objects());
        without.remove("7298");
        SceneryCatalog trimmed = new SceneryCatalog(
                new SceneryData(full.schemaVersion(), full.solidCoveragePercent(), without), content.sprites().all());

        DataException e = catchThrowableOfType(DataException.class, () -> new RoomBaker(content.map(), trimmed));
        assertThat(e).isNotNull();
        assertThat(e.file()).endsWith("original_map.json");
        assertThat(e.pointer()).matches("/templates/\\d+/\\d+/graphic");
        assertThat(e.detail()).contains("7298");
    }

    @Test
    void theWorldEdgeIsSolid() {
        WorldGrid grid = new WorldGrid(content.rooms());
        assertThat(grid.isSolid(-1, 0)).isTrue();
        assertThat(grid.isSolid(0, -1)).isTrue();
        assertThat(grid.isSolid(WorldGrid.COLS, 0)).isTrue();
        assertThat(grid.isSolid(0, WorldGrid.ROWS)).isTrue();
        assertThat(WorldGrid.COLS).isEqualTo(512);
        assertThat(WorldGrid.ROWS).isEqualTo(384);
    }
}
