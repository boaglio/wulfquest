package wulf.engine;

/**
 * The game's one random number generator: xorshift128+ (AGENTS.md §6.3). Never
 * {@code java.util.Random}, never {@code Math.random()}.
 *
 * <p>Seeded through splitmix64 so that nearby seeds give unrelated streams and
 * the state is never all-zero. Integer only, so a seed replays identically
 * everywhere.
 */
public final class Rng {

    private static final long GOLDEN = 0x9E3779B97F4A7C15L;

    private long s0;
    private long s1;

    public Rng(long seed) {
        long z = seed + GOLDEN;
        s0 = splitmix(z);
        s1 = splitmix(z + GOLDEN);
        if ((s0 | s1) == 0) {
            s1 = 1;
        }
    }

    private static long splitmix(long seed) {
        long z = seed;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public long nextLong() {
        long x = s0;
        long y = s1;
        s0 = y;
        x ^= x << 23;
        s1 = x ^ y ^ (x >>> 17) ^ (y >>> 26);
        return s1 + y;
    }

    /** Uniform in {@code [0, bound)}. */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive, was " + bound);
        }
        return (int) ((nextLong() >>> 1) % bound);
    }

    /** True with probability {@code per10k / 10000}. */
    public boolean chance(int per10k) {
        return nextInt(10_000) < per10k;
    }

    /** A digest of the internal state, for the simulation's state hash (§6.4). */
    public long stateHash() {
        return s0 * 31 + s1;
    }

    /** A stateless 64-bit mix of several values — the §6.3 room seed. */
    public static long hash(long... values) {
        long h = GOLDEN;
        for (long v : values) {
            h = splitmix(h ^ v);
        }
        return h;
    }
}
