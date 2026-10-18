package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
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

/**
 * AGENTS.md §21.4, §17.4, §17.9 — three save slots, each the game's seed and input, loading
 * back to the same state; reached from the pause menu and the title, with the options beside them.
 */
class SaveSlotsTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final Content CONTENT = Content.load(DB);
    private static final WorldGrid WORLD = new WorldGrid(CONTENT.rooms());
    private static final RoomAddress START = CONTENT.map().startRoom();
    private static final SaveSlots.GameMaker GAME = (seed, job, easy) -> Simulation.startingIn(CONTENT.player(), WORLD,
            CONTENT.game().transition().freezeTicks(), START, CONTENT.ecosystem(job, easy), seed);
    private static final InputState PAUSE = new InputState(false, false, false, false, false, false, true, false,
            false, false, false);
    private static final InputState QUIT = new InputState(false, false, false, false, false, false, false, true,
            false, false, false);
    private static final InputState FIRE = InputState.of(0, 0, true, true);
    private static final InputState DOWN = InputState.of(0, 1, false, false);

    private static PlayerDb db(Path dir) {
        return new PlayerDb(dir, DB, CONTENT.defaultScores(), CONTENT.defaultSettings());
    }

    private static SaveSlots slots(PlayerDb db, String contentHash) {
        return new SaveSlots(db, contentHash, START, false, GAME, sim -> { }, () -> "2026-10-14");
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
    void aLoadComesBackToExactlyTheMomentSavedAndCountsAsAnyGame(@TempDir Path dir) {
        PlayerDb db = db(dir);
        SaveSlots saves = slots(db, CONTENT.db().contentHash());
        GameSession s = new GameSession(GAME.make(42L, 0, false));
        play(s, 0, 500);
        long hash = s.sim().stateHash();
        long tick = s.sim().tick();
        saves.save(s, 2);
        play(s, 500, 900);
        assertThat(s.sim().stateHash()).as("the game went on").isNotEqualTo(hash);

        SaveSlots.Loaded back = saves.load(2);
        assertThat(back.ok()).as(back.refusal()).isTrue();
        assertThat(back.sim().tick()).isEqualTo(tick);
        assertThat(back.played().ticks()).isEqualTo(500);
        assertThat(back.sim().practice()).as("a loaded game ranks like any other").isFalse();
        assertThat(back.sim().stateHash()).isEqualTo(hash);
        assertThat(saves.load(1).refusal()).as("the other slots are untouched").isEqualTo("EMPTY");
    }

    @Test
    void aSlotSaysWhatIsInIt(@TempDir Path dir) {
        PlayerDb db = db(dir);
        SaveSlots saves = slots(db, CONTENT.db().contentHash());
        GameSession s = new GameSession(GAME.make(42L, 1, true));
        play(s, 0, 300);
        saves.save(s, 3);
        QuickSaveData d = saves.peek(3).orElseThrow();
        assertThat(d.newGamePlus()).isEqualTo(1);
        assertThat(d.easy()).isTrue();
        assertThat(d.date()).isEqualTo("2026-10-14");
        assertThat(d.room()).isEqualTo(s.sim().room().col() + "," + s.sim().room().row());
        assertThat(d.score()).isEqualTo(s.sim().score());
        assertThat(MenuScreen.slotLine(CONTENT.shell().saves(), 3, d)).startsWith("3  EASY NG+  0/4");
        assertThat(saves.peek(1)).isEmpty();
        SaveSlots.Loaded back = saves.load(3);
        assertThat(back.sim().level()).isEqualTo(1);
        assertThat(back.sim().easy()).isTrue();
    }

    @Test
    void anotherVersionsSaveLoadsWhenItsRulesStillFitAndIsRefusedWhenNot(@TempDir Path dir) {
        PlayerDb db = db(dir);
        GameSession s = new GameSession(GAME.make(3L, 0, false));
        play(s, 0, 300);
        slots(db, "an older version").save(s, 1);
        SaveSlots.Loaded older = slots(db, CONTENT.db().contentHash()).load(1);
        assertThat(older.ok()).as("words and menus changed, not the rules: it loads").isTrue();
        assertThat(older.sim().stateHash()).isEqualTo(s.sim().stateHash());
        QuickSaveData o = db.saveSlot(1).orElseThrow();
        db.putSaveSlot(1, new QuickSaveData(o.schemaVersion(), o.contentHash(), o.seed() + 1, o.startRoom(), o.dev(),
                o.practiced(), o.ticks(), o.input(), o.stateHash(), o.newGamePlus(), o.easy(), o.date(), o.quarters(),
                o.score(), o.room()));
        assertThat(slots(db, CONTENT.db().contentHash()).load(1).refusal()).as("its rules took it elsewhere")
                .isEqualTo("SAVE IS FROM ANOTHER VERSION");
        SaveSlots saves = slots(db, CONTENT.db().contentHash());
        saves.save(s, 1);
        QuickSaveData d = db.saveSlot(1).orElseThrow();
        db.putSaveSlot(1, new QuickSaveData(d.schemaVersion(), d.contentHash(), d.seed() + 1, d.startRoom(), d.dev(),
                d.practiced(), d.ticks(), d.input(), d.stateHash(), d.newGamePlus(), d.easy(), d.date(), d.quarters(),
                d.score(), d.room()));
        assertThat(saves.load(1).refusal()).isEqualTo("SAVE DOES NOT FIT THIS GAME");
    }

    private static Shell shell(PlayerDb db, long seed) {
        Shell shell = new Shell(CONTENT.shell(), db, List.copyOf(CONTENT.input().profiles().keySet()),
                () -> GAME.make(seed, 0, false), null, () -> "2026-10-14");
        shell.saveSlots(slots(db, CONTENT.db().contentHash()));
        return shell;
    }

    private static void press(Shell shell, InputState in, int times) {
        for (int i = 0; i < times; i++) {
            shell.tick(in, MenuInput.NONE, false);
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
    }

    private static int line(List<wulf.data.ShellConfig.Entry> items, String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id().equals(id)) {
                return i;
            }
        }
        throw new IllegalArgumentException(id);
    }

    @Test
    void pauseOpensTheMenuAndSaveAndLoadGoThroughIt(@TempDir Path dir) {
        // §17.4: P pauses with the menu; SAVE GAME, a slot, fire; then LOAD GAME, that slot, fire.
        PlayerDb db = db(dir);
        Shell shell = shell(db, 11L);
        begin(shell);   // §21.4: a new game, in the first empty slot
        for (int t = 0; t < 200; t++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        long tick = shell.session().sim().tick();
        shell.tick(PAUSE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PAUSED);
        idleIn(shell, 50);
        assertThat(shell.session().sim().tick()).as("the game stands still under the menu").isEqualTo(tick);
        List<wulf.data.ShellConfig.Entry> items = CONTENT.shell().pause().items();
        press(shell, DOWN, line(items, "save"));
        press(shell, FIRE, 1);
        assertThat(shell.state()).isEqualTo(Shell.State.SAVE_GAME);
        press(shell, DOWN, 1);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(shell.session().message(false)).isEqualTo("SAVED TO SLOT 2");
        long saved = shell.session().sim().stateHash();
        assertThat(db.saveSlot(2)).isPresent();

        for (int t = 0; t < 200; t++) {
            shell.tick(InputState.of(-1, 0, false, false), MenuInput.NONE, false);
        }
        shell.tick(PAUSE, MenuInput.NONE, false);
        press(shell, DOWN, line(items, "load"));
        press(shell, FIRE, 1);
        assertThat(shell.state()).isEqualTo(Shell.State.LOAD_GAME);
        press(shell, DOWN, 2);   // slot 1 is the game's own, slot 2 the one saved by hand
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.menuNote()).as("slot 3 is empty").isEqualTo("SLOT 3 IS EMPTY");
        assertThat(shell.state()).isEqualTo(Shell.State.LOAD_GAME);
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        press(shell, InputState.of(0, -1, false, false), 1);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(shell.session().message(false)).isEqualTo("LOADED SLOT 2");
        assertThat(shell.session().sim().stateHash()).isEqualTo(saved);
        assertThat(shell.session().sim().practice()).isFalse();
    }

    /** Fire on the title's START, then fire on the slot it offers: the first empty one (§21.4). */
    private static void begin(Shell shell) {
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.NEW_GAME);
        idleIn(shell, CONTENT.shell().screenHoldTicks());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
    }

    private static void idleIn(Shell shell, int ticks) {
        for (int i = 0; i < ticks; i++) {
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
    }

    @Test
    void escapeGoesBackAStepAndPauseOrEscapeResumes(@TempDir Path dir) {
        Shell shell = shell(db(dir), 11L);
        begin(shell);   // §21.4: a new game, in the first empty slot
        shell.tick(PAUSE, MenuInput.NONE, false);
        List<wulf.data.ShellConfig.Entry> items = CONTENT.shell().pause().items();
        press(shell, DOWN, line(items, "options"));
        press(shell, FIRE, 1);
        assertThat(shell.state()).isEqualTo(Shell.State.OPTIONS);
        assertThat(shell.menuInGame()).isTrue();
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PAUSED);
        assertThat(shell.cursor()).as("back on the line that went there").isEqualTo(line(items, "options"));
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        shell.tick(PAUSE, MenuInput.NONE, false);
        shell.tick(PAUSE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        shell.tick(PAUSE, MenuInput.NONE, false);
        press(shell, DOWN, line(items, "quit"));
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).as("quit banks the run as Escape does").isIn(Shell.State.TITLE, Shell.State.HISCORE_ENTRY);
    }

    @Test
    void theTitlesLoadPageResumesASavedGame(@TempDir Path dir) {
        PlayerDb db = db(dir);
        SaveSlots saves = slots(db, CONTENT.db().contentHash());
        GameSession s = new GameSession(GAME.make(5L, 0, false));
        play(s, 0, 400);
        saves.save(s, 1);
        Shell shell = shell(db, 99L);
        shell.tick(InputState.NONE, MenuInput.of("L"), false);
        assertThat(shell.state()).isEqualTo(Shell.State.LOAD_GAME);
        assertThat(shell.menuInGame()).isFalse();
        idleIn(shell, CONTENT.shell().screenHoldTicks());
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(shell.session().sim().runSeed()).isEqualTo(5L);
        assertThat(shell.session().sim().tick()).isEqualTo(s.sim().tick());
    }

    @Test
    void theOptionsSwitchAndAreKept(@TempDir Path dir) {
        // §17.9: each switch flips with fire and is written to the settings at once.
        PlayerDb db = db(dir);
        Shell shell = shell(db, 1L);
        shell.tick(InputState.NONE, MenuInput.of("O"), false);
        assertThat(shell.state()).isEqualTo(Shell.State.OPTIONS);
        idleIn(shell, CONTENT.shell().screenHoldTicks());
        List<wulf.data.ShellConfig.Entry> items = CONTENT.shell().options().items();
        for (String id : List.of("scanlines", "attributeClash", "inkedCreatures")) {
            boolean before = Shell.optionOn(db.settings(), id);
            while (!items.get(shell.cursor()).id().equals(id)) {
                press(shell, DOWN, 1);
            }
            press(shell, FIRE, 1);
            assertThat(Shell.optionOn(db.settings(), id)).as(id).isEqualTo(!before);
        }
        while (!items.get(shell.cursor()).id().equals("back")) {
            press(shell, DOWN, 1);
        }
        press(shell, FIRE, 1);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
    }

    @Test
    void theSoundIsOnTheOptionsTooAndHeardAtOnce(@TempDir Path dir) {
        // §17.9: from the pause menu, fire turns the sound off and on; the arrows turn the volume.
        PlayerDb db = db(dir);
        Shell shell = shell(db, 11L);
        int[] heard = {-1, -1};
        java.util.List<String> tunes = new java.util.ArrayList<>();
        shell.jukebox(new Jukebox() {
            @Override
            public void tune(String id) {
                tunes.add(String.valueOf(id));
            }

            @Override
            public void sfx(String id) {
            }

            @Override
            public void settings(boolean enabled, int volumePercent) {
                heard[0] = enabled ? 1 : 0;
                heard[1] = volumePercent;
            }
        });
        begin(shell);   // §21.4: a new game, in the first empty slot
        shell.tick(PAUSE, MenuInput.NONE, false);
        press(shell, DOWN, line(CONTENT.shell().pause().items(), "options"));
        press(shell, FIRE, 1);
        assertThat(shell.state()).isEqualTo(Shell.State.OPTIONS);
        List<wulf.data.ShellConfig.Entry> items = shell.optionLines();   // over a game: without the title's own lines
        int volume = db.settings().audio().volumePercent();
        int step = CONTENT.shell().sound().volumeStepPercent();

        press(shell, DOWN, line(items, "sound"));
        assertThat(db.settings().audio().enabled()).isTrue();
        tunes.clear();
        press(shell, FIRE, 1);
        assertThat(db.settings().audio().enabled()).isFalse();
        assertThat(heard[0]).as("the jukebox told at once").isZero();
        assertThat(tunes).as("over a game the menus stay silent").containsOnly("null");
        press(shell, FIRE, 1);
        assertThat(db.settings().audio().enabled()).isTrue();
        assertThat(heard[0]).isOne();

        press(shell, DOWN, 1);
        assertThat(items.get(shell.cursor()).id()).isEqualTo("volume");
        press(shell, InputState.of(-1, 0, false, false), 2);
        assertThat(db.settings().audio().volumePercent()).isEqualTo(volume - 2 * step);
        assertThat(heard[1]).isEqualTo(volume - 2 * step);
        press(shell, InputState.of(1, 0, false, false), 1);
        assertThat(db.settings().audio().volumePercent()).isEqualTo(volume - step);
        press(shell, FIRE, 1);
        assertThat(db.settings().audio().volumePercent()).as("fire does not turn it").isEqualTo(volume - step);
        assertThat(shell.state()).isEqualTo(Shell.State.OPTIONS);
        press(shell, InputState.of(1, 0, false, false), 20);
        assertThat(db.settings().audio().volumePercent()).as("no louder than all").isEqualTo(100);
    }

    @Test
    void eightBitModeTurnsTheOriginalsLookOnAndOffTogether(@TempDir Path dir) {
        // §17.9: one line for every switch of the original's look.
        PlayerDb db = db(dir);
        Shell shell = shell(db, 1L);
        shell.tick(InputState.NONE, MenuInput.of("O"), false);
        idleIn(shell, CONTENT.shell().screenHoldTicks());
        List<wulf.data.ShellConfig.Entry> items = CONTENT.shell().options().items();
        List<String> look = CONTENT.shell().options().eightBit();
        assertThat(look).containsExactlyInAnyOrder("scanlines", "attributeClash", "inkedCreatures", "spriteFlicker");
        assertThat(Shell.eightBitOn(db.settings(), look)).as("the shipped look is ours").isFalse();

        // One switch already on: 8-BIT MODE still turns them all on, not that one off.
        press(shell, DOWN, line(items, "inkedCreatures"));
        press(shell, FIRE, 1);
        assertThat(Shell.optionOn(db.settings(), "inkedCreatures")).isTrue();
        press(shell, InputState.of(0, -1, false, false), line(items, "inkedCreatures") - line(items, "eightBit"));
        assertThat(items.get(shell.cursor()).id()).isEqualTo("eightBit");
        press(shell, FIRE, 1);
        for (String id : look) {
            assertThat(Shell.optionOn(db.settings(), id)).as(id).isTrue();
        }
        assertThat(Shell.eightBitOn(db.settings(), look)).isTrue();
        assertThat(db.settings().audio()).as("the sound is not part of it").isEqualTo(CONTENT.defaultSettings().audio());

        press(shell, FIRE, 1);
        for (String id : look) {
            assertThat(Shell.optionOn(db.settings(), id)).as(id).isFalse();
        }
    }

    private static void writtenAt(Path dir, int slot, long millis) throws java.io.IOException {
        java.nio.file.Files.setLastModifiedTime(dir.resolve("save" + slot + ".json"),
                java.nio.file.attribute.FileTime.fromMillis(millis));
    }

    @Test
    void withAGameSavedFireOnTheTitleGoesOnWithTheNewestAndGStartsAnew(@TempDir Path dir) throws Exception {
        // §17.2: asked for by the user — a saved game is what the title offers first.
        PlayerDb db = db(dir);
        SaveSlots saves = slots(db, CONTENT.db().contentHash());
        GameSession older = new GameSession(GAME.make(5L, 0, false));
        play(older, 0, 300);
        saves.save(older, 1);
        GameSession newer = new GameSession(GAME.make(6L, 0, true));
        play(newer, 0, 450);
        saves.save(newer, 3);
        writtenAt(dir, 1, 2_000_000_000_000L);
        writtenAt(dir, 3, 1_000_000_000_000L);   // slot 1 was written last after all

        Shell shell = shell(db, 99L);
        assertThat(shell.continueSlot()).as("the newest of them").isEqualTo(1);
        assertThat(shell.continueGame().seed()).isEqualTo(5L);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(shell.session().sim().runSeed()).as("the saved game, not a new one").isEqualTo(5L);
        assertThat(shell.session().sim().stateHash()).isEqualTo(older.sim().stateHash());

        Shell again = shell(db, 99L);
        again.tick(InputState.NONE, MenuInput.of(CONTENT.shell().title().newGameKey()), false);
        assertThat(again.state()).as("G: a slot first").isEqualTo(Shell.State.NEW_GAME);
        assertThat(again.cursor()).as("the empty one").isEqualTo(1);
        idleIn(again, CONTENT.shell().screenHoldTicks());
        again.tick(FIRE, MenuInput.NONE, false);
        assertThat(again.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(again.session().sim().runSeed()).as("G: a new game").isEqualTo(99L);
        assertThat(again.session().sim().tick()).isZero();
    }

    @Test
    void withNoGameSavedFireStartsANewOne(@TempDir Path dir) {
        Shell shell = shell(db(dir), 77L);
        assertThat(shell.continueSlot()).isZero();
        assertThat(shell.continueGame()).isNull();
        begin(shell);   // §21.4: a new game, in the first empty slot
        assertThat(shell.session().sim().runSeed()).isEqualTo(77L);
    }

    @Test
    void aSavedGameThatWillNotLoadSaysSoAndFireThenStartsAnew(@TempDir Path dir) {
        PlayerDb db = db(dir);
        GameSession s = new GameSession(GAME.make(3L, 0, false));
        play(s, 0, 300);
        slots(db, CONTENT.db().contentHash()).save(s, 2);
        QuickSaveData d = db.saveSlot(2).orElseThrow();
        db.putSaveSlot(2, new QuickSaveData(d.schemaVersion(), d.contentHash(), d.seed() + 1, d.startRoom(), d.dev(),
                d.practiced(), d.ticks(), d.input(), d.stateHash(), d.newGamePlus(), d.easy(), d.date(), d.quarters(),
                d.score(), d.room()));
        Shell shell = shell(db, 99L);
        assertThat(shell.continueSlot()).isEqualTo(2);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        assertThat(shell.notice()).isEqualTo("SLOT 2: SAVE DOES NOT FIT THIS GAME");
        assertThat(shell.continueSlot()).isZero();
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        begin(shell);   // §21.4: a new game, in the first empty slot
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(shell.session().sim().runSeed()).isEqualTo(99L);
    }

    @Test
    void savingInTheGameAndQuittingOffersThatGameOnTheTitle(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = shell(db, 11L);
        assertThat(shell.continueSlot()).isZero();
        begin(shell);   // §21.4: a new game, in the first empty slot
        for (int t = 0; t < 120; t++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        shell.tick(PAUSE, MenuInput.NONE, false);
        List<wulf.data.ShellConfig.Entry> items = CONTENT.shell().pause().items();
        press(shell, DOWN, line(items, "save"));
        press(shell, FIRE, 1);
        shell.tick(FIRE, MenuInput.NONE, false);   // slot 1
        shell.tick(PAUSE, MenuInput.NONE, false);
        press(shell, DOWN, line(items, "quit"));
        shell.tick(FIRE, MenuInput.NONE, false);
        while (shell.state() == Shell.State.HISCORE_ENTRY) {
            shell.tick(FIRE, MenuInput.NONE, false);
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        assertThat(shell.continueSlot()).as("the game just saved").isEqualTo(1);
    }

    @Test
    void aNewGameInASlotWithAGameAsksAgainAndEscapeGoesBackToStart(@TempDir Path dir) {
        // §21.4: every slot full, so the page offers the first; fire there says so, a second fire replaces it.
        PlayerDb db = db(dir);
        SaveSlots saves = slots(db, CONTENT.db().contentHash());
        for (int n = 1; n <= CONTENT.shell().saves().slots(); n++) {
            GameSession s = new GameSession(GAME.make(n, 0, false));
            play(s, 0, 100);
            saves.save(s, n);
        }
        Shell shell = shell(db, 42L);
        int start = line(shell.titleLines(), "start");
        press(shell, DOWN, start);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.NEW_GAME);
        idleIn(shell, CONTENT.shell().screenHoldTicks());
        shell.tick(QUIT, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        assertThat(shell.cursor()).as("back on START").isEqualTo(start);

        shell.tick(FIRE, MenuInput.NONE, false);
        idleIn(shell, CONTENT.shell().screenHoldTicks());
        assertThat(shell.cursor()).isZero();
        press(shell, FIRE, 1);
        assertThat(shell.state()).as("slot 1 has a game: not yet").isEqualTo(Shell.State.NEW_GAME);
        assertThat(shell.menuNote()).isEqualTo("FIRE AGAIN TO REPLACE SLOT 1");
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(db.saveSlot(1).orElseThrow().seed()).as("the new game is in slot 1 at once").isEqualTo(42L);
    }

    @Test
    void theGameSavesItselfToItsSlotNowAndThenAndOnLeaving(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = new Shell(CONTENT.shell(), db, List.copyOf(CONTENT.input().profiles().keySet()), () -> {
            Simulation sim = GAME.make(11L, 0, false);
            sim.god(true);   // a minute of play with nothing ending it
            return sim;
        }, null, () -> "2026-10-18");
        shell.saveSlots(slots(db, CONTENT.db().contentHash()));
        begin(shell);
        assertThat(db.saveSlot(1).orElseThrow().ticks()).isZero();
        int every = CONTENT.shell().saves().autosaveEveryTicks();
        for (int t = 0; t < every; t++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        assertThat(db.saveSlot(1).orElseThrow().ticks()).as("saved after a while of play").isPositive();
        assertThat(shell.session().message(false)).isEqualTo(CONTENT.shell().saves().autosaved());
        for (int t = 0; t < 77; t++) {
            shell.tick(InputState.of(-1, 0, false, false), MenuInput.NONE, false);
        }
        long hash = shell.session().sim().stateHash();
        shell.tick(QUIT, MenuInput.NONE, false);
        while (shell.state() == Shell.State.HISCORE_ENTRY) {
            shell.tick(FIRE, MenuInput.NONE, false);
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
        assertThat(shell.state()).isEqualTo(Shell.State.TITLE);
        assertThat(shell.continueSlot()).as("left for the title, it is kept").isEqualTo(1);
        assertThat(db.saveSlot(1).orElseThrow().stateHash()).isEqualTo(wulf.engine.Replay.hex(hash));
    }

    @Test
    void aGameOverIsGoneFromItsSlot(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = shell(db, 11L);
        begin(shell);
        assertThat(db.saveSlot(1)).isPresent();
        Simulation sim = shell.session().sim();
        for (int i = 0; i < 100_000 && shell.state() == Shell.State.PLAYING; i++) {
            if (i % 200 == 0) {
                sim.kill();
            }
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
        assertThat(shell.state()).isEqualTo(Shell.State.GAME_OVER);
        shell.tick(QUIT, MenuInput.NONE, false);   // no continue: the run is over
        assertThat(db.saveSlot(1)).as("nothing to go on with").isEmpty();
        assertThat(shell.state()).isIn(Shell.State.TITLE, Shell.State.HISCORE_ENTRY);
    }
}
