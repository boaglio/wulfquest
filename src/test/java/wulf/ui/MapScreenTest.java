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
        MapScreen map = new MapScreen(C.display(), C.rooms(), C.landmarks(),
                new SpriteBank(C.sprites()).get(C.landmarks().amulet().sprite()), C.palette(), C.shell().map());
        Framebuffer fb = new Framebuffer(C.display().canvas().w(), C.display().canvas().h());
        map.paint(fb, sim, C.shell().map().blinkTicks());   // a tick when the blink is off
        int ground = C.palette().indexOf("black");
        DisplayConfig.Playfield f = C.display().playfield();
        assertThat(anyLit(fb, f, C.map().startRoom(), ground)).as("the start room is known").isTrue();
        assertThat(anyLit(fb, f, new RoomAddress(3, 3), ground)).as("a room never visited is dark").isFalse();
        assertThat(anyLit(fb, f, new RoomAddress(5, 3), ground)).as("and no lair is marked without an eye").isFalse();
    }
}
