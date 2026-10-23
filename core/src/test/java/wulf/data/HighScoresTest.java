package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** The table's ranking rules (AGENTS.md §21.2). */
class HighScoresTest {

    private static final int SIZE = 3;

    private static HighScores.Entry entry(String name, long score, long ticks) {
        return new HighScores.Entry(name, score, 0, ticks, "2026-09-19");
    }

    private static HighScores table(HighScores.Entry... rows) {
        return new HighScores(HighScores.SCHEMA_VERSION, List.of(rows));
    }

    @Test
    void bestFirstAndThenQuickest() {
        HighScores t = table(entry("SLO", 100, 9000), entry("LOW", 50, 100), entry("FST", 100, 500));
        assertThat(t.ranked(SIZE).entries()).extracting(HighScores.Entry::name).containsExactly("FST", "SLO", "LOW");
    }

    @Test
    void aTableWithRoomTakesAnything() {
        assertThat(table(entry("ONE", 100, 100)).qualifies(1, 999_999, SIZE)).isTrue();
    }

    @Test
    void aFullTableOnlyTakesWhatBeatsItsLastRow() {
        HighScores full = table(entry("A", 300, 100), entry("B", 200, 100), entry("C", 100, 100));
        assertThat(full.qualifies(99, 100, SIZE)).isFalse();
        assertThat(full.qualifies(101, 100, SIZE)).isTrue();
        // The same score, but quicker, is better.
        assertThat(full.qualifies(100, 99, SIZE)).isTrue();
        assertThat(full.qualifies(100, 101, SIZE)).isFalse();
    }

    @Test
    void zeroNeverQualifies() {
        assertThat(HighScores.empty().qualifies(0, 10, SIZE)).isFalse();
    }

    @Test
    void addingARowTrimsTheTableAndReportsWhereItLanded() {
        HighScores full = table(entry("A", 300, 100), entry("B", 200, 100), entry("C", 100, 100));
        HighScores.Entry row = entry("NEW", 250, 100);
        HighScores after = full.with(row, SIZE);
        assertThat(after.entries()).hasSize(SIZE);
        assertThat(full.placeOf(row, SIZE)).isEqualTo(1);
        assertThat(after.entries()).extracting(HighScores.Entry::name).containsExactly("A", "NEW", "B");
        assertThat(after.best()).isEqualTo(300);
    }

    @Test
    void anEmptyTableHasNoBest() {
        assertThat(HighScores.empty().best()).isZero();
    }
}
