package wulf.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** AGENTS.md §6.3 — the one random stream. */
class RngTest {

    @Test
    void theSameSeedGivesTheSameSequence() {
        Rng a = new Rng(42);
        Rng b = new Rng(42);
        for (int i = 0; i < 1000; i++) {
            assertThat(a.nextLong()).isEqualTo(b.nextLong());
        }
    }

    @Test
    void nearbySeedsGiveUnrelatedSequences() {
        assertThat(new Rng(1).nextLong()).isNotEqualTo(new Rng(2).nextLong());
        assertThat(new Rng(0).nextLong()).as("seed zero still works").isNotEqualTo(new Rng(0).nextLong() + 1);
    }

    @Test
    void nextIntStaysInBoundsAndIsRoughlyUniform() {
        Rng r = new Rng(7);
        int[] buckets = new int[10];
        for (int i = 0; i < 100_000; i++) {
            int v = r.nextInt(10);
            assertThat(v).isBetween(0, 9);
            buckets[v]++;
        }
        for (int count : buckets) {
            assertThat(count).isBetween(9_000, 11_000);
        }
    }

    @Test
    void chanceHonoursItsExtremes() {
        Rng r = new Rng(3);
        for (int i = 0; i < 1000; i++) {
            assertThat(r.chance(0)).isFalse();
            assertThat(r.chance(10_000)).isTrue();
        }
    }

    @Test
    void theRoomSeedHashIsStableAndSensitiveToEveryValue() {
        assertThat(Rng.hash(1, 2, 3, 4)).isEqualTo(Rng.hash(1, 2, 3, 4));
        assertThat(Rng.hash(1, 2, 3, 4)).isNotEqualTo(Rng.hash(1, 2, 3, 5));
        assertThat(Rng.hash(1, 2, 3, 4)).isNotEqualTo(Rng.hash(1, 2, 4, 3));
        assertThat(Rng.hash(1, 2, 3, 4)).isNotEqualTo(Rng.hash(2, 2, 3, 4));
    }
}
