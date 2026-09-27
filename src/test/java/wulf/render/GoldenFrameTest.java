package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wulf.Content;
import wulf.data.JsonDb;
import wulf.data.PlayerDb;
import wulf.data.ShellConfig;
import wulf.input.InputState;
import wulf.input.MenuInput;
import wulf.sim.Simulation;
import wulf.ui.GamePainter;
import wulf.ui.Shell;
import wulf.ui.ShellPainter;
import wulf.world.WorldGrid;

/**
 * AGENTS.md §22.5 — the same seed and the same input must draw the same pixels.
 *
 * <p>A frame is hashed whole, so this notices a sprite that moved by one pixel, a colour that
 * changed, a panel that lost a digit and a menu that reflowed — the drift no amount of looking at
 * screenshots reliably catches.
 *
 * <p>The hashes are <b>not</b> a specification of what is correct. When a change here is
 * deliberate, regenerate them with {@code -Dgolden.update=true} and <b>say in the commit message
 * what changed and why</b>. A hash that moved for a reason nobody can name is a bug, not a golden
 * file that needs updating.
 */
class GoldenFrameTest {

    /** §22.5: one frame early, one after a room has settled, one mid-play, one much later. */
    private static final int[] TICKS = {1, 50, 200, 1000};
    private static final long SEED = 20260920L;

    private static final Path GOLDEN = Path.of("src", "test", "resources", "golden");
    private static final boolean UPDATE = Boolean.getBoolean("golden.update");

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static Content content;
    private static Fonts fonts;

    @BeforeAll
    static void load() {
        content = Content.load(DB);
        fonts = new Fonts("art/font/font.json", content.font());
    }

    /**
     * A fixed script: walk a square, swing on a prime-ish beat. It has to be the same every run
     * and busy enough to move creatures, the sabre and the panel through their states.
     */
    private static InputState script(int t) {
        int phase = (t / 37) % 4;
        int dx = phase == 0 ? 1 : phase == 2 ? -1 : 0;
        int dy = phase == 1 ? 1 : phase == 3 ? -1 : 0;
        boolean fire = t % 23 == 0;
        return InputState.of(dx, dy, fire, fire);
    }

    @Test
    void theJungleDrawsTheSamePixelsItAlwaysHas() {
        GamePainter painter = new GamePainter(content, fonts);
        Framebuffer fb = new Framebuffer(content.display().canvas().w(), content.display().canvas().h());
        Simulation sim = Simulation.startingIn(content.player(), new WorldGrid(content.rooms()),
                content.game().transition().freezeTicks(), content.map().startRoom(), content.ecosystem(), SEED);

        Map<String, String> frames = new LinkedHashMap<>();
        int last = TICKS[TICKS.length - 1];
        for (int t = 1; t <= last; t++) {
            sim.play(script(t), false);
            if (contains(TICKS, t)) {
                painter.paint(fb, sim, 0, null, false);
                frames.put("tick " + t, fb.hash());
            }
        }

        assertThat(frames).hasSize(TICKS.length);
        assertThat(List.copyOf(frames.values())).as("every frame differs from the others")
                .doesNotHaveDuplicates();
        check("play.hash", frames);
    }

    @Test
    void everyShellScreenDrawsTheSamePixelsItAlwaysHas(@TempDir Path userDir) {
        PlayerDb db = new PlayerDb(userDir, DB, content.defaultScores(), content.defaultSettings());
        ShellPainter painter = new ShellPainter(content, content.shell(), db, fonts);
        Framebuffer fb = new Framebuffer(content.display().canvas().w(), content.display().canvas().h());

        Map<String, String> frames = new LinkedHashMap<>();
        frames.put("title", paint(painter, fb, shell(db), MenuInput.NONE, 0));
        // The wordmark's colours walk down the rows, so a later tick is a different picture.
        frames.put("title cycled", paint(painter, fb, shell(db), MenuInput.NONE,
                content.shell().title().cycle().ticksPerStep()));
        for (ShellConfig.MenuItem item : content.shell().title().menu()) {
            frames.put(item.screen().toLowerCase(java.util.Locale.ROOT),
                    paint(painter, fb, shell(db), MenuInput.of(item.key()), 0));
        }

        assertThat(frames).hasSizeGreaterThanOrEqualTo(3);
        assertThat(List.copyOf(frames.values())).as("every screen differs from the others")
                .doesNotHaveDuplicates();
        check("shell.hash", frames);
    }

    // ---------------------------------------------------------------- plumbing

    private Shell shell(PlayerDb db) {
        return new Shell(content.shell(), db, List.copyOf(content.input().profiles().keySet()),
                () -> Simulation.startingIn(content.player(), new WorldGrid(content.rooms()),
                        content.game().transition().freezeTicks(), content.map().startRoom(),
                        content.ecosystem(), SEED),
                null, () -> "2026-09-20");
    }

    /** Opens one screen, lets it sit for {@code settle} ticks, and hashes what it draws. */
    private static String paint(ShellPainter painter, Framebuffer fb, Shell shell, MenuInput keys, int settle) {
        shell.tick(InputState.NONE, keys, false);
        for (int i = 0; i < settle; i++) {
            shell.tick(InputState.NONE, MenuInput.NONE, false);
        }
        fb.clear(0);
        painter.paint(fb, shell, false);
        return fb.hash();
    }

    private static boolean contains(int[] values, int wanted) {
        for (int value : values) {
            if (value == wanted) {
                return true;
            }
        }
        return false;
    }

    /** Compares against the stored frames, or rewrites them under {@code -Dgolden.update=true}. */
    private static void check(String name, Map<String, String> frames) {
        Path file = GOLDEN.resolve(name);
        if (UPDATE) {
            write(file, frames);
            System.out.println("golden: rewrote " + file + " (" + frames.size() + " frames)");
            return;
        }
        assertThat(file).as("run with -Dgolden.update=true to create it").exists();
        assertThat(read(file))
                .as("%s drifted. If you meant it, rerun with -Dgolden.update=true and say why in the "
                        + "commit message (§22.5)", name)
                .containsExactlyInAnyOrderEntriesOf(frames);
    }

    private static void write(String name, Map<String, String> frames) {
        write(GOLDEN.resolve(name), frames);
    }

    private static void write(Path file, Map<String, String> frames) {
        StringBuilder out = new StringBuilder();
        out.append("# ").append(file.getFileName()).append(" — AGENTS.md §22.5\n");
        out.append("# Framebuffer hashes. Regenerate with -Dgolden.update=true, and say why.\n");
        frames.forEach((key, hash) -> out.append(key).append(" = ").append(hash).append('\n'));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, out.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    private static Map<String, String> read(Path file) {
        Map<String, String> frames = new LinkedHashMap<>();
        List<String> lines;
        try {
            lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
        for (String line : lines) {
            String text = line.strip();
            if (text.isEmpty() || text.startsWith("#")) {
                continue;
            }
            int equals = text.lastIndexOf('=');
            frames.put(text.substring(0, equals).strip(), text.substring(equals + 1).strip());
        }
        return frames;
    }
}
