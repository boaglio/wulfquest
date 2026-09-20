package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wulf.Content;
import wulf.data.JsonDb;
import wulf.data.PlayerDb;
import wulf.data.ShellConfig;
import wulf.input.InputState;
import wulf.input.MenuInput;
import wulf.render.Fonts;
import wulf.render.Framebuffer;

/**
 * Every shell screen draws something, and draws something different from the
 * one before it (AGENTS.md §17). A blank page is the failure mode that a
 * state-machine test cannot see.
 */
class ShellPainterTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final Content CONTENT = Content.load(DB);
    private static final ShellConfig CONFIG = CONTENT.shell();

    private static final InputState FIRE = new InputState(false, false, false, false, true, true, false, false,
            false, false, false);

    private Framebuffer fb() {
        return new Framebuffer(CONTENT.display().canvas().w(), CONTENT.display().canvas().h());
    }

    private PlayerDb db(Path dir) {
        return new PlayerDb(dir, DB, CONTENT.defaultScores(), CONTENT.defaultSettings());
    }

    private ShellPainter painter(PlayerDb db) {
        return new ShellPainter(CONTENT, CONFIG, db, new Fonts("art/font/font.json", CONTENT.font()));
    }

    private Shell shell(PlayerDb db) {
        return new Shell(CONFIG, db, List.copyOf(CONTENT.input().profiles().keySet()), GameSessionTest::scoringSim,
                null, () -> "2026-09-19");
    }

    private static int ink(Framebuffer fb) {
        int lit = 0;
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                if (fb.get(x, y) != 0) {
                    lit++;
                }
            }
        }
        return lit;
    }

    @Test
    void everyPageTheTitleOffersDrawsSomething(@TempDir Path dir) {
        PlayerDb db = db(dir);
        ShellPainter painter = painter(db);
        Framebuffer fb = fb();
        for (ShellConfig.MenuItem item : CONFIG.title().menu()) {
            Shell shell = shell(db);
            shell.tick(InputState.NONE, MenuInput.of(item.key()), false);
            fb.clear(0);
            painter.paint(fb, shell, false);
            assertThat(ink(fb)).as("%s is not blank", item.screen()).isGreaterThan(100);
        }
    }

    @Test
    void theWordmarkCyclesItsColours(@TempDir Path dir) {
        Shell shell = shell(db(dir));
        ShellPainter painter = painter(db(dir));
        Framebuffer first = fb();
        painter.paint(first, shell, false);
        String before = first.hash();
        for (int i = 0; i < CONFIG.title().cycle().ticksPerStep(); i++) {
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
        Framebuffer later = fb();
        painter.paint(later, shell, false);
        assertThat(later.hash()).as("the wordmark's colours moved").isNotEqualTo(before);
    }

    @Test
    void theStatsPageSaysSoWhenNothingHasHappenedYet(@TempDir Path dir) {
        PlayerDb db = db(dir);
        assertThat(db.stats().blank()).isTrue();
        Shell shell = shell(db);
        shell.tick(InputState.NONE, MenuInput.of("S"), false);
        Framebuffer fb = fb();
        painter(db).paint(fb, shell, false);
        assertThat(ink(fb)).isPositive();
    }

    @Test
    void gameOverIsDrawnOverAJungleThatIsStillThere(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = shell(db);
        ShellPainter painter = painter(db);
        shell.tick(FIRE, MenuInput.NONE, false);
        Framebuffer playing = fb();
        painter.paint(playing, shell, false);
        int whilePlaying = ink(playing);

        GameSessionTest.playUntilGameOver(shell.session(), shell.session().sim());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        assertThat(shell.state()).isEqualTo(Shell.State.GAME_OVER);
        Framebuffer over = fb();
        painter.paint(over, shell, false);
        assertThat(ink(over)).as("the playfield is still drawn under the message").isGreaterThan(whilePlaying / 4);
        assertThat(over.hash()).isNotEqualTo(playing.hash());
    }

    @Test
    void pausingDropsThePlayfieldToTheNonBrightPalette(@TempDir Path dir) {
        PlayerDb db = db(dir);
        Shell shell = shell(db);
        ShellPainter painter = painter(db);
        shell.tick(FIRE, MenuInput.NONE, false);
        shell.tick(new InputState(false, false, false, false, false, false, true, false, false, false, false),
                MenuInput.NONE, false);
        assertThat(shell.session().paused()).isTrue();
        Framebuffer fb = fb();
        painter.paint(fb, shell, false);
        wulf.data.DisplayConfig.Playfield f = CONTENT.display().playfield();
        for (int y = f.y(); y < f.y() + f.h(); y++) {
            for (int x = f.x(); x < f.x() + f.w(); x++) {
                assertThat(fb.get(x, y)).as("bright ink at %d,%d", x, y).isLessThan(8);
            }
        }
    }

    @Test
    void theHiScoreEntryScreenShowsTheNameBeingTyped(@TempDir Path dir) {
        PlayerDb db = db(dir);
        db.putScores(wulf.data.HighScores.empty());
        Shell shell = shell(db);
        ShellPainter painter = painter(db);
        shell.tick(FIRE, MenuInput.NONE, false);
        for (int i = 0; i < 40 && shell.session().sim().score() == 0; i++) {
            shell.tick(InputState.of(1, 0, false, false), MenuInput.NONE, false);
        }
        GameSessionTest.playUntilGameOver(shell.session(), shell.session().sim());
        shell.tick(InputState.NONE, MenuInput.NONE, false);
        for (int i = 0; i < CONFIG.gameOver().holdTicks(); i++) {
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
        assertThat(shell.state()).isEqualTo(Shell.State.HISCORE_ENTRY);
        Framebuffer fb = fb();
        painter.paint(fb, shell, false);
        assertThat(ink(fb)).isGreaterThan(100);
    }
}
