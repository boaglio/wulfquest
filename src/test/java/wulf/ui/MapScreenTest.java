package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.DisplayConfig;
import wulf.render.Framebuffer;
import wulf.render.SpriteBank;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §17.7 — the map shows the rooms Vale has been in, and nothing else, until the scroll is found. */
class MapScreenTest {

    private static final Content C = Content.load(Path.of("data"));

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
        MapScreen map = new MapScreen(C.display(), C.rooms(), C.landmarks(), C.palette(), C.shell().map());
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
        MapScreen map = new MapScreen(C.display(), C.rooms(), C.landmarks(), C.palette(), C.shell().map());
        Framebuffer fb = new Framebuffer(C.display().canvas().w(), C.display().canvas().h());
        map.paint(fb, sim, C.shell().map().blinkTicks());
        int ground = C.palette().indexOf("black");
        DisplayConfig.Playfield f = C.display().playfield();
        int cw = f.w() / 16;
        int ch = f.h() / 16;
        for (wulf.data.LandmarksData.Lair lair : C.landmarks().lairs()) {
            RoomAddress room = wulf.data.LandmarksData.room(lair.room());
            int centre = fb.get(f.x() + room.col() * cw + cw / 2, f.y() + room.row() * ch + ch / 2);
            if (eye.reveals().contains(lair.id())) {
                String colour = C.shell().map().quarters().get(lair.piece().slot());
                assertThat(centre).as("%s at %s fills its room in its quarter's colour", lair.id(), room)
                        .isEqualTo(C.palette().indexOf(colour));
            } else {
                assertThat(anyLit(fb, f, room, ground)).as("%s: the other eye's, not shown", lair.id()).isFalse();
            }
        }
    }

    @Test
    void aTakenQuarterTurnsItsLairGrey() {
        // §14.8: what is done shows as done, eye or no eye.
        wulf.data.LandmarksData.Lair lair = C.landmarks().lairs().get(0);
        RoomAddress room = wulf.data.LandmarksData.room(lair.room());
        Simulation sim = Simulation.at(C.player(), new WorldGrid(C.rooms()), 0,
                wulf.engine.Fixed.fp(room.col() * Simulation.ROOM_W_PX + lair.pedestal().x()),
                wulf.engine.Fixed.fp(room.row() * Simulation.ROOM_H_PX + lair.pedestal().y()), C.ecosystem(), 1L);
        sim.tick(wulf.input.InputState.NONE);
        assertThat(sim.quest().taken(0)).as("standing on the pedestal takes it").isTrue();
        MapScreen map = new MapScreen(C.display(), C.rooms(), C.landmarks(), C.palette(), C.shell().map());
        Framebuffer fb = new Framebuffer(C.display().canvas().w(), C.display().canvas().h());
        map.paint(fb, sim, C.shell().map().blinkTicks());
        DisplayConfig.Playfield f = C.display().playfield();
        int centre = fb.get(f.x() + room.col() * (f.w() / 16) + f.w() / 32, f.y() + room.row() * (f.h() / 16) + f.h() / 32);
        assertThat(centre).isEqualTo(C.palette().indexOf(C.shell().map().taken()));
    }
}
