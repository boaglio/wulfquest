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
    void anotherVersionsSaveIsRefusedAndSoIsOneThatDoesNotFit(@TempDir Path dir) {
        PlayerDb db = db(dir);
        GameSession s = new GameSession(GAME.make(3L, 0, false));
        play(s, 0, 300);
        slots(db, "an older version").save(s, 1);
        assertThat(slots(db, CONTENT.db().contentHash()).load(1).refusal()).isEqualTo("SAVE IS FROM ANOTHER VERSION");
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
        shell.tick(FIRE, MenuInput.NONE, false);
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
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.menuNote()).as("slot 1 is empty").isEqualTo("SLOT 1 IS EMPTY");
        assertThat(shell.state()).isEqualTo(Shell.State.LOAD_GAME);
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        press(shell, DOWN, 1);
        shell.tick(FIRE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.PLAYING);
        assertThat(shell.session().message(false)).isEqualTo("LOADED SLOT 2");
        assertThat(shell.session().sim().stateHash()).isEqualTo(saved);
        assertThat(shell.session().sim().practice()).isFalse();
    }

    private static void idleIn(Shell shell, int ticks) {
        for (int i = 0; i < ticks; i++) {
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
    }

    @Test
    void escapeGoesBackAStepAndPauseOrEscapeResumes(@TempDir Path dir) {
        Shell shell = shell(db(dir), 11L);
        shell.tick(FIRE, MenuInput.NONE, false);
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
}
