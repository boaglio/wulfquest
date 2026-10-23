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

    private static final InputState DOWN = InputState.of(0, 1, false, false);
    private static final InputState RIGHT = InputState.of(1, 0, false, false);

    /** One press and a release, {@code times} over: the menus move on the press. */
    private static void press(Shell shell, InputState in, int times) {
        for (int i = 0; i < times; i++) {
            shell.tick(in, MenuInput.NONE, false);
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
    }

    private static int line(List<ShellConfig.Entry> lines, String id) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new AssertionError("no line " + id);
    }

    @Test
    void theTitleIsAShortMenuAndExitQuits(@TempDir Path dir) {
        // §17.2: with no game saved, START, OPTIONS and EXIT; down and fire picks.
        Shell shell = shell(db(dir));
        assertThat(shell.titleLines()).extracting(ShellConfig.Entry::id).containsExactly("start", "options", "exit");
        assertThat(shell.cursor()).isZero();
        press(shell, DOWN, line(shell.titleLines(), "exit"));
        assertThat(shell.quit()).isFalse();
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.quit()).isTrue();
    }

    @Test
    void spaceOrEnterChoosesOnAPageButIsNothingInTheJungle(@TempDir Path dir) {
        // §19.1: the menuSelect keys pick the line, whatever the profile's fire is.
        Shell shell = shell(db(dir));
        press(shell, DOWN, line(shell.titleLines(), "options"));
        shell.tick(InputState.NONE, MenuInput.named(MenuInput.SELECT), false);
        assertThat(shell.state()).isEqualTo(Shell.State.OPTIONS);

        Shell playing = shell(db(dir.resolve("b")));
        playing.tick(FIRE, MenuInput.NONE, false);
        assertThat(playing.state()).isEqualTo(Shell.State.PLAYING);
        idle(playing, 30);   // past the room's opening freeze
        assertThat(playing.session().sim().player().swinging()).isFalse();
        playing.tick(InputState.NONE, MenuInput.named(MenuInput.SELECT), false);
        assertThat(playing.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(playing.session().sim().player().swinging()).isFalse();
        playing.tick(FIRE, MenuInput.NONE, false);
        assertThat(playing.session().sim().player().swinging()).isTrue();
    }

    @Test
    void theTitlesOptionsHoldThePagesItUsedToListAndComeBackToThem(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = shell(db);
        press(shell, DOWN, line(shell.titleLines(), "options"));
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.OPTIONS);
        assertThat(shell.optionLines()).extracting(ShellConfig.Entry::id)
                .contains("difficulty", "keys", "load", "hiScores", "stats")
                .doesNotContain("credits");   // only after a win now (§17.2)
        idle(shell, CONFIG.screenHoldTicks());

        // The difficulty switches here now (§17.8).
        boolean easy = shell.easy();
        press(shell, DOWN, line(shell.optionLines(), "difficulty"));
        press(shell, FIRE, 1);
        assertThat(shell.easy()).isNotEqualTo(easy);
        assertThat(db.settings().easy()).isEqualTo(shell.easy());

        // A page opened from here goes back here, on its own line.
        int stats = line(shell.optionLines(), "stats");
        press(shell, DOWN, stats - shell.cursor());
        press(shell, FIRE, 1);
        assertThat(shell.state()).isEqualTo(Shell.State.STATS);
        idle(shell, CONFIG.screenHoldTicks());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.OPTIONS);
        assertThat(shell.cursor()).isEqualTo(stats);

        // And Escape goes back to the title, on OPTIONS.
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        assertThat(shell.titleLines().get(shell.cursor()).id()).isEqualTo("options");
    }

    @Test
    void overAGameTheOptionsLeaveOutTheTitlesOwnLines(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        shell.tick(FIRE, MenuInput.NONE, false);
        shell.tick(new InputState(false, false, false, false, false, false, true, false, false, false, false),
                MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PAUSED);
        press(shell, DOWN, line(CONFIG.pause().items(), "options"));
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.OPTIONS);
        assertThat(shell.optionLines()).extracting(ShellConfig.Entry::id)
                .doesNotContainAnyElementsOf(ShellConfig.Options.TITLE_ONLY)
                .contains("sound", "back");
    }

    @Test
    void leftAndRightOnStartChooseTheJobOnceOneIsOpen(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        shell.practiseJob(2);
        assertThat(shell.level()).isEqualTo(2);
        press(shell, RIGHT, 1);
        assertThat(shell.level()).as("past the last, back to the first").isZero();
        press(shell, InputState.of(-1, 0, false, false), 1);
        assertThat(shell.level()).isEqualTo(2);
    }

    @Test
    void everyTitleMenuKeyOpensItsPageAndFireCloneItAgain(@TempDir Path dir) {
        for (ShellConfig.MenuItem item : CONFIG.title().menu()) {
            Shell shell = shell(db(dir));
            shell.tick(InputState.NONE, MenuInput.of(item.key()), false);
            assertThat(shell.state()).as("%s opens %s", item.key(), item.screen())
                    .isEqualTo(Shell.State.valueOf(item.screen()));
            idle(shell, CONFIG.screenHoldTicks());
            // On the menus (§17.9, §21.4) fire picks a line; Escape is the way back, as on every page.
            boolean menu = item.screen().equals("LOAD_GAME") || item.screen().equals("OPTIONS");
            shell.tick(menu ? QUIT : FIRE, MenuInput.NONE, false);
            assertThat(shell.state()).as("%s closes again", item.screen()).isEqualTo(Shell.State.TITLE);
        }
    }

    @Test
    void aPageIgnoresFireForABeatSoTheKeyThatOpenedItCannotCloseIt(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        shell.tick(InputState.NONE, MenuInput.of("S"), false);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.STATS);
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
    void aPracticeRunNeverReachesTheTableOrTheLedger(@TempDir Path dir) {
        // §17.6: --lives, --infinite-lives and --god make a practice run; it scores, and is not kept.
        PlayerDb db = db(dir);
        db.putScores(HighScores.empty());
        long gamesBefore = db.stats().gamesPlayed();
        Shell shell = new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()), () -> {
            Simulation practice = GameSessionTest.scoringSim();
            practice.infiniteLives(true);
            return practice;
        }, null, () -> "2026-10-01");
        shell.tick(FIRE, MenuInput.NONE, false);
        Simulation sim = shell.session().sim();
        for (int i = 0; i < 400 && sim.score() == 0; i++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        assertThat(sim.score()).as("it scored").isPositive();
        GameSessionTest.playUntilGameOver(shell.session(), sim);
        idle(shell, CONFIG.gameOver().holdTicks() + 1);
        shell.tick(GameSessionTest.pressed(false, false, true, false, false), MenuInput.NONE, false);
        idle(shell, 5);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        assertThat(db.scores().entries()).as("no name asked for, none written").isEmpty();
        assertThat(db.stats().gamesPlayed()).as("and the ledger untouched").isEqualTo(gamesBefore);
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
    void theDifficultyKeySwitchesBetweenHardAndEasyAndIsRemembered(@TempDir Path dir) {
        // §17.8: Hard to begin with — the original's game — and D on the title switches, kept in the settings.
        PlayerDb db = db(dir);
        boolean[] asked = new boolean[1];
        Shell.GameMaker maker = (level, easy) -> {
            asked[0] = easy;
            return GameSessionTest.sim();
        };
        Shell shell = new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()), maker, null,
                () -> "2026-10-12");
        assertThat(shell.easy()).isFalse();
        shell.tick(InputState.NONE, MenuInput.of(CONFIG.difficulty().key()), false);
        assertThat(shell.easy()).isTrue();
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        assertThat(db.settings().easy()).isTrue();
        assertThat(new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()), maker, null,
                () -> "2026-10-12").easy()).as("a new session remembers").isTrue();
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(asked[0]).as("the game is made easy").isTrue();
    }

    @Test
    void anEasyRunIsMarkedOnTheTable(@TempDir Path dir) {
        PlayerDb db = db(dir);
        db.putScores(HighScores.empty());
        Shell shell = new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()),
                (Shell.GameMaker) (level, easy) -> GameSessionTest.scoringSimIn(easy ? CONTENT.game().easy() : null),
                null, () -> "2026-10-12");
        shell.tick(InputState.NONE, MenuInput.of(CONFIG.difficulty().key()), false);
        shell.tick(FIRE, MenuInput.NONE, false);
        Simulation sim = shell.session().sim();
        for (int i = 0; i < 40 && sim.score() == 0; i++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        assertThat(sim.player().lives()).as("Easy's lives").isEqualTo(CONTENT.game().easy().livesStart());
        GameSessionTest.playUntilGameOver(shell.session(), sim, sim.player().lives());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        idle(shell, CONFIG.gameOver().holdTicks());
        assertThat(shell.state()).isEqualTo(Shell.State.HISCORE_ENTRY);
        for (int i = 0; i < CONFIG.hiScores().nameLength(); i++) {
            shell.tick(FIRE, MenuInput.NONE, false);
        }
        assertThat(db.scores().entries()).hasSize(1);
        assertThat(db.scores().entries().get(0).easy()).isTrue();
    }

    @Test
    void gameOverOffersToGoOnAndFireDoes(@TempDir Path dir) {
        // §17.5: three continues; fire goes on where he fell, the score from nothing; escape banks the run's best.
        PlayerDb db = db(dir);
        db.putScores(HighScores.empty());
        Shell shell = new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()),
                () -> GameSessionTest.continuingSim(3), null, () -> "2026-10-12");
        shell.tick(FIRE, MenuInput.NONE, false);
        Simulation sim = shell.session().sim();
        for (int i = 0; i < 40 && sim.score() == 0; i++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        long scored = sim.score();
        GameSessionTest.playUntilGameOver(shell.session(), sim);
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.GAME_OVER);
        assertThat(shell.continuing()).isTrue();
        idle(shell, CONFIG.gameOver().holdTicks());
        assertThat(shell.state()).as("the offer outlasts the usual hold").isEqualTo(Shell.State.GAME_OVER);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(sim.player().mode()).isEqualTo(wulf.sim.Player.Mode.ALIVE);
        assertThat(sim.score()).isZero();
        assertThat(sim.continuesLeft()).isEqualTo(2);
        assertThat(shell.session().played().ticks()).as("the continue is in the input").isEqualTo(sim.tick());

        GameSessionTest.playUntilGameOver(shell.session(), sim);
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.GAME_OVER);
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.state()).as("escape banks it").isEqualTo(Shell.State.HISCORE_ENTRY);
        for (int i = 0; i < CONFIG.hiScores().nameLength(); i++) {
            shell.tick(FIRE, MenuInput.NONE, false);
        }
        assertThat(db.scores().entries().get(0).score()).as("the run's best").isEqualTo(scored);
    }

    @Test
    void theOfferRunsOut(@TempDir Path dir) {
        Shell shell = new Shell(CONFIG, db(dir), List.copyOf(CONTENT.input().profiles().keySet()),
                () -> GameSessionTest.continuingSim(3), null, () -> "2026-10-12");
        shell.tick(FIRE, MenuInput.NONE, false);
        GameSessionTest.playUntilGameOver(shell.session(), shell.session().sim());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.GAME_OVER);
        idle(shell, CONFIG.gameOver().continueTicks());
        assertThat(shell.state()).isNotEqualTo(Shell.State.GAME_OVER);
    }

    @Test
    void theEndingsShowPlaysEveryJobsEndingInTurnAndTouchesNothing(@TempDir Path dir) {
        // §17.6, --endings: each job won on the spot — the scene, the tally, the roll, the offer if any.
        PlayerDb db = db(dir);
        HighScores before = db.scores();
        List<Integer> made = new java.util.ArrayList<>();
        Shell shell = new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()),
                (Shell.GameMaker) (level, easy) -> {
                    made.add(level);
                    wulf.sim.Ecosystem eco = level == 0 ? wulf.sim.Ecosystem.NONE
                            : wulf.sim.Ecosystem.NONE.atLevel(level, CONTENT.game().newGamePlus().level(level));
                    return Simulation.at(CONTENT.player(), (gx, gy) -> false, 0, wulf.engine.Fixed.fp(1000),
                            wulf.engine.Fixed.fp(1000), eco, 1L);
                }, null, () -> "2026-10-12");
        shell.showEndings();
        List<Integer> offers = new java.util.ArrayList<>();
        List<Shell.State> seen = new java.util.ArrayList<>();
        for (int i = 0; i < 60_000 && shell.showingEndings(); i++) {
            shell.tick(i % 2 == 0 ? FIRE : InputState.NONE, MenuInput.NONE, false);
            if (seen.isEmpty() || seen.get(seen.size() - 1) != shell.state()) {
                seen.add(shell.state());
                if (shell.state() == Shell.State.OFFER) {
                    offers.add(shell.offered());
                }
            }
        }
        assertThat(shell.showingEndings()).isFalse();
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        assertThat(made).containsExactly(0, 1, 2);
        assertThat(offers).as("the game's ending offers job 1, job 1's job 2, the last none").containsExactly(1, 2);
        assertThat(java.util.Collections.frequency(seen, Shell.State.ENDING)).isEqualTo(3);
        assertThat(java.util.Collections.frequency(seen, Shell.State.ROLL)).isEqualTo(3);
        assertThat(db.stats().unlocked()).as("nothing unlocked").isZero();
        assertThat(db.scores()).as("nothing on the table").isEqualTo(before);
        assertThat(shell.level()).isZero();
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
        assertThat(db.stats().deaths()).isEqualTo(CONTENT.player().lives().start() + 1);   // the reserve, then the last fall
        assertThat(db.stats().escapes()).isZero();
    }

    @Test
    void theTitleFallsIntoAttractModeWhenLeftAloneAndAnyKeyEndsIt(@TempDir Path dir) {
        Shell shell = shell(db(dir), ShellTest::demo);
        idle(shell, CONFIG.attract().idleTicks());
        assertThat(shell.state()).as("the plane lands first").isEqualTo(Shell.State.INTRO);
        idle(shell, CONFIG.attract().intro().ticks());
        assertThat(shell.state()).isEqualTo(Shell.State.ATTRACT);
        long before = shell.demo().sim().tick();
        idle(shell, 10);
        assertThat(shell.demo().sim().tick()).isGreaterThan(before);
        shell.tick(InputState.NONE, MenuInput.of("Q"), false);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
    }

    @Test
    void theOptionsSizeTheWindowAndPutItOverTheWholeScreen(@TempDir Path dir) {
        // §17.9: SCALE steps through options.scales with left and right (fire steps up, and round);
        // FULLSCREEN switches. Both are written, and the window follows them as it draws.
        PlayerDb db = db(dir);
        Shell shell = shell(db);
        press(shell, DOWN, line(shell.titleLines(), "options"));
        shell.tick(FIRE, MenuInput.NONE, false);
        idle(shell, CONFIG.screenHoldTicks());
        List<Integer> scales = CONFIG.options().scales();
        int was = db.settings().scale();
        press(shell, DOWN, line(shell.optionLines(), "scale"));
        press(shell, RIGHT, 1);
        assertThat(db.settings().scale()).isEqualTo(scales.get((scales.indexOf(was) + 1) % scales.size()));
        press(shell, InputState.of(-1, 0, false, false), 1);
        assertThat(db.settings().scale()).isEqualTo(was);

        assertThat(db.settings().fullscreen()).isFalse();
        press(shell, DOWN, 1);
        assertThat(shell.optionLines().get(shell.cursor()).id()).isEqualTo("fullscreen");
        press(shell, FIRE, 1);
        assertThat(db.settings().fullscreen()).isTrue();
        press(shell, FIRE, 1);
        assertThat(db.settings().fullscreen()).isFalse();

        // F11 switches it anywhere: here, and in the jungle.
        shell.tick(InputState.NONE, MenuInput.named(CONFIG.options().fullscreenKey()), false);
        assertThat(db.settings().fullscreen()).isTrue();
        shell.tick(QUIT, MenuInput.NONE, false);
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        shell.tick(FIRE, MenuInput.NONE, false);
        shell.tick(InputState.NONE, MenuInput.named(CONFIG.options().fullscreenKey()), false);
        assertThat(db.settings().fullscreen()).isFalse();
    }

    @Test
    void anyKeyDuringThePlaneSceneBringsTheTitleBack(@TempDir Path dir) {
        // §17.2: the idle title's scene, a plane landing in the forest, before the demo.
        Shell shell = shell(db(dir), ShellTest::demo);
        idle(shell, CONFIG.attract().idleTicks());
        assertThat(shell.state()).isEqualTo(Shell.State.INTRO);
        idle(shell, 10);
        shell.tick(InputState.NONE, MenuInput.of("Q"), false);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        idle(shell, CONFIG.attract().idleTicks() - 1);
        assertThat(shell.state()).as("the wait starts again").isEqualTo(Shell.State.TITLE);
    }

    @Test
    void withoutADemoThePlaneSceneGoesBackToTheTitle(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        idle(shell, CONFIG.attract().idleTicks());
        assertThat(shell.state()).isEqualTo(Shell.State.INTRO);
        idle(shell, CONFIG.attract().intro().ticks());
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

    /** A shell whose game is won on the first tick of play, with a jukebox that writes down what it is asked for. */
    private Shell won(PlayerDb db, List<String> tunes) {
        Shell shell = shell(db);
        shell.jukebox(new Jukebox() {
            @Override
            public void tune(String id) {
                tunes.add(id);
            }

            @Override
            public void sfx(String id) {
                // not listened for
            }
        });
        shell.tick(FIRE, MenuInput.NONE, false);
        wulf.sim.WonGames.win(shell.session().sim());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        return shell;
    }

    @Test
    void aWonGameGoesToTheClosingSceneThenTheTallyThenTheCredits(@TempDir Path dir) {
        // §14.7: the scene, the sums, the roll — and then, as from any run, the table or the title.
        PlayerDb db = db(dir);
        List<String> tunes = new java.util.ArrayList<>();
        Shell shell = won(db, tunes);
        assertThat(shell.state()).isEqualTo(Shell.State.ENDING);
        assertThat(tunes.get(tunes.size() - 1)).as("the scene opens in silence").isNull();

        ShellConfig.Ending ending = CONFIG.ending();
        idle(shell, ending.tuneAt() - 1);
        assertThat(tunes.get(tunes.size() - 1)).as("not yet").isNull();
        idle(shell, 1);
        assertThat(tunes.get(tunes.size() - 1)).as("its tune comes in on its tick").isEqualTo(ending.tune());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).as("fire waits for the prompt").isEqualTo(Shell.State.ENDING);
        idle(shell, ending.promptAt());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.TALLY);

        idle(shell, CONFIG.tally().holdTicks());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.ROLL);
        assertThat(db.stats().gamesPlayed()).as("not banked until it is all over").isZero();

        idle(shell, CONFIG.credits().rollTicks());
        // §14.9: the first win, so Mr Jones has a new job.
        assertThat(shell.state()).as("the roll ends on its own").isEqualTo(Shell.State.OFFER);
        assertThat(shell.offered()).isEqualTo(1);
        assertThat(tunes.get(tunes.size() - 1)).as("he talks in silence").isNull();
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).as("fire waits for his words").isEqualTo(Shell.State.OFFER);
        idle(shell, CONFIG.newGamePlus().levels().get(1).offer().promptAt());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isIn(Shell.State.TITLE, Shell.State.HISCORE_ENTRY);
        assertThat(db.stats().gamesPlayed()).isEqualTo(1);
        assertThat(db.stats().escapes()).isEqualTo(1);
    }

    @Test
    void fireStopsTheRollToReadAndEscapeLeavesTheEndingAtAnyPoint(@TempDir Path dir) {
        Shell shell = won(db(dir), new java.util.ArrayList<>());
        idle(shell, CONFIG.ending().promptAt());
        shell.tick(FIRE, MenuInput.NONE, false);
        idle(shell, CONFIG.tally().holdTicks());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.ROLL);
        idle(shell, CONFIG.screenHoldTicks());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.rollPaused()).as("fire stops it").isTrue();
        long at = shell.rollTick();
        idle(shell, 2 * CONFIG.credits().rollTicks());
        assertThat(shell.state()).as("and it waits as long as it is read").isEqualTo(Shell.State.ROLL);
        assertThat(shell.rollTick()).isEqualTo(at);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.rollPaused()).isFalse();
        idle(shell, 3);
        assertThat(shell.rollTick()).as("fire starts it again").isGreaterThan(at);
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.state()).as("escape skips to Mr Jones's offer").isEqualTo(Shell.State.OFFER);
        idle(shell, 5);
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.state()).as("escape leaves the offer too").isIn(Shell.State.TITLE, Shell.State.HISCORE_ENTRY);

        PlayerDb other = db(dir.resolve("other"));
        Shell left = won(other, new java.util.ArrayList<>());
        idle(left, 5);
        left.tick(QUIT, MenuInput.NONE, false);
        assertThat(left.state()).as("escape banks the run").isIn(Shell.State.TITLE, Shell.State.HISCORE_ENTRY);
        assertThat(other.stats().escapes()).as("and it still counts as an escape").isEqualTo(1);
        assertThat(other.stats().unlocked()).as("and it still opens the next job").isEqualTo(1);
    }

    /** A game of job {@code n} (§14.9) in the empty world: only its number matters to the shell. */
    private static Simulation jobSim(int n) {
        wulf.sim.Ecosystem eco = new wulf.sim.Ecosystem(wulf.data.CreatureData.EMPTY, wulf.sim.RoomPopulator.NONE, 0);
        wulf.world.CollisionWorld open = (gx, gy) -> gx < 0 || gy < 0 || gx >= wulf.world.WorldGrid.COLS
                || gy >= wulf.world.WorldGrid.ROWS;
        return Simulation.at(CONTENT.player(), open, 0, wulf.engine.Fixed.fp(1000),
                wulf.engine.Fixed.fp(1000), n == 0 ? eco : eco.atLevel(n, CONTENT.game().newGamePlus().level(n)), 1L);
    }

    /** A shell whose games are of the job asked for, every request written down. */
    private Shell jobShell(PlayerDb db, List<Integer> asked) {
        return new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()), n -> {
            asked.add(n);
            return jobSim(n);
        }, null, () -> "2026-10-12");
    }

    /** Plays the session's next game to a win and sits through the ending, the sums, the roll and any offer. */
    private static void winAndSitThrough(Shell shell) {
        shell.tick(FIRE, MenuInput.NONE, false);
        wulf.sim.WonGames.win(shell.session().sim());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        for (int i = 0; i < 20_000 && shell.state() != Shell.State.TITLE && shell.state() != Shell.State.HISCORE_ENTRY; i++) {
            shell.tick(i % 2 == 0 ? FIRE : InputState.NONE, MenuInput.NONE, false);
        }
        assertThat(shell.state()).isIn(Shell.State.TITLE, Shell.State.HISCORE_ENTRY);
        while (shell.state() == Shell.State.HISCORE_ENTRY) {
            shell.tick(FIRE, MenuInput.NONE, false);
        }
        for (int i = 0; i < 1_000 && shell.state() != Shell.State.TITLE; i++) {
            shell.tick(i % 2 == 0 ? FIRE : InputState.NONE, MenuInput.NONE, false);
        }
    }

    @Test
    void eachWinOpensTheNextJobWhichTheTitleThenStarts(@TempDir Path dir) {
        // §14.9: the game, then the amulet in two halves, then whole — and after that, no more offers.
        PlayerDb db = db(dir);
        List<Integer> asked = new java.util.ArrayList<>();
        Shell shell = jobShell(db, asked);
        assertThat(shell.unlocked()).isZero();
        shell.tick(InputState.NONE, MenuInput.of(CONFIG.newGamePlus().key()), false);
        assertThat(shell.level()).as("nothing to choose yet").isZero();

        winAndSitThrough(shell);
        assertThat(db.stats().unlocked()).isEqualTo(1);
        assertThat(shell.level()).as("the next game is the new job").isEqualTo(1);
        winAndSitThrough(shell);
        assertThat(db.stats().unlocked()).isEqualTo(2);
        assertThat(shell.level()).isEqualTo(2);

        // The last job won: the credits roll, and there is no offer after them.
        shell.tick(FIRE, MenuInput.NONE, false);
        wulf.sim.WonGames.win(shell.session().sim());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        assertThat(shell.offered()).isZero();
        for (int i = 0; i < 20_000 && shell.state() != Shell.State.ROLL; i++) {
            shell.tick(i % 2 == 0 ? FIRE : InputState.NONE, MenuInput.NONE, false);
        }
        idle(shell, CONFIG.credits().rollTicks());
        assertThat(shell.state()).isIn(Shell.State.TITLE, Shell.State.HISCORE_ENTRY);
        assertThat(asked).containsExactly(0, 1, 2);
        assertThat(db.stats().unlocked()).isEqualTo(2);
    }

    @Test
    void theTitleKeyStepsThroughTheJobsOpenAndAFreshShellRemembersThem(@TempDir Path dir) {
        PlayerDb db = db(dir);
        db.putStats(db.stats().unlocking(1));
        List<Integer> asked = new java.util.ArrayList<>();
        Shell shell = jobShell(db, asked);
        assertThat(shell.level()).as("it starts on the newest").isEqualTo(1);
        String key = CONFIG.newGamePlus().key();
        shell.tick(InputState.NONE, MenuInput.of(key), false);
        assertThat(shell.level()).as("back to the game itself").isZero();
        shell.tick(InputState.NONE, MenuInput.of(key), false);
        assertThat(shell.level()).isEqualTo(1);
        shell.tick(InputState.NONE, MenuInput.of(key), false);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(asked).containsExactly(0);
        assertThat(shell.session().sim().level()).isZero();

        // Winning the game itself again opens nothing new, and offers nothing.
        wulf.sim.WonGames.win(shell.session().sim());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        assertThat(shell.offered()).isZero();
    }

    @Test
    void aPracticeWinOpensTheNextJobForThisSessionOnly(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()), n -> {
            Simulation sim = jobSim(n);
            sim.markPractice();
            return sim;
        }, null, () -> "2026-10-12");
        winAndSitThrough(shell);
        assertThat(shell.unlocked()).as("this session can play it").isEqualTo(1);
        assertThat(db.stats().unlocked()).as("but the ledger never heard of it").isZero();

        Shell skipped = jobShell(db(dir.resolve("other")), new java.util.ArrayList<>());
        skipped.practiseJob(2);
        assertThat(skipped.level()).isEqualTo(2);
        assertThat(skipped.unlocked()).isEqualTo(2);
    }

    @Test
    void thereIsNoTuneForTheJungleItself() {
        // §18.3 and §27 rule 8: during play there is only the jungle. Silence is the tension.
        assertThat(Shell.tuneFor(Shell.State.PLAYING)).isNull();
        assertThat(Shell.tuneFor(Shell.State.TITLE)).isEqualTo(wulf.data.MusicData.TITLE);
        assertThat(Shell.tuneFor(Shell.State.ATTRACT)).isEqualTo(wulf.data.MusicData.TITLE);
        assertThat(Shell.tuneFor(Shell.State.TALLY)).isEqualTo(wulf.data.MusicData.WIN);
        assertThat(Shell.tuneFor(Shell.State.ENDING)).as("the scene's own tune waits for its tick").isNull();
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
        shell.tick(InputState.NONE, MenuInput.of("S"), false);
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

    @Test
    void theMapKeyIsNeverAKeyTheLayoutPlaysWith(@TempDir Path dir) {
        // §17.7: in the period layout M swings the sabre, so the map is B there; M elsewhere.
        PlayerDb db = db(dir);
        db.putSettings(db.settings().withInputProfile("period"));
        Shell shell = shell(db);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        idle(shell, CONFIG.screenHoldTicks());
        shell.tick(FIRE, MenuInput.of("M"), false);   // the M key: fire in this layout, and typed
        assertThat(shell.session().mapOpen()).as("swinging does not open the map").isFalse();
        shell.tick(InputState.NONE, MenuInput.of("B"), false);
        assertThat(shell.session().mapOpen()).isTrue();
        assertThat(CONFIG.map().messageFor("period")).isEqualTo("MAP: PRESS B TO RETURN");
        assertThat(CONFIG.map().messageFor("modern")).isEqualTo("MAP: PRESS M TO RETURN");
        shell.tick(InputState.NONE, MenuInput.of("B"), false);
        assertThat(shell.session().mapOpen()).isFalse();

        db.putSettings(db.settings().withInputProfile("modern"));
        shell.tick(InputState.NONE, MenuInput.of("M"), false);
        assertThat(shell.session().mapOpen()).isTrue();
    }

    @Test
    void thePadsButtonsStandInForTheShellsLetters(@TempDir Path dir) {
        // §19.3: the title's D and G, and the map's key in the jungle, have a button each.
        PlayerDb db = db(dir);
        Shell shell = shell(db);
        MenuInput difficulty = new MenuInput(List.of(), true, List.of(), java.util.Set.of(MenuInput.Pad.DIFFICULTY));
        shell.tick(InputState.NONE, difficulty, false);
        assertThat(shell.easy()).isTrue();
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        shell.tick(InputState.NONE, new MenuInput(List.of(), true, List.of(), java.util.Set.of(MenuInput.Pad.NEW_GAME)),
                false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        idle(shell, CONFIG.screenHoldTicks());
        shell.tick(InputState.NONE, new MenuInput(List.of(), true, List.of(), java.util.Set.of(MenuInput.Pad.MAP)), false);
        assertThat(shell.session().mapOpen()).isTrue();
    }
}
