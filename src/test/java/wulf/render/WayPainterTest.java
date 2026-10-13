package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.LandmarksData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.Simulation;
import wulf.world.CollisionMask;
import wulf.world.RoomAddress;
import wulf.world.Wayfinder;
import wulf.world.WorldGrid;

/** AGENTS.md §14.10 — once a glass is taken, the way leads to a quarter still out there, and an arrow shows it. */
class WayPainterTest {

    private static Content c;
    private static WorldGrid grid;
    private static WayPainter way;

    @BeforeAll
    static void load() {
        c = Content.load(Path.of("data"));
        grid = new WorldGrid(c.rooms());
        way = new WayPainter(c.display(), new SpriteBank(c.sprites()), c.landmarks(), c.player().collisionBox(),
                new Wayfinder(grid));
    }

    private static Simulation at(LandmarksData.Place place, int dy) {
        RoomAddress r = LandmarksData.room(place.room());
        return Simulation.at(c.player(), grid, 0, Fixed.fp(r.col() * Simulation.ROOM_W_PX + place.spot().x()),
                Fixed.fp(r.row() * Simulation.ROOM_H_PX + place.spot().y() + dy), c.ecosystem(0), 2L);
    }

    @Test
    void noGlassNoWay() {
        Simulation s = at(c.landmarks().items().glass().places().get(0), 40);
        s.tick(InputState.NONE);
        assertThat(way.route(s)).isEmpty();
    }

    @Test
    void eachGlassLeadsToAQuarterOfThisGameAndAnArrowShowsWhereToLeave() {
        for (LandmarksData.Place place : c.landmarks().items().glass().places()) {
            Simulation s = at(place, 0);
            s.tick(InputState.NONE);
            assertThat(s.quest().wayTicks()).as(place.room()).isPositive();
            int[] route = way.route(s);
            assertThat(route).as(place.room()).isNotEmpty();
            int end = route[route.length - 1];
            RoomAddress endRoom = new RoomAddress(Wayfinder.gx(end) / CollisionMask.COLS, Wayfinder.gy(end) / CollisionMask.ROWS);
            assertThat(s.landmarks().lairs().stream().map(l -> LandmarksData.room(l.room())))
                    .as("ends where this game hides a quarter").contains(endRoom);
            assertThat(endRoom).isNotEqualTo(s.room());

            Framebuffer with = new Framebuffer(c.display().canvas().w(), c.display().canvas().h());
            while ((s.tick() / c.landmarks().items().glass().arrow().blinkTicks()) % 2 != 0) {
                s.tick(InputState.NONE);
            }
            way.paintArrow(with, s);
            byte[] blank = new byte[with.pixels().length];
            assertThat(Arrays.equals(with.pixels(), blank)).as("an arrow is drawn").isFalse();
        }
    }
}
