package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wulf.Content;
import wulf.data.HighScores;
import wulf.data.JsonDb;
import wulf.data.PlayerDb;
import wulf.data.ShellConfig;
import wulf.input.InputState;
import wulf.input.MenuInput;
import wulf.sim.Simulation;

/**
 * The state machine of AGENTS.md §17.1, walked with nothing but input — no
 * window, no clock, no sound. Every page the title offers must open and close,
 * and a finished run must reach either the hi-score table or the title, never a
 * dead end.
 */
class ShellTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final Content CONTENT = Content.load(DB);
    private static final ShellConfig CONFIG = CONTENT.shell();

    private static final InputState FIRE = new InputState(false, false, false, false, true, true, false, false,
            false, false, false);
    private static final InputState QUIT = new InputState(false, false, false, false, false, false, false, true,
            false, false, false);

    private PlayerDb db(Path dir) {
        return new PlayerDb(dir, DB, CONTENT.defaultScores(), CONTENT.defaultSettings());
    }

    private Shell shell(PlayerDb db) {
        return shell(db, null);
    }

    private Shell shell(PlayerDb db, java.util.function.Supplier<Shell.Demo> demo) {
        return new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()),
                GameSessionTest::sim, demo, () -> "2026-09-19");
    }

    private static void idle(Shell shell, int ticks) {
        for (int i = 0; i < ticks; i++) {
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
    }

    @Test
    void itStartsOnTheTitleAndFireBeginsAGame(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(shell.session()).isNotNull();
    }

    @Test
    void everyTitleMenuKeyOpensItsPageAndFireCloneItAgain(@TempDir Path dir) {
        for (ShellConfig.MenuItem item : CONFIG.title().menu()) {
            Shell shell = shell(db(dir));
            shell.tick(InputState.NONE, MenuInput.of(item.key()), false);
            assertThat(shell.state()).as("%s opens %s", item.key(), item.screen())
                    .isEqualTo(Shell.State.valueOf(item.screen()));
            idle(shell, CONFIG.screenHoldTicks());
            shell.tick(FIRE, MenuInput.NONE, false);
            assertThat(shell.state()).as("%s closes again", item.screen()).isEqualTo(Shell.State.TITLE);
        }
    }

    @Test
    void aPageIgnoresFireForABeatSoTheKeyThatOpenedItCannotCloseIt(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        shell.tick(InputState.NONE, MenuInput.of("C"), false);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.CREDITS);
    }

    @Test
    void theKeysPageWritesTheChosenProfile(@TempDir Path dir) {
        PlayerDb db = db(dir);
        List<String> profiles = List.copyOf(CONTENT.input().profiles().keySet());
        Shell shell = shell(db);
        shell.tick(InputState.NONE, MenuInput.of("K"), false);
        shell.tick(InputState.NONE, MenuInput.of("2"), false);
        assertThat(db.settings().inputProfile()).isEqualTo(profiles.get(1));
        shell.tick(InputState.NONE, MenuInput.of("1"), false);
        assertThat(db.settings().inputProfile()).isEqualTo(profiles.get(0));
    }

    @Test
    void escapeOnTheTitleQuitsButEscapeInTheJungleOnlyLeavesIt(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        shell.tick(FIRE, MenuInput.NONE, false);
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.quit()).isFalse();
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.quit()).isTrue();
    }

    @Test
    void anEmptyRunGoesStraightBackToTheTitle(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        shell.tick(FIRE, MenuInput.NONE, false);
        Simulation sim = shell.session().sim();
        GameSessionTest.playUntilGameOver(shell.session(), sim);
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.GAME_OVER);
        idle(shell, CONFIG.gameOver().holdTicks());
        // Nothing was scored, so there is no place to enter a name for.
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
    }

    @Test
    void aScoringRunEarnsItsPlaceAndTheNameIsWritten(@TempDir Path dir) {
        PlayerDb db = db(dir);
        db.putScores(HighScores.empty());
        Shell shell = new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()),
                GameSessionTest::scoringSim, null, () -> "2026-09-19");
        shell.tick(FIRE, MenuInput.NONE, false);
        Simulation sim = shell.session().sim();
        for (int i = 0; i < 40 && sim.score() == 0; i++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        assertThat(sim.score()).isPositive();
        GameSessionTest.playUntilGameOver(shell.session(), sim);
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        idle(shell, CONFIG.gameOver().holdTicks());
        assertThat(shell.state()).isEqualTo(Shell.State.HISCORE_ENTRY);

        // Fire once per letter: the last one commits.
        for (int i = 0; i < CONFIG.hiScores().nameLength(); i++) {
            shell.tick(FIRE, MenuInput.NONE, false);
        }
        assertThat(shell.state()).isEqualTo(Shell.State.HISCORES);
        assertThat(db.scores().entries()).hasSize(1);
        assertThat(db.scores().entries().get(0).date()).isEqualTo("2026-09-19");
        assertThat(shell.highlight()).isZero();
    }

    @Test
    void aFinishedRunAlwaysLandsInTheLedger(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = shell(db);
        assertThat(db.stats().gamesPlayed()).isZero();
        shell.tick(FIRE, MenuInput.NONE, false);
        GameSessionTest.playUntilGameOver(shell.session(), shell.session().sim());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        idle(shell, CONFIG.gameOver().holdTicks());
        assertThat(db.stats().gamesPlayed()).isEqualTo(1);
        assertThat(db.stats().deaths()).isEqualTo(CONTENT.player().lives().start());
        assertThat(db.stats().escapes()).isZero();
    }

    @Test
    void theTitleFallsIntoAttractModeWhenLeftAloneAndAnyKeyEndsIt(@TempDir Path dir) {
        Shell shell = shell(db(dir), ShellTest::demo);
        idle(shell, CONFIG.attract().idleTicks());
        assertThat(shell.state()).isEqualTo(Shell.State.ATTRACT);
        long before = shell.demo().sim().tick();
        idle(shell, 10);
        assertThat(shell.demo().sim().tick()).isGreaterThan(before);
        shell.tick(InputState.NONE, MenuInput.of("Q"), false);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
    }

    @Test
    void withoutADemoTheTitleSimplyWaits(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        idle(shell, CONFIG.attract().idleTicks() * 2);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
    }

    @Test
    void touchingAnythingKeepsTheTitleAwake(@TempDir Path dir) {
        Shell shell = shell(db(dir), ShellTest::demo);
        for (int i = 0; i < CONFIG.attract().idleTicks() * 2; i++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
    }

    @Test
    void thereIsNoTuneForTheJungleItself() {
        // §18.3 and §27 rule 8: during play there is only the jungle. Silence is the tension.
        assertThat(Shell.tuneFor(Shell.State.PLAYING)).isNull();
        assertThat(Shell.tuneFor(Shell.State.TITLE)).isEqualTo(wulf.data.MusicData.TITLE);
        assertThat(Shell.tuneFor(Shell.State.ATTRACT)).isEqualTo(wulf.data.MusicData.TITLE);
        assertThat(Shell.tuneFor(Shell.State.TALLY)).isEqualTo(wulf.data.MusicData.WIN);
        assertThat(Shell.tuneFor(Shell.State.GAME_OVER)).isEqualTo(wulf.data.MusicData.GAME_OVER);
    }

    @Test
    void theShellAsksForItsTunesAndItsClicks(@TempDir Path dir) {
        List<String> tunes = new java.util.ArrayList<>();
        List<String> clicks = new java.util.ArrayList<>();
        Shell shell = shell(db(dir));
        shell.jukebox(new Jukebox() {
            @Override
            public void tune(String id) {
                tunes.add(String.valueOf(id));
            }

            @Override
            public void sfx(String id) {
                clicks.add(id);
            }
        });
        assertThat(tunes).containsExactly(wulf.data.MusicData.TITLE);
        shell.tick(InputState.NONE, MenuInput.of("C"), false);
        assertThat(clicks).containsExactly("menu_pick");
        assertThat(tunes).as("the same tune is not restarted between menu pages")
                .containsExactly(wulf.data.MusicData.TITLE, wulf.data.MusicData.TITLE);
        idle(shell, CONFIG.screenHoldTicks());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(tunes).as("the jungle is silent").endsWith("null");
    }

    @Test
    void theSoundPageTurnsTheSoundOffAndChangesTheVolume(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = shell(db);
        int was = db.settings().audio().volumePercent();
        int step = CONFIG.sound().volumeStepPercent();
        shell.tick(InputState.NONE, MenuInput.of("A"), false);
        assertThat(shell.state()).isEqualTo(Shell.State.SOUND);

        shell.tick(InputState.NONE, MenuInput.of("2"), false);
        assertThat(db.settings().audio().enabled()).isFalse();
        shell.tick(InputState.NONE, MenuInput.of("1"), false);
        assertThat(db.settings().audio().enabled()).isTrue();

        shell.tick(InputState.of(-1, 0, false, false), MenuInput.NONE, false);
        assertThat(db.settings().audio().volumePercent()).isEqualTo(was - step);
        shell.tick(InputState.of(-1, 0, false, false), MenuInput.NONE, false);
        assertThat(db.settings().audio().volumePercent()).as("held, not tapped").isEqualTo(was - step);
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        assertThat(db.settings().audio().volumePercent()).isEqualTo(was);
    }

    @Test
    void theVolumeStopsAtBothEnds(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = shell(db);
        shell.tick(InputState.NONE, MenuInput.of("A"), false);
        for (int i = 0; i < 40; i++) {
            shell.tick(InputState.of(-1, 0, false, false), MenuInput.NONE, false);
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
        assertThat(db.settings().audio().volumePercent()).isZero();
        for (int i = 0; i < 40; i++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
        assertThat(db.settings().audio().volumePercent()).isEqualTo(100);
    }

    private static Shell.Demo demo() {
        wulf.engine.Replay replay = wulf.engine.Replay.read(Path.of("replays/attract.json"));
        Simulation sim = Simulation.startingIn(CONTENT.player(), new wulf.world.WorldGrid(CONTENT.rooms()),
                CONTENT.game().transition().freezeTicks(), replay.start(), CONTENT.ecosystem(), replay.seed());
        wulf.input.RecordedInput input = replay.recorded();
        return new Shell.Demo(sim, input.cursor(), input.ticks(), replay.dev());
    }
}
