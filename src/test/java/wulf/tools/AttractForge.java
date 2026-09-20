package wulf.tools;

import java.nio.file.Path;
import wulf.Content;
import wulf.engine.Replay;
import wulf.engine.ReplayRecorder;
import wulf.input.InputState;
import wulf.input.RecordedInput;
import wulf.sim.Simulation;
import wulf.world.WorldGrid;

/**
 * Dev tool, not a test: cuts {@code replays/attract.json} out of the front of a
 * longer recording (AGENTS.md §17.2, §22.6).
 *
 * <p>The title screen's demo wants a minute of somebody playing, not a whole
 * won game, so this replays an existing run for a given number of ticks and
 * records what it saw — a real replay with its own hashes, which
 * {@code ReplayRunner} verifies like any other.
 *
 * <pre>java -cp ... wulf.tools.AttractForge [source.json] [out.json] [ticks]</pre>
 */
public final class AttractForge {

    private AttractForge() {
    }

    public static void main(String[] args) {
        Path source = Path.of(args.length > 0 ? args[0] : "replays/full_run.json");
        Path out = Path.of(args.length > 1 ? args[1] : "replays/attract.json");
        int ticks = args.length > 2 ? Integer.parseInt(args[2]) : 3000;

        Content c = Content.load(Path.of("data"));
        Replay from = Replay.read(source);
        Simulation sim = Simulation.startingIn(c.player(), new WorldGrid(c.rooms()),
                c.game().transition().freezeTicks(), from.start(), c.ecosystem(), from.seed());
        RecordedInput.Cursor cursor = from.recorded().cursor();
        ReplayRecorder recorder = new ReplayRecorder(nameOf(out), c.db().contentHash(), from.seed(), from.start(),
                from.dev())
                .note("attract-mode demo, cut from " + source.getFileName() + " by AttractForge");

        int cut = Math.min(ticks, from.recorded().ticks());
        // Whole checkpoints only, so the file never carries a half-counted tail.
        cut -= cut % ReplayRecorder.CHECKPOINT_EVERY;
        for (int t = 0; t < cut && cursor.hasNext(); t++) {
            InputState in = cursor.next();
            sim.play(in, from.dev());
            recorder.record(in, sim);
        }
        recorder.write(out);
        System.out.println("attract: " + recorder.ticks() + " ticks from " + source + " -> " + out
                + "  (room " + sim.room() + ", score " + sim.score() + ")");
    }

    private static String nameOf(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }
}
