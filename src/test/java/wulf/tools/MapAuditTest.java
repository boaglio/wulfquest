package wulf.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;

/** AGENTS.md §22.3 — the M2 acceptance gate. */
class MapAuditTest {

    private static MapAudit.Report report;

    @BeforeAll
    static void audit() {
        report = MapAudit.run(Content.load(Path.of("data")).rooms());
    }

    @Test
    void everyInteriorRoomIsReachableFromTheStart() {
        assertThat(report.interiorRooms()).isEqualTo(196);
        assertThat(report.unreachable()).isEmpty();
        assertThat(report.interiorReached()).isEqualTo(196);
    }

    @Test
    void noRoomIsSealed() {
        assertThat(report.sealed()).isEmpty();
    }

    @Test
    void noRoomFallsBelowTheWalkableFloor() {
        assertThat(report.belowFloor()).isEmpty();
        assertThat(report.passes()).isTrue();
    }

    @Test
    void artCollisionIsNeverTighterThanTheFootprintMaze() {
        for (MapAudit.RoomStat s : report.rooms()) {
            assertThat(s.walkablePercent()).as("room %s", s.room()).isGreaterThanOrEqualTo(s.footprintWalkablePercent());
        }
    }

    @Test
    void theFootprintBaselineIsTheExtractedMazeItself() {
        // Measured from original_map.json alone: first in M2, again on 2026-09-27 once the grid was
        // the game's own and the rooms lost the two banner rows that were never playfield (§25 Q14).
        assertThat(report.footprintMinWalk()).isEqualTo(24);
        assertThat(report.footprintMaxWalk()).isEqualTo(48);
        assertThat(report.footprintMedianWalk()).isEqualTo(28);
    }

    /**
     * §7.3 / §25 Q13: collision is derived from the art, and the art must fill its
     * footprint. Sparse sprites reopen routes the original maze walled off; this
     * stops a future sprite edit from quietly loosening the maze again.
     */
    @Test
    void artKeepsTheMazeWithinSixPointsOfTheFootprintMaze() {
        assertThat(report.medianWalk() - report.footprintMedianWalk())
                .as("interior median walkable %d%% against the footprint maze's %d%%",
                        report.medianWalk(), report.footprintMedianWalk())
                .isLessThanOrEqualTo(6);
    }

    // ------------------------------------------------------------------ the edge of the map

    /**
     * Until 2026-09-22 reachability was counted over the 196 interior rooms only — the 60 on the
     * border were skipped outright, so the audit had never looked at the edge of the map. It is
     * the edge a player notices first.
     */
    @Test
    void everyRoomIsReachable_includingTheOnesOnTheBorder() {
        assertThat(report.unreachableAnywhere()).isEmpty();
        assertThat(report.allReached()).isEqualTo(256);
        assertThat(report.allRooms()).isEqualTo(256);
    }

    /**
     * A way through that nothing can walk to. These used to be ruled out as a defect; on the true
     * map they are the original's own dead ends. Emulating the original's collision and room flips
     * over its own layout (§25 Q14) finds exactly these: 29 east-west, none north-south.
     */
    @Test
    void theWaysThroughNobodyCanReachAreTheOriginalsOwn() {
        long eastWest = report.stranded().stream().filter(seam -> seam.from().row() == seam.to().row()).count();
        assertThat(eastWest).as("east-west").isEqualTo(29);
        assertThat(report.stranded().size() - eastWest).as("north-south").isZero();
    }

    @Test
    void everySeamIsCountedOnce() {
        assertThat(report.seams()).hasSize(240 + 240);
        assertThat(report.seams()).allSatisfy(seam ->
                assertThat(seam.reachable()).isLessThanOrEqualTo(seam.crossings()));
    }

    /**
     * The room edges the original lets you through (§25 Q14). An emulation of the game's own
     * collision test ($B873) and room flips over its own layout table finds 154 east-west and 160
     * north-south edges with a way through; this map, drawn from the same templates with our
     * collision, has exactly those. East-west "blocked" counts the 29 dead ends above as well.
     *
     * <p>Before the grid was corrected this read 0 and 107: a two-cell corridor along the top of
     * every room let anything through east-west, and nothing else connected north-south.
     */
    @Test
    void theRoomEdgesAreTheOriginals() {
        assertThat(report.blockedEastWest()).as("east-west edges blocked").isEqualTo(86 + 29);
        assertThat(report.blockedNorthSouth()).as("north-south edges blocked").isEqualTo(80);
    }

    /**
     * <b>A known defect, pinned.</b> A walkable cell on the outermost cell-line is open ground the
     * player can stand in with the world's edge in front of them (§7.5). With the corrected grid
     * the original's footprints leave 6 such cells; art-derived collision (§7.3) opens 769 across
     * 48 border rooms, where a sprite does not paint a quarter of an edge cell. It was 937 across
     * 62 rooms before the grid was corrected.
     */
    @Test
    void theMapsOwnEdgeIsNoMoreOpenThanTheDayItWasFound() {
        assertThat(report.borderOpenCells()).as("open cells against the world edge — §25 Q14")
                .isLessThanOrEqualTo(769);
    }
}
