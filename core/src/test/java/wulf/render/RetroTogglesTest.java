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

/**
 * AGENTS.md §5.4 — the optional retro toggles: scanlines, attribute clash, sprite flicker.
 * How the window draws scanlines and glow is the desktop's {@code ScalerTest}.
 */
class RetroTogglesTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final DisplayConfig DISPLAY = DB.load("config/display", DisplayConfig.class);

    // ------------------------------------------------------------------ the shipped toggles

    @Test
    void theShippedDefaultsLeaveEveryToggleOff() {
        assertThat(DISPLAY.crt().scanlines()).isFalse();
        assertThat(DISPLAY.crt().attributeClash()).isFalse();
        assertThat(DISPLAY.crt().glow()).isFalse();
        assertThat(DISPLAY.crt().glowPercent()).isEqualTo(60);
        assertThat(DISPLAY.spriteFlicker()).isFalse();
        assertThat(DISPLAY.crt().scanlineLuminancePercent()).isEqualTo(82);
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

    private static Framebuffer blank() {
        Framebuffer fb = new Framebuffer(DISPLAY.canvas().w(), DISPLAY.canvas().h());
        fb.clear(BLACK);
        return fb;
    }

    @Test
    void aCellOfFoliageIsOnePaperWithBlackDetail() {
        // The room data's attributes: paper the foliage's colour, ink black ($60 = bright green paper).
        DisplayConfig.Playfield f = DISPLAY.playfield();
        Framebuffer fb = blank();
        fb.fillRect(f.x(), f.y(), 8, 5, GREEN);      // mostly green
        fb.set(f.x() + 2, f.y() + 1, YELLOW);        // a yellow fleck in it
        AttributeClash clash = new AttributeClash(f, BLACK);
        clash.scenery(fb);
        clash.apply(fb);

        assertThat(fb.get(f.x(), f.y())).as("the paper is the cell's most colour").isEqualTo(GREEN);
        assertThat(fb.get(f.x() + 2, f.y() + 1)).as("another colour is black detail").isEqualTo(BLACK);
        assertThat(fb.get(f.x(), f.y() + 7)).isEqualTo(BLACK);
    }

    @Test
    void inTheOpenACellTakesTheSpritesColourButFoliageKeepsItsOwn() {
        // $BB45: a sprite's colour goes only into cells of black paper; over foliage it is a black shape.
        DisplayConfig.Playfield f = DISPLAY.playfield();
        Framebuffer fb = blank();
        fb.fillRect(f.x(), f.y(), 8, 8, GREEN);      // a cell of foliage, and the open cell beside it
        AttributeClash clash = new AttributeClash(f, BLACK);
        clash.scenery(fb);
        fb.fillRect(f.x() + 6, f.y() + 2, 4, 2, YELLOW);   // Vale, across both cells
        fb.set(f.x() + 11, f.y() + 2, BLACK);               // and his outline, which the original has not got
        clash.apply(fb);

        assertThat(fb.get(f.x() + 6, f.y() + 2)).as("over foliage, a black shape").isEqualTo(BLACK);
        assertThat(fb.get(f.x() + 5, f.y() + 2)).as("the foliage keeps its colour").isEqualTo(GREEN);
        assertThat(fb.get(f.x() + 8, f.y() + 2)).as("in the open, his colour").isEqualTo(YELLOW);
        assertThat(fb.get(f.x() + 12, f.y() + 5)).isEqualTo(BLACK);
    }

    @Test
    void aSpriteOverBlackDetailShowsThePaper() {
        // $BA14 XORs: a sprite pixel on the foliage's black ink lets its paper through.
        DisplayConfig.Playfield f = DISPLAY.playfield();
        Framebuffer fb = blank();
        fb.fillRect(f.x(), f.y(), 8, 6, GREEN);      // the bottom two rows of the cell stay black detail
        AttributeClash clash = new AttributeClash(f, BLACK);
        clash.scenery(fb);
        fb.set(f.x() + 1, f.y() + 7, YELLOW);
        clash.apply(fb);
        assertThat(fb.get(f.x() + 1, f.y() + 7)).isEqualTo(GREEN);
        assertThat(fb.get(f.x() + 2, f.y() + 7)).isEqualTo(BLACK);
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
                }
            }
        }
        // Two colours a cell, one of them black: the Spectrum's attribute.
        for (int cy = 0; cy < f.rows(); cy++) {
            for (int cx = 0; cx < f.cols(); cx++) {
                java.util.Set<Integer> colours = new java.util.HashSet<>();
                for (int y = 0; y < f.cell(); y++) {
                    for (int x = 0; x < f.cell(); x++) {
                        colours.add(clashed.get(f.x() + cx * f.cell() + x, f.y() + cy * f.cell() + y));
                    }
                }
                colours.remove(BLACK);
                assertThat(colours).as("cell (%d,%d)", cx, cy).hasSizeLessThanOrEqualTo(1);
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
        return Simulation.at(RULES, OPEN, 0, Fixed.fp(ROOM.col() * 256 + 20), Fixed.fp(ROOM.row() * Simulation.ROOM_H_PX + 164),
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
