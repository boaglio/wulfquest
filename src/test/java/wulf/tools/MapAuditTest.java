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
}
