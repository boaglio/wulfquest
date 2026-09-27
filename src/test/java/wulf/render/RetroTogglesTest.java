package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.CreatureData;
import wulf.data.DisplayConfig;
import wulf.data.JsonDb;
import wulf.data.Palette;
import wulf.data.PlayerData;
import wulf.data.Settings;
import wulf.data.SpriteRepository;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.sim.Ecosystem;
import wulf.sim.Herd;
import wulf.sim.RoomPopulator;
import wulf.sim.Simulation;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §5.4 — the optional retro toggles: scanlines, attribute clash, sprite flicker. */
class RetroTogglesTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final DisplayConfig DISPLAY = DB.load("config/display", DisplayConfig.class);

    private static int[] greyRamp() {
        int[] argb = new int[16];
        for (int i = 0; i < 16; i++) {
            argb[i] = 0xFF000000 | (i * 16) << 16 | (i * 16) << 8 | (i * 16);
        }
        argb[15] = 0xFFFFFFFF;
        return argb;
    }

    // ------------------------------------------------------------------ scanlines

    @Test
    void theShippedDefaultsLeaveEveryToggleOff() {
        assertThat(DISPLAY.crt().scanlines()).isFalse();
        assertThat(DISPLAY.crt().attributeClash()).isFalse();
        assertThat(DISPLAY.crt().glow()).isFalse();
        assertThat(DISPLAY.spriteFlicker()).isFalse();
        assertThat(DISPLAY.crt().scanlineLuminancePercent()).isEqualTo(82);
    }

    @Test
    void scanlinesDarkenEveryOddOutputRowAndNothingElse() {
        Framebuffer fb = new Framebuffer(2, 2);
        fb.clear(15);
        Scaler scaler = new Scaler(greyRamp(), 2, 2, 3);
        scaler.setScanlines(true, 82);
        var img = scaler.render(fb);
        int dim = Scaler.dim(0xFFFFFFFF, 82);
        assertThat(dim).isEqualTo(0xFFD1D1D1);   // 255 * 82 / 100 = 209
        for (int y = 0; y < 6; y++) {
            for (int x = 0; x < 6; x++) {
                assertThat(img.getRGB(x, y)).as("(%d,%d)", x, y).isEqualTo(y % 2 == 1 ? dim : 0xFFFFFFFF);
            }
        }
    }

    @Test
    void scanlinesAreOneOutputRowTallAtScaleOneToo() {
        Framebuffer fb = new Framebuffer(1, 4);
        fb.clear(15);
        Scaler scaler = new Scaler(greyRamp(), 1, 4, 1);
        scaler.setScanlines(true, 50);
        var img = scaler.render(fb);
        assertThat(img.getRGB(0, 0)).isEqualTo(0xFFFFFFFF);
        assertThat(img.getRGB(0, 1)).isEqualTo(0xFF7F7F7F);
        assertThat(img.getRGB(0, 2)).isEqualTo(0xFFFFFFFF);
        assertThat(img.getRGB(0, 3)).isEqualTo(0xFF7F7F7F);
    }

    @Test
    void turningScanlinesOffRestoresTheCleanPicture() {
        Framebuffer fb = new Framebuffer(2, 2);
        fb.clear(15);
        Scaler scaler = new Scaler(greyRamp(), 2, 2, 2);
        scaler.setScanlines(true, 82);
        scaler.render(fb);
        scaler.setScanlines(false, 82);
        assertThat(scaler.render(fb).getRGB(0, 1)).isEqualTo(0xFFFFFFFF);
    }

    @Test
    void thePlayersChoiceOverridesTheShippedToggleButKeepsTheShippedDepth() {
        DisplayConfig chosen = DISPLAY.withCrt(new Settings.Crt(true, false, true));
        assertThat(chosen.crt().scanlines()).isTrue();
        assertThat(chosen.crt().attributeClash()).isTrue();
        assertThat(chosen.crt().scanlineLuminancePercent()).isEqualTo(82);
        assertThat(chosen.playfield()).isEqualTo(DISPLAY.playfield());

        Content c = Content.load(DB);
        Settings settings = c.defaultSettings();
        Settings withScanlines = new Settings(settings.schemaVersion(), settings.scale(), settings.audio(),
                settings.inputProfile(), new Settings.Crt(true, false, false), settings.features());
        assertThat(c.withSettings(withScanlines).display().crt().scanlines()).isTrue();
        assertThat(c.withSettings(settings).display().crt().scanlines()).isFalse();
    }

    // ------------------------------------------------------------------ attribute clash

    private static final int BLACK = 0;
    private static final int GREEN = 4;
    private static final int YELLOW = 14;

    private static Framebuffer clashing() {
        Framebuffer fb = new Framebuffer(DISPLAY.canvas().w(), DISPLAY.canvas().h());
        fb.trackInk(BLACK);
        fb.clear(BLACK);
        return fb;
    }

    @Test
    void theLastInkDrawnIntoACellTakesTheWholeCell() {
        DisplayConfig.Playfield f = DISPLAY.playfield();
        Framebuffer fb = clashing();
        fb.fillRect(f.x(), f.y(), 8, 4, GREEN);      // foliage over the top half of the first cell
        fb.set(f.x() + 3, f.y() + 6, YELLOW);        // then one pixel of Vale, lower down
        new AttributeClash(f).apply(fb);

        assertThat(fb.get(f.x(), f.y())).as("the foliage took Vale's colour").isEqualTo(YELLOW);
        assertThat(fb.get(f.x() + 7, f.y() + 3)).isEqualTo(YELLOW);
        assertThat(fb.get(f.x() + 3, f.y() + 6)).isEqualTo(YELLOW);
        assertThat(fb.get(f.x(), f.y() + 7)).as("paper stays paper").isEqualTo(BLACK);
    }

    @Test
    void aCellWithOneInkIsUntouchedAndItsNeighbourIsNotDraggedIn() {
        DisplayConfig.Playfield f = DISPLAY.playfield();
        Framebuffer fb = clashing();
        fb.fillRect(f.x(), f.y(), 16, 8, GREEN);
        fb.set(f.x() + 9, f.y() + 1, YELLOW);        // second cell only
        new AttributeClash(f).apply(fb);

        assertThat(fb.get(f.x() + 7, f.y() + 7)).isEqualTo(GREEN);
        assertThat(fb.get(f.x() + 8, f.y())).isEqualTo(YELLOW);
    }

    @Test
    void brightBlackIsPaperTooAndClearingForgetsTheInk() {
        DisplayConfig.Playfield f = DISPLAY.playfield();
        Framebuffer fb = clashing();
        fb.set(f.x(), f.y(), 8);
        assertThat(fb.ink(f.x() >> 3, f.y() >> 3)).isEqualTo(-1);
        fb.set(f.x(), f.y(), GREEN);
        assertThat(fb.ink(f.x() >> 3, f.y() >> 3)).isEqualTo(GREEN);
        fb.clear(BLACK);
        assertThat(fb.ink(f.x() >> 3, f.y() >> 3)).isEqualTo(-1);
    }

    @Test
    void anUntrackedFramebufferRemembersNothing() {
        Framebuffer fb = new Framebuffer(16, 16);
        fb.set(1, 1, GREEN);
        assertThat(fb.tracksInk()).isFalse();
        assertThat(fb.ink(0, 0)).isEqualTo(-1);
    }

    @Test
    void aRealFrameClashesOnlyInsideThePlayfieldAndOnlyWhenAskedTo() {
        Content base = Content.load(DB);
        Fonts fonts = new Fonts("art/font/font.json", base.font());
        Settings d = base.defaultSettings();
        Content clash = base.withSettings(new Settings(d.schemaVersion(), d.scale(), d.audio(), d.inputProfile(),
                new Settings.Crt(false, false, true), d.features()));
        Simulation sim = Simulation.startingIn(base.player(), new WorldGrid(base.rooms()),
                base.game().transition().freezeTicks(), base.map().startRoom(), base.ecosystem(), 7L);
        for (int t = 0; t < 120; t++) {
            sim.play(wulf.input.InputState.of(t % 60 < 30 ? 1 : 0, 0, false, false), false);
        }
        Framebuffer plain = new Framebuffer(DISPLAY.canvas().w(), DISPLAY.canvas().h());
        Framebuffer clashed = new Framebuffer(DISPLAY.canvas().w(), DISPLAY.canvas().h());
        new wulf.ui.GamePainter(base, fonts).paint(plain, sim, 0, null, false);
        new wulf.ui.GamePainter(clash, fonts).paint(clashed, sim, 0, null, false);

        assertThat(clashed.hash()).isNotEqualTo(plain.hash());
        DisplayConfig.Playfield f = DISPLAY.playfield();
        for (int y = 0; y < DISPLAY.canvas().h(); y++) {
            for (int x = 0; x < DISPLAY.canvas().w(); x++) {
                boolean inField = x >= f.x() && x < f.x() + f.w() && y >= f.y() && y < f.y() + f.h();
                if (!inField) {
                    assertThat(clashed.get(x, y)).as("(%d,%d) outside the playfield", x, y).isEqualTo(plain.get(x, y));
                } else if (plain.get(x, y) == BLACK) {
                    assertThat(clashed.get(x, y)).as("(%d,%d) paper", x, y).isEqualTo(BLACK);
                }
            }
        }
    }

    // ------------------------------------------------------------------ sprite flicker

    private static final PlayerData RULES = DB.load("entities/player", PlayerData.class);
    private static final CreatureData CREATURES = DB.load("entities/creatures", CreatureData.class);
    private static final CollisionWorld OPEN =
            (gx, gy) -> gx < 0 || gy < 0 || gx >= WorldGrid.COLS || gy >= WorldGrid.ROWS;
    private static final RoomAddress ROOM = new RoomAddress(3, 5);

    /** {@code count} of one species a few pixels apart, sharing cells, and Vale well away from them. */
    private static Simulation pile(int count) {
        RoomPopulator stack = (spawner, room, seed, visit) -> {
            if (room.equals(ROOM)) {
                for (int i = 0; i < count; i++) {
                    spawner.spawn("tribesman", 124 + 4 * i, 96, Herd.NONE, new Rng(3 + i));
                }
            }
        };
        return Simulation.at(RULES, OPEN, 0, Fixed.fp(ROOM.col() * 256 + 20), Fixed.fp(ROOM.row() * 192 + 180),
                new Ecosystem(CREATURES, stack, 0), 5L);
    }

    private static DisplayConfig flickering(boolean on) {
        DisplayConfig d = DISPLAY;
        return new DisplayConfig(d.schemaVersion(), d.canvas(), d.playfield(), d.panel(), d.scale(), d.border(),
                d.crt(), on);
    }

    private static String frame(DisplayConfig display, Simulation sim) {
        Palette palette = DB.load("art/palette", Palette.class);
        CreaturePainter painter = new CreaturePainter(display, new SpriteBank(new SpriteRepository(DB)), CREATURES,
                palette);
        Framebuffer fb = new Framebuffer(display.canvas().w(), display.canvas().h());
        fb.clear(0);
        painter.paint(fb, sim);
        return fb.hash();
    }

    @Test
    void aCrowdOfThreeTakesTurnsTickByTick() {
        Simulation sim = pile(3);
        assertThat(sim.creatures()).hasSize(3);
        String steady = frame(flickering(false), sim);
        String even = frame(flickering(true), sim);
        assertThat(even).as("some of the crowd sits this tick out").isNotEqualTo(steady);
    }

    @Test
    void twoSpritesSharingACellNeverFlicker() {
        Simulation sim = pile(2);
        assertThat(sim.creatures()).hasSize(2);
        assertThat(frame(flickering(true), sim)).isEqualTo(frame(flickering(false), sim));
    }
}
