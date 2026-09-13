package wulf.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The integer sine table behind SINE_FLIGHT (AGENTS.md §12.5). */
class SinTableTest {

    @Test
    void hitsTheQuarterPointsExactly() {
        assertThat(SinTable.sin(0)).isZero();
        assertThat(SinTable.sin(64)).isEqualTo(256);
        assertThat(SinTable.sin(128)).isZero();
        assertThat(SinTable.sin(192)).isEqualTo(-256);
    }

    @Test
    void theSecondHalfMirrorsTheFirst() {
        for (int i = 0; i < 128; i++) {
            assertThat(SinTable.sin(i + 128)).as("index %d", i).isEqualTo(-SinTable.sin(i));
        }
    }

    @Test
    void risesThroughTheFirstQuarterAndWraps() {
        for (int i = 1; i <= 64; i++) {
            assertThat(SinTable.sin(i)).isGreaterThanOrEqualTo(SinTable.sin(i - 1));
        }
        assertThat(SinTable.sin(256)).isEqualTo(SinTable.sin(0));
        assertThat(SinTable.sin(-64)).isEqualTo(-256);
    }
}
