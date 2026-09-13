package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.data.CreatureData;
import wulf.data.DisplayConfig;
import wulf.data.JsonDb;
import wulf.data.Palette;
import wulf.data.PlayerData;
import wulf.data.SpriteRepository;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.input.InputState;
import wulf.sim.Creature;
import wulf.sim.Ecosystem;
import wulf.sim.Herd;
import wulf.sim.RoomPopulator;
import wulf.sim.Simulation;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §12, §5.3 — how creatures are drawn. */
class CreaturePainterTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final PlayerData RULES = DB.load("entities/player", PlayerData.class);
    private static final CreatureData CREATURES = DB.load("entities/creatures", CreatureData.class);
    private static final CollisionWorld OPEN =
            (gx, gy) -> gx < 0 || gy < 0 || gx >= WorldGrid.COLS || gy >= WorldGrid.ROWS;
    private static final RoomAddress ROOM = new RoomAddress(3, 5);
    private static final int TPF = CREATURES.walkTicksPerFrame();

    /** The roster with one species held still. */
    private static CreatureData still(String id) {
        List<CreatureData.Species> roster = new ArrayList<>();
        for (CreatureData.Species s : CREATURES.creatures()) {
            roster.add(!s.id().equals(id) ? s : new CreatureData.Species(s.id(), s.displayName(), s.sprite(), s.size(),
                    s.collisionBox(), new CreatureData.Speed(0, 0), s.hp(), s.score(), s.ignoresScenery(), s.behaviour()));
        }
        return new CreatureData(CREATURES.schemaVersion(), CREATURES.fidelity(), CREATURES.diagonalScaleFp(),
                CREATURES.puffTicks(), CREATURES.hurtFlashTicks(), CREATURES.walkTicksPerFrame(), roster,
                CREATURES.projectiles());
    }

    private static Simulation with(CreatureData roster, int playerX, int playerY, String id, int x, int y) {
        RoomPopulator one = (spawner, room, seed, visit) -> {
            if (room.equals(ROOM)) {
                spawner.spawn(id, x, y, Herd.NONE, new Rng(3));
            }
        };
        return Simulation.at(RULES, OPEN, 0, Fixed.fp(ROOM.col() * 256 + playerX), Fixed.fp(ROOM.row() * 192 + playerY),
                new Ecosystem(roster, one, 0), 5L);
    }

    private static Creature only(Simulation s) {
        assertThat(s.creatures()).hasSize(1);
        return s.creatures().get(0);
    }

    @Test
    void aStillCreatureShowsItsFirstFrameFacingItsWay() {
        Simulation s = with(still("tribesman"), 20, 180, "tribesman", 128, 96);
        Creature c = only(s);
        c.direction(1, 0);
        assertThat(CreaturePainter.frameFor(c, 0, TPF)).isEqualTo("walk0");
        c.direction(-1, 0);
        assertThat(CreaturePainter.frameFor(c, 99, TPF)).isEqualTo("walk0_l");
    }

    @Test
    void aWalkingCreatureAlternatesItsTwoFrames() {
        Simulation s = with(CREATURES, 20, 180, "tribesman", 128, 96);
        Creature c = only(s);
        List<String> frames = new ArrayList<>();
        for (int t = 0; t < 4 * TPF; t++) {
            s.tick(InputState.NONE);
            frames.add(CreaturePainter.frameFor(c, s.tick(), TPF).replace("_l", ""));
        }
        assertThat(frames).contains("walk0", "walk1");
        assertThat(frames.subList(0, TPF - 1)).containsOnly("walk0");
    }

    @Test
    void fliersFlapEvenWhenStill() {
        Simulation s = with(still("bat"), 20, 180, "bat", 128, 96);
        Creature c = only(s);
        assertThat(CreaturePainter.frameFor(c, 0, TPF)).startsWith("walk0");
        assertThat(CreaturePainter.frameFor(c, TPF, TPF)).startsWith("walk1");
    }

    @Test
    void aHurtCreatureFlashesOnAlternateTicks() {
        Simulation s = with(still("rhino"), 128, 100, "rhino", 128, 114);
        Creature c = only(s);
        s.tick(InputState.of(0, 0, true, true));
        for (int i = 0; i < RULES.sabre().windupTicks(); i++) {
            s.tick(InputState.NONE);
        }
        assertThat(c.hurtTicks()).isPositive();
        boolean first = CreaturePainter.flashing(c);
        s.tick(InputState.NONE);
        assertThat(CreaturePainter.flashing(c)).isNotEqualTo(first);
    }

    @Test
    void aDyingCreatureShowsBothPuffFrames() {
        Simulation s = with(still("tribesman"), 128, 100, "tribesman", 128, 110);
        Creature c = only(s);
        s.tick(InputState.of(0, 0, true, true));
        for (int i = 0; i < RULES.sabre().windupTicks(); i++) {
            s.tick(InputState.NONE);
        }
        assertThat(c.alive()).isFalse();
        assertThat(CreaturePainter.puffFrame(c, CREATURES.puffTicks())).isEqualTo("f0");
        for (int i = 0; i < CREATURES.puffTicks() / 2; i++) {
            s.tick(InputState.NONE);
        }
        assertThat(CreaturePainter.puffFrame(c, CREATURES.puffTicks())).isEqualTo("f1");
    }

    @Test
    void paintingStaysInsideThePlayfield() {
        DisplayConfig display = DB.load("config/display", DisplayConfig.class);
        Palette palette = DB.load("art/palette", Palette.class);
        CreaturePainter painter = new CreaturePainter(display, new SpriteBank(new SpriteRepository(DB)), CREATURES, palette);
        DisplayConfig.Playfield f = display.playfield();
        // A hippo against the room's west edge: its sprite overhangs the box it collides with.
        Simulation s = with(still("hippo"), 200, 180, "hippo", 14, 100);
        Framebuffer fb = new Framebuffer(display.canvas().w(), display.canvas().h());
        fb.clear(3);
        painter.paint(fb, s);
        int drawn = 0;
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                boolean inField = x >= f.x() && y >= f.y() && x < f.x() + f.w() && y < f.y() + f.h();
                if (!inField) {
                    assertThat(fb.get(x, y)).as("pixel %d,%d outside the playfield", x, y).isEqualTo(3);
                } else if (fb.get(x, y) != 3) {
                    drawn++;
                }
            }
        }
        assertThat(drawn).isPositive();
    }
}
