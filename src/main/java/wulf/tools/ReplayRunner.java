package wulf.tools;

import java.nio.file.Path;
import wulf.Content;
import wulf.data.DataException;
import wulf.engine.Replay;
import wulf.input.RecordedInput;
import wulf.sim.Simulation;
import wulf.world.WorldGrid;

/**
 * Re-executes recorded runs and checks every state hash (AGENTS.md §22.6).
 *
 * <pre>mvn -q exec:java -Dexec.mainClass=wulf.tools.ReplayRunner -Dexec.args="replays/wulf_escape.json"</pre>
 *
 * Exit code 0 when every replay matches, 1 on a divergence, 2 on bad input.
 */
public final class ReplayRunner {

    /**
     * @param divergedAtTick the first checkpoint tick whose hash differs, or -1
     * @param sim            the simulation as it stood when the run ended or diverged
     */
    public record Result(int ticks, int checkpoints, int divergedAtTick, String expected, String actual,
                         boolean sameContent, Simulation sim) {

        public boolean passed() {
            return divergedAtTick < 0;
        }
    }

    private ReplayRunner() {
    }

    /** A fresh simulation exactly as the recording began. */
    public static Simulation start(Content c, Replay r) {
        return Simulation.startingIn(c.player(), new WorldGrid(c.rooms()), c.game().transition().freezeTicks(),
                r.start(), c.ecosystem(), r.seed());
    }

    public static Result run(Content c, Replay r) {
        Simulation sim = start(c, r);
        boolean same = c.db().contentHash().equals(r.contentHash());
        RecordedInput.Cursor in = r.recorded().cursor();
        int t = 0;
        int checkpoint = 0;
        while (in.hasNext()) {
            sim.play(in.next(), r.dev());
            t++;
            if (t % r.checkpointEvery() == 0) {
                String expected = r.hashes().get(checkpoint++);
                String actual = Replay.hex(sim.stateHash());
                if (!actual.equals(expected)) {
                    return new Result(t, checkpoint, t, expected, actual, same, sim);
                }
            }
        }
        return new Result(t, checkpoint, -1, "", "", same, sim);
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.err.println("usage: ReplayRunner replays/<name>.json ...");
            System.exit(2);
        }
        Content c;
        try {
            c = Content.load(Path.of("data"));
        } catch (DataException e) {
            System.err.println("DATA ERROR: " + e.getMessage());
            System.exit(2);
            return;
        }
        boolean allPassed = true;
        for (String arg : args) {
            Replay r;
            try {
                r = Replay.read(Path.of(arg));
            } catch (RuntimeException e) {
                System.err.println(arg + ": " + e.getMessage());
                System.exit(2);
                return;
            }
            Result result = run(c, r);
            String content = result.sameContent() ? "" : "  (content changed since it was recorded)";
            if (result.passed()) {
                System.out.printf("%s: OK — %d ticks, %d checkpoints%s%n", r.name(), result.ticks(), result.checkpoints(), content);
            } else {
                allPassed = false;
                System.out.printf("%s: DIVERGED at tick %d — expected %s, got %s%s%n",
                        r.name(), result.divergedAtTick(), result.expected(), result.actual(), content);
            }
        }
        System.exit(allPassed ? 0 : 1);
    }
}
