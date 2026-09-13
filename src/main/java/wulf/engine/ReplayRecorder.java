package wulf.engine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import wulf.input.InputState;
import wulf.input.RecordedInput;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;

/** Records a run as it is played, for {@code --record} and the replay forges (AGENTS.md §22.6). */
public final class ReplayRecorder {

    /** §22.6: a state hash after every 50th tick. */
    public static final int CHECKPOINT_EVERY = 50;

    private final String name;
    private final String contentHash;
    private final long seed;
    private final RoomAddress start;
    private final boolean dev;
    private final RecordedInput input = new RecordedInput();
    private final List<String> hashes = new ArrayList<>();
    private String note = "recorded with ./run.sh --record";

    public ReplayRecorder(String name, String contentHash, long seed, RoomAddress start, boolean dev) {
        this.name = name;
        this.contentHash = contentHash;
        this.seed = seed;
        this.start = start;
        this.dev = dev;
    }

    /** Call once per simulated tick, after it, with the input that drove it. */
    public void record(InputState in, Simulation sim) {
        input.append(in);
        if (input.ticks() % CHECKPOINT_EVERY == 0) {
            hashes.add(Replay.hex(sim.stateHash()));
        }
    }

    public ReplayRecorder note(String text) {
        note = text;
        return this;
    }

    public int ticks() {
        return input.ticks();
    }

    public Replay replay() {
        return new Replay(Replay.SCHEMA_VERSION, name, note, contentHash, seed, start.toString(), dev,
                CHECKPOINT_EVERY, input.runs(), hashes);
    }

    public void write(Path file) {
        replay().write(file);
    }
}
