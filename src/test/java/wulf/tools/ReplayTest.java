package wulf.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wulf.Content;
import wulf.engine.Replay;
import wulf.engine.ReplayRecorder;
import wulf.input.InputState;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;

/** AGENTS.md §22.6 — recorded runs replay to the same state hashes, and a divergence is caught where it happens. */
class ReplayTest {

    private static Content content;

    @TempDir
    Path dir;

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
    }

    /** A busy script in a populated room, pressing the dev key for the Wulf every 50 ticks — it comes when it can. */
    private static InputState script(int t) {
        int phase = (t / 41) % 6;
        int dx = phase < 2 ? 1 : phase < 4 ? -1 : 0;
        int dy = phase == 1 || phase == 4 ? 1 : phase == 5 ? -1 : 0;
        boolean fire = t % 19 == 0;
        return new InputState(dy < 0, dy > 0, dx < 0, dx > 0, fire, fire, false, false, false, false, t % 50 == 10);
    }

    private static Replay record(long seed, int ticks) {
        RoomAddress start = new RoomAddress(4, 1);   // a hut: the Wulf can get in (7,3's openings are too narrow for it)
        Replay shell = new Replay(Replay.SCHEMA_VERSION, "t", null, "", seed, start.toString(), true, 50, List.of(), List.of());
        Simulation sim = ReplayRunner.start(content, shell);
        ReplayRecorder rec = new ReplayRecorder("test", content.db().contentHash(), seed, start, true).note("ReplayTest");
        for (int t = 0; t < ticks; t++) {
            InputState in = script(t);
            sim.play(in, true);
            rec.record(in, sim);
        }
        return rec.replay();
    }

    @Test
    void aRecordingReplaysExactlyThroughAFile() {
        Path file = dir.resolve("run.json");
        record(5L, 1200).write(file);
        Replay back = Replay.read(file);
        ReplayRunner.Result result = ReplayRunner.run(content, back);
        assertThat(result.passed()).as("diverged at %d", result.divergedAtTick()).isTrue();
        assertThat(result.ticks()).isEqualTo(1200);
        assertThat(result.checkpoints()).isEqualTo(24);
        assertThat(result.sameContent()).isTrue();
        assertThat(result.sim().wulf().appearances()).as("the dev key replayed too").isPositive();
    }

    /**
     * M5's acceptance replay: a Wulf chase survived across four rooms. Beyond matching
     * every hash, it must still show what it was recorded to show.
     */
    @Test
    void theWulfEscapeReplayPassesAndStillShowsAnEscape() {
        Replay r = Replay.read(Path.of("replays/wulf_escape.json"));
        ReplayRunner.Result result = ReplayRunner.run(content, r);
        assertThat(result.passed()).as("wulf_escape diverged at tick %d: expected %s, got %s",
                result.divergedAtTick(), result.expected(), result.actual()).isTrue();

        Simulation sim = ReplayRunner.start(content, r);
        wulf.sim.Wulf w = sim.wulf();
        wulf.input.RecordedInput.Cursor in = r.recorded().cursor();
        int deaths = 0;
        int longestEscape = 0;
        int chaseRooms = 0;
        int evasions = 0;
        RoomAddress room = sim.room();
        boolean chasing = false;
        while (in.hasNext()) {
            wulf.sim.Player.Mode before = sim.player().mode();
            wulf.sim.Wulf.State was = w.state();
            room = sim.room();
            sim.play(in.next(), r.dev());
            if (before == wulf.sim.Player.Mode.ALIVE && sim.player().mode() != wulf.sim.Player.Mode.ALIVE) {
                deaths++;
            }
            // From its appearance; a room counts only if it was after you as you flipped into it — not while leaving.
            boolean after = was == wulf.sim.Wulf.State.WARNING || was == wulf.sim.Wulf.State.PURSUE
                    || was == wulf.sim.Wulf.State.ARRIVING;
            if (was == wulf.sim.Wulf.State.ABSENT && w.state() == wulf.sim.Wulf.State.WARNING) {
                chasing = true;
                chaseRooms = 1;
                evasions = w.evasions();
            } else if (chasing && after && !sim.room().equals(room)) {
                chaseRooms++;
            }
            if (chasing && w.state() == wulf.sim.Wulf.State.ABSENT && w.evasions() == evasions) {
                chasing = false;
            }
            if (chasing && w.evasions() > evasions) {
                longestEscape = Math.max(longestEscape, chaseRooms);
                chasing = false;
            }
        }
        assertThat(deaths).as("survived").isZero();
        assertThat(longestEscape).as("rooms in the longest chase that ended in an escape").isGreaterThanOrEqualTo(4);
    }

    @Test
    void aWrongHashIsCaughtAtItsCheckpoint() {
        Replay r = record(5L, 600);
        List<String> hashes = new ArrayList<>(r.hashes());
        hashes.set(3, "0000000000000000");
        Replay tampered = new Replay(r.schemaVersion(), r.name(), r.note(), r.contentHash(), r.seed(), r.startRoom(), r.dev(),
                r.checkpointEvery(), r.input(), hashes);
        ReplayRunner.Result result = ReplayRunner.run(content, tampered);
        assertThat(result.passed()).isFalse();
        assertThat(result.divergedAtTick()).isEqualTo(200);
    }

    @Test
    void aFileWhoseHashesDoNotFitItsTicksIsRejected() throws Exception {
        Replay r = record(5L, 300);
        Replay shortOfHashes = new Replay(r.schemaVersion(), r.name(), r.note(), r.contentHash(), r.seed(), r.startRoom(),
                r.dev(), r.checkpointEvery(), r.input(), r.hashes().subList(0, 2));
        Path file = dir.resolve("short.json");
        shortOfHashes.write(file);
        assertThat(Files.readString(file)).contains("\"checkpointEvery\": 50");
        assertThatThrownBy(() -> Replay.read(file)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hashes");
    }
}
