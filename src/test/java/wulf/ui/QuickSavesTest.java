package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.function.LongFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wulf.Content;
import wulf.data.JsonDb;
import wulf.data.PlayerDb;
import wulf.data.QuickSaveData;
import wulf.input.InputState;
import wulf.input.MenuInput;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §21.4 — a quick save is the game's seed and input; loading replays it to the same state. */
class QuickSavesTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final Content CONTENT = Content.load(DB);
    private static final WorldGrid WORLD = new WorldGrid(CONTENT.rooms());
    private static final RoomAddress START = CONTENT.map().startRoom();
    private static final LongFunction<Simulation> GAME = seed -> Simulation.startingIn(CONTENT.player(), WORLD,
            CONTENT.game().transition().freezeTicks(), START, CONTENT.ecosystem(), seed);

    private static PlayerDb db(Path dir) {
        return new PlayerDb(dir, DB, CONTENT.defaultScores(), CONTENT.defaultSettings());
    }

    private static QuickSaves saves(PlayerDb db, String contentHash) {
        return new QuickSaves(db, contentHash, START, false, GAME, sim -> { });
    }

    /** A busy few seconds: walking, turning, swinging. */
    private static void play(GameSession s, int from, int ticks) {
        for (int t = from; t < from + ticks; t++) {
            int phase = (t / 37) % 4;
            int dx = phase == 0 ? 1 : phase == 2 ? -1 : 0;
            int dy = phase == 1 ? -1 : phase == 3 ? 1 : 0;
            s.tick(InputState.of(dx, dy, t % 23 < 4, t % 23 == 0), false);
        }
    }

    @Test
    void aLoadComesBackToExactlyTheMomentSaved(@TempDir Path dir) {
        PlayerDb db = db(dir);
        QuickSaves saves = saves(db, CONTENT.db().contentHash());
        GameSession s = new GameSession(GAME.apply(42L));
        play(s, 0, 500);
        long hash = s.sim().stateHash();
        long tick = s.sim().tick();
        saves.save(s);
        play(s, 500, 900);
        assertThat(s.sim().stateHash()).as("the game went on").isNotEqualTo(hash);

        QuickSaves.Loaded back = saves.load();
        assertThat(back.ok()).as(back.refusal()).isTrue();
        assertThat(back.sim().tick()).isEqualTo(tick);
        assertThat(back.played().ticks()).isEqualTo(500);
        assertThat(back.sim().practice()).as("a load makes it practice").isTrue();
        Simulation unpracticed = GAME.apply(42L);
        GameSession again = new GameSession(unpracticed);
        play(again, 0, 500);
        unpracticed.markPractice();
        assertThat(back.sim().stateHash()).isEqualTo(unpracticed.stateHash());
    }

    @Test
    void aLoadedGameCanBeSavedAndLoadedAgain(@TempDir Path dir) {
        PlayerDb db = db(dir);
        QuickSaves saves = saves(db, CONTENT.db().contentHash());
        GameSession s = new GameSession(GAME.apply(7L));
        play(s, 0, 600);
        saves.save(s);
        QuickSaves.Loaded first = saves.load();
        GameSession resumed = new GameSession(first.sim(), wulf.data.ShellConfig.AmuletReveal.NONE, first.played());
        play(resumed, 600, 600);
        long hash = resumed.sim().stateHash();
        saves.save(resumed);
        QuickSaves.Loaded second = saves.load();
        assertThat(second.ok()).as(second.refusal()).isTrue();
        assertThat(second.sim().stateHash()).isEqualTo(hash);
        assertThat(second.played().ticks()).isEqualTo(1200);
    }

    @Test
    void nothingSavedNothingLoaded(@TempDir Path dir) {
        assertThat(saves(db(dir), CONTENT.db().contentHash()).load().refusal()).isEqualTo("NO QUICK SAVE YET");
    }

    @Test
    void anotherVersionsSaveIsRefused(@TempDir Path dir) {
        PlayerDb db = db(dir);
        GameSession s = new GameSession(GAME.apply(3L));
        play(s, 0, 100);
        saves(db, "an older version").save(s);
        assertThat(saves(db, CONTENT.db().contentHash()).load().refusal()).isEqualTo("SAVE IS FROM ANOTHER VERSION");
    }

    @Test
    void aSaveThatDoesNotReplayToItsHashIsRefused(@TempDir Path dir) {
        PlayerDb db = db(dir);
        GameSession s = new GameSession(GAME.apply(3L));
        play(s, 0, 300);
        QuickSaves saves = saves(db, CONTENT.db().contentHash());
        saves.save(s);
        QuickSaveData d = db.quickSave().orElseThrow();
        db.putQuickSave(new QuickSaveData(d.schemaVersion(), d.contentHash(), d.seed() + 1, d.startRoom(), d.dev(),
                d.practiced(), d.ticks(), d.input(), d.stateHash()));
        assertThat(saves.load().refusal()).isEqualTo("SAVE DOES NOT FIT THIS GAME");
    }

    @Test
    void theShellSavesOnF5AndGoesBackOnF9(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = new Shell(CONTENT.shell(), db, List.copyOf(CONTENT.input().profiles().keySet()),
                () -> GAME.apply(11L), null, () -> "2026-10-04");
        shell.quickSaves(saves(db, CONTENT.db().contentHash()));
        shell.tick(InputState.of(0, 0, true, true), MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        for (int t = 0; t < 200; t++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        shell.tick(InputState.NONE, MenuInput.named(CONTENT.shell().quickSave().saveKey()), false);
        assertThat(shell.session().message(false)).isEqualTo("SAVED");
        long saved = shell.session().sim().stateHash();
        for (int t = 0; t < 200; t++) {
            shell.tick(InputState.of(-1, 0, false, false), MenuInput.NONE, false);
        }
        shell.tick(InputState.NONE, MenuInput.named(CONTENT.shell().quickSave().loadKey()), false);
        assertThat(shell.session().message(false)).isEqualTo("LOADED");
        assertThat(shell.session().sim().practice()).isTrue();
        Simulation expected = GAME.apply(11L);
        GameSession replay = new GameSession(expected);
        // The save key's own tick played too (it was a NONE input), so the moment saved is after it.
        for (int t = 0; t < 200; t++) {
            replay.tick(InputState.of(1, 0, false, false), false);
        }
        replay.tick(InputState.NONE, false);
        expected.markPractice();
        assertThat(shell.session().sim().stateHash()).isEqualTo(expected.stateHash());
        assertThat(saved).isNotZero();
    }

    @Test
    void theTitlesLoadKeyResumesTheSavedGame(@TempDir Path dir) {
        PlayerDb db = db(dir);
        QuickSaves saves = saves(db, CONTENT.db().contentHash());
        GameSession s = new GameSession(GAME.apply(5L));
        play(s, 0, 400);
        saves.save(s);
        Shell shell = new Shell(CONTENT.shell(), db, List.copyOf(CONTENT.input().profiles().keySet()),
                () -> GAME.apply(99L), null, () -> "2026-10-04");
        shell.quickSaves(saves);
        shell.tick(InputState.NONE, MenuInput.named(CONTENT.shell().quickSave().loadKey()), false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(shell.session().sim().runSeed()).isEqualTo(5L);
        assertThat(shell.session().sim().tick()).isEqualTo(s.sim().tick());
    }

    @Test
    void withoutTheFeatureTheKeysDoNothing(@TempDir Path dir) {
        Shell shell = new Shell(CONTENT.shell(), db(dir), List.copyOf(CONTENT.input().profiles().keySet()),
                () -> GAME.apply(11L), null, () -> "2026-10-04");
        shell.tick(InputState.of(0, 0, true, true), MenuInput.NONE, false);
        shell.tick(InputState.NONE, MenuInput.named(CONTENT.shell().quickSave().saveKey()), false);
        assertThat(shell.session().message(false)).isNull();
        assertThat(CONTENT.game().feature("quickSave")).as("[NEW]: off by default (§1.1)").isFalse();
    }
}
