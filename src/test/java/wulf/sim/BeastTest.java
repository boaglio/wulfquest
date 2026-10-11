package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.FlameData;
import wulf.data.OrchidData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/** AGENTS.md §12.9 — the original's big beasts: rhinos, tribesmen, and the sleeper. */
class BeastTest {

    private static final Content CONTENT = Content.load(Path.of("data"));
    private static final BeastRules BEASTS = CONTENT.beastRules();
    private static final InputState STILL = InputState.NONE;

    private static Simulation sim(CollisionWorld world, RoomAddress room, int lx, int ly) {
        Ecosystem eco = new Ecosystem(CONTENT.originalCreatures(), RoomPopulator.NONE, 0, WulfRules.NONE,
                QuestRules.NONE, OrchidData.NONE, FlameData.NONE, SwarmRules.NONE, BEASTS);
        return Simulation.at(SimFixtures.RULES, world, 0, Fixed.fp(room.col() * Simulation.ROOM_W_PX + lx),
                Fixed.fp(room.row() * Simulation.ROOM_H_PX + ly), eco, 9L);
    }

    private static Beast first(Simulation s, Beast.Kind kind) {
        return s.beasts().stream().filter(b -> b.kind() == kind).findFirst().orElseThrow();
    }

    /** Sets a beast down in a room, at room-local feet, heading as asked. */
    private static void put(Beast b, RoomAddress room, int lx, int ly, int vxFp) {
        b.at = room;
        b.xFp = Fixed.fp(room.col() * Simulation.ROOM_W_PX + lx);
        b.yFp = Fixed.fp(room.row() * Simulation.ROOM_H_PX + ly);
        b.vxFp = vxFp;
        b.vyFp = 0;
        b.faceX = vxFp < 0 ? -1 : 1;
    }

    @Test
    void aNewGameSetsThemWhereTheActorTableSays() {
        Simulation s = sim(SimFixtures.OPEN, new RoomAddress(8, 10), 128, 100);
        assertThat(s.beasts()).hasSize(8);
        assertThat(s.beasts().stream().filter(b -> b.kind() == Beast.Kind.RHINO).map(Beast::at).map(Object::toString))
                .containsExactly("8,9", "7,0", "3,15", "8,9");
        assertThat(first(s, Beast.Kind.SLEEPER).at()).isEqualTo(new RoomAddress(3, 8));
    }

    @Test
    void aRhinoPacesItsRowAndTurnsAtTheScenery() {
        RoomAddress room = new RoomAddress(3, 5);
        int wallCx = room.col() * 32 + 28;
        Simulation s = sim(SimFixtures.verticalWall(wallCx), new RoomAddress(3, 7), 128, 100);
        Beast r = first(s, Beast.Kind.RHINO);
        put(r, room, 120, 100, BEASTS.param(r.kindIndex, "paceFp"));
        int y = r.yFp;
        boolean turned = false;
        for (int t = 0; t < 300; t++) {
            s.tick(STILL);
            turned |= r.vxFp < 0;
            assertThat(r.yFp).as("it keeps to its line").isEqualTo(y);
        }
        assertThat(turned).as("$AD20: the wall turns it about").isTrue();
        assertThat(Math.abs(r.vxFp)).isEqualTo(BEASTS.param(r.kindIndex, "paceFp"));
    }

    @Test
    void aRhinoRunningIntoAWalkingValeCostsALife() {
        RoomAddress room = new RoomAddress(3, 5);
        Simulation s = sim(SimFixtures.OPEN, room, 128, 100);
        Beast r = first(s, Beast.Kind.RHINO);
        put(r, room, 60, 100, BEASTS.param(r.kindIndex, "paceFp"));
        int t = 0;
        while (s.deaths() == 0 && t++ < 200) {
            s.tick(STILL);
        }
        assertThat(s.deaths()).isEqualTo(1);
        assertThat(r.vxFp).as("$ADA2: and it turns about").isNegative();
    }

    @Test
    void aBladeHeldAtARhinoSendsItOffTheOtherWayAtARun() {
        RoomAddress room = new RoomAddress(3, 5);
        Simulation s = sim(SimFixtures.OPEN, room, 128, 100);
        Beast r = first(s, Beast.Kind.RHINO);
        put(r, room, 60, 100, BEASTS.param(r.kindIndex, "paceFp"));
        SimFixtures.run(s, InputState.of(-1, 0, false, false), 1);   // face it
        int t = 0;
        while (r.vxFp > 0 && t++ < 200) {
            s.tick(InputState.of(0, 0, true, false));
        }
        assertThat(s.deaths()).as("$AB68: facing it, fencing, he is safe").isZero();
        assertThat(r.vxFp).isEqualTo(-BEASTS.param(r.kindIndex, "shovedFp"));
    }

    @Test
    void aTribesmanFarAwayIsSetDownARowFromThePlayer() {
        RoomAddress room = new RoomAddress(9, 6);
        Simulation s = sim(SimFixtures.OPEN, room, 128, 100);
        s.god(true);
        s.tick(STILL);
        for (Beast b : s.beasts()) {
            if (b.kind() == Beast.Kind.TRIBESMAN) {
                assertThat(Math.abs(b.at().row() - room.row())).as("$A630").isLessThanOrEqualTo(1);
                assertThat(b.at().col() / BEASTS.followColumns()).as("in his block of columns")
                        .isEqualTo(room.col() / BEASTS.followColumns());
            }
        }
    }

    @Test
    void aSleeperWakesWhenTouchedThenChargesAndSleepsWhereItStops() {
        RoomAddress room = new RoomAddress(3, 5);
        int wallCx = room.col() * 32 + 4;
        Simulation s = sim(SimFixtures.verticalWall(wallCx), room, 128, 100);
        Beast h = first(s, Beast.Kind.SLEEPER);
        put(h, room, 150, 100, BEASTS.param(h.kindIndex, "chargeFp"));
        h.asleep = true;
        SimFixtures.run(s, InputState.of(-1, 0, false, false), 1);   // face it, away from where it lies
        SimFixtures.run(s, InputState.of(1, 0, false, false), 1);
        int t = 0;
        while (h.asleep && t++ < 300) {
            s.tick(InputState.of(1, 0, true, false));   // walk at it, blade out
        }
        assertThat(h.asleep).as("$9EF0: touched, it wakes").isFalse();
        assertThat(h.pauseTicks).isPositive();
        assertThat(s.deaths()).as("he met it with the blade").isZero();
        t = 0;
        while (!h.asleep && t++ < 2000) {
            s.tick(STILL);
        }
        assertThat(h.asleep).as("$9E92: stopped, it sleeps again").isTrue();
    }

    @Test
    void aTribesmanLevelWithAQuarterLightsUp() {
        // $9D41: on the map column of a quarter still lying, and level with it across the room.
        Ecosystem eco = CONTENT.withFeature("inventedCreatures", false).ecosystem();
        wulf.data.LandmarksData.Lair lair = CONTENT.landmarks().lairs().get(0);
        RoomAddress lairRoom = wulf.data.LandmarksData.room(lair.room());
        RoomAddress room = new RoomAddress(lairRoom.col(), lairRoom.row() + 2);
        Simulation s = Simulation.at(SimFixtures.RULES, SimFixtures.OPEN, 0,
                Fixed.fp(room.col() * Simulation.ROOM_W_PX + 40), Fixed.fp(room.row() * Simulation.ROOM_H_PX + 40), eco, 3L);
        s.god(true);
        Beast man = first(s, Beast.Kind.TRIBESMAN);
        put(man, room, lair.pedestal().x(), 120, 0);
        s.tick(STILL);
        assertThat(man.hinting()).isTrue();
        put(man, room, lair.pedestal().x() + 60, 120, 0);
        s.tick(STILL);
        assertThat(man.hinting()).as("out of line, nothing").isFalse();
    }

    @Test
    void sameSeedSameBeasts() {
        Ecosystem eco = CONTENT.withFeature("inventedCreatures", false).ecosystem();
        wulf.world.WorldGrid grid = new wulf.world.WorldGrid(CONTENT.rooms());
        Simulation a = Simulation.startingIn(CONTENT.player(), grid, 6, CONTENT.map().startRoom(), eco, 4L);
        Simulation b = Simulation.startingIn(CONTENT.player(), grid, 6, CONTENT.map().startRoom(), eco, 4L);
        for (int t = 0; t < 3000; t++) {
            InputState in = InputState.of((t / 97) % 3 - 1, (t / 61) % 3 - 1, t % 40 < 10, t % 40 == 0);
            a.tick(in);
            b.tick(in);
            assertThat(a.stateHash()).isEqualTo(b.stateHash());
        }
    }
}
