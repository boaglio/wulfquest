package wulf.sim;

/**
 * Where the simulation announces that something made a noise (AGENTS.md §18.1).
 *
 * <p>The simulation never makes a sound itself and never waits for one: it names
 * what happened and moves on. {@link #NONE} is what every test, replay and tool
 * uses, which is why a recorded run sounds like nothing and plays back exactly
 * the same.
 *
 * <p>Deliberately declared here, in {@code wulf.sim}, with no reference to the
 * audio package: the simulation's purity test (§22.4) forbids the sim even
 * naming {@code javax.sound}.
 */
@FunctionalInterface
public interface SoundSink {

    /** The silent sink. The simulation's default, and the only one the tests use. */
    SoundSink NONE = id -> { };

    /** Something happened that {@code sfx.json} has a sound for. */
    void play(String id);

    /** Starts or stops a sound that repeats while something is true — only the growl (§18.2). */
    default void loop(String id, boolean on) {
        // A sink that cannot hold a sound simply does not.
    }
}
