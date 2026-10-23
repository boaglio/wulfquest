package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.DisplayConfig;
import wulf.render.Framebuffer;
import wulf.render.Sprite;
import wulf.render.SpriteBank;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §17.7 — the map shows the rooms Vale has been in, and nothing else, until the scroll is found. */
class MapScreenTest {

    /** Set 3 (§14.2): the rooms these tests look at. */
    private static final Content C = Content.load(Path.of("data")).withAmuletSet(3);
    private static final Sprite ARCH = new SpriteBank(C.sprites()).get(C.shell().map().exitIcon().sprite());
    private static final Sprite ICON = new SpriteBank(C.sprites()).get(C.shell().map().quarterIcon().sprite());

    private static MapScreen map() {
        return new MapScreen(C.display(), C.rooms(), C.landmarks(), C.palette(), C.shell().map(), ICON, ARCH, null);
    }

    /** Whether {@code room} on the map carries the icon's {@code frame}: every pixel of it, where it sits. */
    private static boolean bears(Framebuffer fb, DisplayConfig.Playfield f, RoomAddress room, String frame) {
        int cw = f.w() / 16;
        int ch = f.h() / 16;
        Framebuffer alone = new Framebuffer(fb.width(), fb.height());
        int none = 99;
        alone.clear(none);
        int x = f.x() + room.col() * cw + cw / 2;
        int y = f.y() + room.row() * ch + ch;
        ICON.blit(alone, frame, x, y);
        int drawn = 0;
        for (int v = y - ICON.h(); v < y; v++) {
            for (int u = x - ICON.originX(); u < x - ICON.originX() + ICON.w(); u++) {
                if (alone.get(u, v) != none) {
                    drawn++;
                    if (fb.get(u, v) != alone.get(u, v)) {
                        return false;
                    }
                }
            }
        }
        return drawn > 0;
    }

    /** Whether any pixel of {@code room} the icon's {@code frame} leaves bare is lit: the room's map, not painted over. */
    private static boolean anyOtherLit(Framebuffer fb, DisplayConfig.Playfield f, RoomAddress room, String frame) {
        int cw = f.w() / 16;
        int ch = f.h() / 16;
        int ground = C.palette().indexOf("black");
        Framebuffer alone = new Framebuffer(fb.width(), fb.height());
        alone.clear(ground);
        ICON.blit(alone, frame, f.x() + room.col() * cw + cw / 2, f.y() + room.row() * ch + ch);
        for (int y = 1; y < ch - 1; y++) {
            for (int x = 1; x < cw - 1; x++) {
                int u = f.x() + room.col() * cw + x;
                int v = f.y() + room.row() * ch + y;
                if (alone.get(u, v) == ground && fb.get(u, v) != ground) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean bearsShiny(Framebuffer fb, DisplayConfig.Playfield f, RoomAddress room) {
        return C.shell().map().quarterIcon().shiny().stream().anyMatch(frame -> bears(fb, f, room, frame));
    }

    private static boolean anyLit(Framebuffer fb, DisplayConfig.Playfield f, RoomAddress room, int ground) {
        int cw = f.w() / 16;
        int ch = f.h() / 16;
        for (int y = 0; y < ch; y++) {
            for (int x = 0; x < cw; x++) {
                if (fb.get(f.x() + room.col() * cw + x, f.y() + room.row() * ch + y) != ground) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void onlyTheRoomsVisitedAreDrawn() {
        Simulation sim = Simulation.startingIn(C.player(), new WorldGrid(C.rooms()), 0, C.map().startRoom(),
                C.ecosystem(), 1L);
        MapScreen map = map();
        Framebuffer fb = new Framebuffer(C.display().canvas().w(), C.display().canvas().h());
        map.paint(fb, sim, C.shell().map().blinkTicks());   // a tick when the blink is off
        int ground = C.palette().indexOf("black");
        DisplayConfig.Playfield f = C.display().playfield();
        assertThat(anyLit(fb, f, C.map().startRoom(), ground)).as("the start room is known").isTrue();
        assertThat(anyLit(fb, f, new RoomAddress(3, 3), ground)).as("a room never visited is dark").isFalse();
        assertThat(anyLit(fb, f, new RoomAddress(5, 3), ground)).as("and no lair is marked without an eye").isFalse();
    }

    @Test
    void anEyeMarksItsTwoQuartersOnTheMap() {
        // §14.8: the north eye shows the north-west and north-east quarters.
        wulf.data.LandmarksData.Eye eye = C.landmarks().items().eyes().get(0);
        RoomAddress at = wulf.data.LandmarksData.room(eye.room());
        Simulation sim = Simulation.at(C.player(), new WorldGrid(C.rooms()), 0,
                wulf.engine.Fixed.fp(at.col() * Simulation.ROOM_W_PX + eye.spot().x()),
                wulf.engine.Fixed.fp(at.row() * Simulation.ROOM_H_PX + eye.spot().y()), C.ecosystem(), 1L);
        sim.tick(wulf.input.InputState.NONE);
        assertThat(sim.quest().eyeTaken(0)).as("standing on it takes it").isTrue();
        MapScreen map = map();
        Framebuffer fb = new Framebuffer(C.display().canvas().w(), C.display().canvas().h());
        map.paint(fb, sim, C.shell().map().blinkTicks());
        int ground = C.palette().indexOf("black");
        DisplayConfig.Playfield f = C.display().playfield();
        for (wulf.data.LandmarksData.Lair lair : C.landmarks().lairs()) {
            RoomAddress room = wulf.data.LandmarksData.room(lair.room());
            if (eye.reveals().contains(lair.id())) {
                assertThat(bearsShiny(fb, f, room)).as("%s at %s carries a shiny diamond", lair.id(), room).isTrue();
                assertThat(bears(fb, f, room, C.shell().map().quarterIcon().taken())).as("not a dull one").isFalse();
            } else {
                assertThat(anyLit(fb, f, room, ground)).as("%s: the other eye's, not shown", lair.id()).isFalse();
            }
        }
    }

    @Test
    void theDiamondTwinkles() {
        // §17.7: the diamonds take their shiny frames in turn.
        wulf.data.LandmarksData.Eye eye = C.landmarks().items().eyes().get(0);
        RoomAddress at = wulf.data.LandmarksData.room(eye.room());
        Simulation sim = Simulation.at(C.player(), new WorldGrid(C.rooms()), 0,
                wulf.engine.Fixed.fp(at.col() * Simulation.ROOM_W_PX + eye.spot().x()),
                wulf.engine.Fixed.fp(at.row() * Simulation.ROOM_H_PX + eye.spot().y()), C.ecosystem(), 1L);
        sim.tick(wulf.input.InputState.NONE);
        RoomAddress room = wulf.data.LandmarksData.room(C.landmarks().lairs().stream()
                .filter(l -> eye.reveals().contains(l.id())).findFirst().orElseThrow().room());
        DisplayConfig.Playfield f = C.display().playfield();
        wulf.data.ShellConfig.QuarterIcon icon = C.shell().map().quarterIcon();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < icon.shiny().size(); i++) {
            Framebuffer fb = new Framebuffer(C.display().canvas().w(), C.display().canvas().h());
            map().paint(fb, sim, (long) i * icon.frameTicks());
            icon.shiny().stream().filter(frame -> bears(fb, f, room, frame)).forEach(seen::add);
        }
        assertThat(seen).as("it twinkles through every shiny frame").containsExactlyInAnyOrderElementsOf(icon.shiny());
    }

    @Test
    void aTakenQuarterDullsItsDiamond() {
        // §14.8: what is done shows as done, eye or no eye.
        wulf.data.LandmarksData.Lair lair = C.landmarks().lairs().get(0);
        RoomAddress room = wulf.data.LandmarksData.room(lair.room());
        Simulation sim = Simulation.at(C.player(), new WorldGrid(C.rooms()), 0,
                wulf.engine.Fixed.fp(room.col() * Simulation.ROOM_W_PX + lair.pedestal().x()),
                wulf.engine.Fixed.fp(room.row() * Simulation.ROOM_H_PX + lair.pedestal().y()), C.ecosystem(), 1L);
        sim.tick(wulf.input.InputState.NONE);
        assertThat(sim.quest().taken(0)).as("standing on the pedestal takes it").isTrue();
        MapScreen map = map();
        Framebuffer fb = new Framebuffer(C.display().canvas().w(), C.display().canvas().h());
        map.paint(fb, sim, C.shell().map().blinkTicks());
        DisplayConfig.Playfield f = C.display().playfield();
        assertThat(bears(fb, f, room, C.shell().map().quarterIcon().taken())).as("a dull diamond").isTrue();
        assertThat(bearsShiny(fb, f, room)).as("not a shiny one").isFalse();
        assertThat(anyOtherLit(fb, f, room, C.shell().map().quarterIcon().taken())).as("he has been there: its own map shows around the diamond").isTrue();
    }
}
