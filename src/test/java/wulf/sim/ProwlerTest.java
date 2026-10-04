package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static wulf.sim.SimFixtures.FIRE;
import static wulf.sim.SimFixtures.HOLD_FIRE;
import static wulf.sim.SimFixtures.LEFT;
import static wulf.sim.SimFixtures.RIGHT;
import static wulf.sim.SimFixtures.run;

import java.nio.file.Path;
import wulf.data.JsonDb;
import wulf.data.PlayerData;
import wulf.data.WulfData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;
import org.junit.jupiter.api.Test;

/** AGENTS.md §13.8 — the original's Wulf: one, on one row, sat down by a sabre held towards it. */
class ProwlerTest {

    private static final WulfData REAL = new JsonDb(Path.of("data")).load("entities/wulf", WulfData.class);
    private static final WulfData.Patrol P = REAL.patrol();
    private static final PlayerData RULES = SimFixtures.RULES;
    private static final CollisionWorld OPEN = SimFixtures.OPEN;
    private static final InputState STILL = InputState.NONE;
    private static final int ROW = 15;
    private static final int W = Simulation.ROOM_W_PX;
    private static final int H = Simulation.ROOM_H_PX;

    /** The shipped Wulf, starting in {@code room} at local x {@code xPx}, heading {@code heading}. */
    private static WulfRules prowler(RoomAddress room, int xPx, int heading) {
        WulfData.Patrol p = new WulfData.Patrol(P.fidelity(), room.toString(), xPx, 96, heading, P.speedFp(),
                P.risenSpeedFp(), P.sitReleaseTicks(), P.riseTicks());
        return WulfRules.patrol(new WulfData(REAL.schemaVersion(), REAL.fidelity(), REAL.id(), REAL.displayName(),
                REAL.sprite(), REAL.size(), REAL.collisionBox(), REAL.speed(), REAL.appearance(), REAL.pursuit(),
                REAL.parry(), p, REAL.audio()));
    }

    /** The player at local (px, 96) in {@code room}: on the Wulf's line when it is the Wulf's row. */
    private static Simulation sim(CollisionWorld world, RoomAddress room, int px, WulfRules wulf) {
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, RoomPopulator.NONE, 0, wulf);
        return Simulation.at(RULES, world, 0, Fixed.fp(room.col() * W + px), Fixed.fp(room.row() * H + 96), eco, 1L);
    }

    @Test
    void itStartsWhereTheActorTableSays() {
        // $AC77: room (6,15), facing west — the shipped data, through the real content's rules.
        WulfRules shipped = WulfRules.patrol(REAL);
        Simulation s = sim(OPEN, new RoomAddress(3, 5), 128, shipped);
        assertThat(s.wulf().at()).isEqualTo(new RoomAddress(6, 15));
        assertThat(s.wulf().state()).isEqualTo(Wulf.State.PROWLING);
        assertThat(s.wulf().heading()).isEqualTo(-1);
        assertThat(s.wulf().visible()).as("the player is elsewhere").isFalse();
    }

    @Test
    void itRunsItsRowAndComesInAtTheOtherEndOfTheMap() {
        // $B19F, $B193: off the east end of the map it is at the west end, the same row.
        Simulation s = sim(OPEN, new RoomAddress(3, 5), 128, prowler(new RoomAddress(15, ROW), 240, 1));
        int x = Fixed.px(s.wulf().body().xFp());
        run(s, STILL, 20);
        assertThat(s.wulf().at()).isEqualTo(new RoomAddress(0, ROW));
        assertThat(Fixed.px(s.wulf().body().xFp())).isLessThan(x);
        assertThat(s.wulf().heading()).isEqualTo(1);
        int y = s.wulf().body().yFp();
        run(s, STILL, 3000);
        assertThat(s.wulf().at().row()).isEqualTo(ROW);
        assertThat(s.wulf().body().yFp()).as("it never moves up or down").isEqualTo(y);
    }

    @Test
    void itMovesWhileThePlayerIsElsewhere() {
        Simulation s = sim(OPEN, new RoomAddress(3, 5), 128, prowler(new RoomAddress(8, ROW), 128, -1));
        int x = s.wulf().body().xFp();
        run(s, STILL, 10);
        assertThat(x - s.wulf().body().xFp()).isEqualTo(10 * P.speedFp());
    }

    @Test
    void blockedItTurnsBack() {
        // $AAED, $ADB3: a wall across the row, a little west of it.
        int wallGx = 8 * 32 + 10;
        CollisionWorld walled = (gx, gy) -> OPEN.isSolid(gx, gy) || gx == wallGx;
        Simulation s = sim(walled, new RoomAddress(3, 5), 128, prowler(new RoomAddress(8, ROW), 128, -1));
        run(s, STILL, 60);
        assertThat(s.wulf().heading()).isEqualTo(1);
        assertThat(Fixed.px(s.wulf().body().xFp())).isGreaterThan(wallGx * 8);
    }

    @Test
    void itTurnsTowardsThePlayerOnceAsTheyComeOntoItsRow() {
        // $A922: the player's room is east of it on its row, so it turns east, once.
        Simulation s = sim(OPEN, new RoomAddress(12, ROW), 128, prowler(new RoomAddress(4, ROW), 128, -1));
        s.tick(STILL);
        assertThat(s.wulf().heading()).isEqualTo(1);
        assertThat(s.wulf().latch()).isEqualTo(Wulf.Latch.STEERED);
    }

    @Test
    void offItsRowItKeepsItsHeading() {
        Simulation s = sim(OPEN, new RoomAddress(12, 14), 128, prowler(new RoomAddress(4, ROW), 128, -1));
        run(s, STILL, 5);
        assertThat(s.wulf().heading()).isEqualTo(-1);
        assertThat(s.wulf().latch()).isEqualTo(Wulf.Latch.FREE);
    }

    @Test
    void inItsRoomItGoesForWhicheverSideThePlayerIsOn() {
        RoomAddress room = new RoomAddress(5, ROW);
        Simulation s = sim(OPEN, room, 40, prowler(room, 200, 1));
        s.tick(STILL);
        assertThat(s.wulf().heading()).isEqualTo(-1);
        assertThat(s.wulf().visible()).isTrue();
    }

    /** The player at local x 40 facing east, the Wulf at 200 heading west, at it: sabre out. */
    private static Simulation facingIt() {
        RoomAddress room = new RoomAddress(5, ROW);
        Simulation s = sim(OPEN, room, 40, prowler(room, 200, -1));
        s.tick(RIGHT);
        s.tick(FIRE);
        return s;
    }

    @Test
    void aSabreHeldTowardsItAsItComesMakesItSit() {
        Simulation s = facingIt();
        assertThat(s.wulf().state()).isEqualTo(Wulf.State.SITTING);
        int x = s.wulf().body().xFp();
        run(s, HOLD_FIRE, 200);
        assertThat(s.wulf().state()).as("it sits for as long as the blade is held at it").isEqualTo(Wulf.State.SITTING);
        assertThat(s.wulf().body().xFp()).isEqualTo(x);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
    }

    @Test
    void letGoItSitsOnThenGetsUpFasterAndWillNotSitAgain() {
        Simulation s = facingIt();
        // The blade stays out a moment after fire is let go (§11.6); the sitting runs on from then.
        int sat = 0;
        while (s.wulf().state() == Wulf.State.SITTING && sat < 200) {
            s.tick(STILL);
            sat++;
        }
        assertThat(sat).isGreaterThanOrEqualTo(P.sitReleaseTicks());
        assertThat(s.wulf().state()).isEqualTo(Wulf.State.RISING);
        run(s, STILL, P.riseTicks());
        assertThat(s.wulf().state()).isEqualTo(Wulf.State.PROWLING);
        assertThat(s.wulf().latch()).isEqualTo(Wulf.Latch.SPENT);
        int x = s.wulf().body().xFp();
        s.tick(FIRE);
        assertThat(s.wulf().state()).as("$A8B2 wants IX+$02 below $10").isEqualTo(Wulf.State.PROWLING);
        assertThat(x - s.wulf().body().xFp()).as("$A964: faster").isEqualTo(P.risenSpeedFp());
    }

    @Test
    void facingAwayDoesNotStopIt() {
        RoomAddress room = new RoomAddress(5, ROW);
        Simulation s = sim(OPEN, room, 40, prowler(room, 200, -1));
        s.tick(LEFT);
        s.tick(FIRE);
        assertThat(s.wulf().state()).isEqualTo(Wulf.State.PROWLING);
    }

    @Test
    void itsTouchCostsALifeAndItTurnsAway() {
        // $A880: the touch kills; $A8AC: it turns from a player who was not fighting.
        RoomAddress room = new RoomAddress(5, ROW);
        Simulation s = sim(OPEN, room, 100, prowler(room, 140, -1));
        for (int t = 0; t < 100 && s.player().mode() == Player.Mode.ALIVE; t++) {
            s.tick(STILL);
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.DYING);
        assertThat(s.wulf().heading()).isEqualTo(1);
    }

    @Test
    void itRunsOnWhileThePlayerIsDown() {
        RoomAddress room = new RoomAddress(5, ROW);
        Simulation s = sim(OPEN, new RoomAddress(3, 5), 128, prowler(room, 128, -1));
        s.kill();
        int x = s.wulf().body().xFp();
        run(s, STILL, 5);
        assertThat(s.wulf().body().xFp()).isNotEqualTo(x);
    }

    @Test
    void theDevKeyDoesNotSummonIt() {
        Simulation s = sim(OPEN, new RoomAddress(3, 5), 128, prowler(new RoomAddress(5, ROW), 128, -1));
        assertThat(s.summonWulf()).isFalse();
    }
}
