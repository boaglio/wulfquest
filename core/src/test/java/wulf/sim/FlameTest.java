package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.data.FlameData;
import wulf.data.JsonDb;
import wulf.data.OrchidData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/** AGENTS.md §12.7 — the flame the original sends after a player who lingers in a room. */
class FlameTest {

    private static final FlameData FLAME = new JsonDb(Path.of("data")).load("entities/flame", FlameData.class);
    private static final CollisionWorld OPEN = SimFixtures.OPEN;
    private static final InputState STILL = InputState.NONE;
    private static final RoomAddress ROOM = EcoFixtures.ROOM;

    private static Simulation sim() {
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, RoomPopulator.NONE, 0, WulfRules.NONE, QuestRules.NONE,
                OrchidData.NONE, FLAME);
        return Simulation.at(SimFixtures.RULES, OPEN, 0, Fixed.fp(ROOM.col() * Simulation.ROOM_W_PX + 128),
                Fixed.fp(ROOM.row() * Simulation.ROOM_H_PX + 100), eco, 7L);
    }

    private static Creature flame(Simulation s) {
        for (Creature c : s.creatures()) {
            if (c.species().id().equals(FLAME.id()) && c.alive()) {
                return c;
            }
        }
        return null;
    }

    /** Stands still until a flame is burning, or gives up after {@code limit} ticks. */
    private static int ticksToFlame(Simulation s, int limit) {
        for (int t = 1; t <= limit; t++) {
            s.tick(STILL);
            if (flame(s) != null) {
                return t;
            }
        }
        return -1;
    }

    @Test
    void itComesForAPlayerWhoLingers() {
        // $9ABC: $96B4 counts down from $80 while he stays.
        Simulation s = sim();
        int at = ticksToFlame(s, FLAME.lingerTicks() + 50);
        assertThat(at).as("not before he has lingered").isGreaterThan(FLAME.lingerTicks());
        Creature f = flame(s);
        int px = Fixed.px(s.player().xFp());
        int py = Fixed.px(s.player().yFp());
        assertThat(Fixed.px(f.xFp()) == px || Fixed.px(f.yFp()) == py).as("in line with him").isTrue();
    }

    @Test
    void anotherRoomStartsTheWaitAgain() {
        Simulation s = sim();
        SimFixtures.run(s, STILL, FLAME.lingerTicks() - 10);
        RoomAddress from = s.room();
        while (s.room().equals(from)) {
            s.tick(SimFixtures.RIGHT);
        }
        assertThat(ticksToFlame(s, FLAME.lingerTicks() - 20)).as("the new room's wait is whole").isEqualTo(-1);
    }

    @Test
    void noBladeCanTouchIt() {
        Simulation s = sim();
        ticksToFlame(s, FLAME.lingerTicks() + 50);
        Creature f = flame(s);
        s.god(true);   // so it cannot end the test by burning him
        for (int t = 0; t < 300 && flame(s) == f; t++) {
            s.tick(InputState.of(f.centreXPx() < s.player().xFp() >> 8 ? -1 : 1, 0, true, t % 6 == 0));
        }
        assertThat(s.kills()).as("swing as he likes, nothing dies").isZero();
    }

    @Test
    void itBurnsOutAndTheNextComesAtOnce() {
        Simulation s = sim();
        s.god(true);
        ticksToFlame(s, FLAME.lingerTicks() + 50);
        Creature first = flame(s);
        SimFixtures.run(s, STILL, FLAME.lifeTicks());
        assertThat(first.alive()).as("burnt out").isFalse();
        // The wait is not started again while he stays: the next one comes as soon as the first is gone.
        assertThat(ticksToFlame(s, 100)).isPositive();
        assertThat(flame(s)).isNotSameAs(first);
    }

    @Test
    void itsTouchCostsALifeAndGoesOutWithHim() {
        Simulation s = sim();
        ticksToFlame(s, FLAME.lingerTicks() + 50);
        for (int t = 0; t < 3000 && s.player().mode() == Player.Mode.ALIVE; t++) {
            s.tick(STILL);
        }
        assertThat(s.player().mode()).as("it found him").isEqualTo(Player.Mode.DYING);
        assertThat(s.deathsByCause()).containsKey(FLAME.id());
        assertThat(flame(s)).as("and went out as he fell").isNull();
    }
}
