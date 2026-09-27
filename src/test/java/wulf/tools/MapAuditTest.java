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
        // Measured from original_map.json alone, before any art existed (M2).
        // walkablePercent() rounds DOWN, so the widest room (52.6%) reads 52.
        assertThat(report.footprintMinWalk()).isEqualTo(25);
        assertThat(report.footprintMaxWalk()).isEqualTo(52);
        assertThat(report.footprintMedianWalk()).isEqualTo(39);
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

    /** A way through that nothing can walk to is worse than a wall: it is visible and it lies. */
    @Test
    void noWayThroughIsWalledOffFromTheRoomItBelongsTo() {
        assertThat(report.stranded()).isEmpty();
    }

    @Test
    void everySeamIsCountedOnce() {
        assertThat(report.seams()).hasSize(240 + 240);
        assertThat(report.seams()).allSatisfy(seam ->
                assertThat(seam.reachable()).isLessThanOrEqualTo(seam.crossings()));
    }

    /**
     * <b>A known defect, pinned so it cannot get worse.</b>
     *
     * <p>Every one of the 256 rooms has its top two cell-rows empty, because the extracted
     * placements start at y=2 in a 24-row playfield (§8, §25 Q14). That leaves a two-cell corridor
     * running along the top of the whole map — which is the only reason no east-west edge is
     * blocked — while the content piles up against the bottom and blocks 107 of the 240
     * north-south edges. A maze blocks both directions; this blocks one.
     *
     * <p>These numbers are recorded, not blessed. When Q14 is settled they should both come down
     * and the asymmetry with them.
     */
    @Test
    void theNorthSouthBlockageIsNoWorseThanTheDayItWasFound() {
        assertThat(report.blockedEastWest()).as("east-west edges blocked").isZero();
        assertThat(report.blockedNorthSouth()).as("north-south edges blocked — §25 Q14")
                .isLessThanOrEqualTo(107);
        assertThat(report.asymmetry()).as("a maze blocks both ways; this blocks one — §25 Q14")
                .isLessThanOrEqualTo(107);
    }

    /**
     * <b>A known defect, pinned.</b> A walkable cell on the outermost cell-line is open ground the
     * player can stand in with the world's edge in front of them (§7.5). 937 of them across 62 of
     * the 64 border rooms is why the map reads as unfinished. Same cause as above, same fix.
     */
    @Test
    void theMapsOwnEdgeIsNoMoreOpenThanTheDayItWasFound() {
        assertThat(report.borderOpenCells()).as("open cells against the world edge — §25 Q14")
                .isLessThanOrEqualTo(937);
    }
}
