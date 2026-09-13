package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.CreatureData;
import wulf.data.JsonDb;
import wulf.data.PlayerData;
import wulf.data.WulfData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/** AGENTS.md §13 — the Wulf: the warning, the chase across rooms, the parry, and giving up. */
class WulfTest {

    private static final WulfData REAL = new JsonDb(Path.of("data")).load("entities/wulf", WulfData.class);
    private static final PlayerData RULES = SimFixtures.RULES;
    private static final CollisionWorld OPEN = SimFixtures.OPEN;
    private static final InputState STILL = InputState.NONE;
    private static final int ARRIVAL_DELAY = 12;
    private static final int EVADED = 250;

    private static final RoomAddress ROOM = new RoomAddress(3, 5);
    private static final int RX = ROOM.col() * Simulation.ROOM_W_PX;
    private static final int RY = ROOM.row() * Simulation.ROOM_H_PX;
    private static final int CX = ROOM.col() * 32;
    private static final int CY = ROOM.row() * 24;

    /**
     * In {@link #ROOM}, everything solid but a corridor two cells high across the room
     * (local y 96..111). Nothing fits at the north or south edge, and the Wulf and the
     * player share one line — so a swing along it always meets the Wulf.
     */
    private static final CollisionWorld CORRIDOR = (gx, gy) -> OPEN.isSolid(gx, gy)
            || (gx >= CX && gx < CX + 32 && gy >= CY && gy < CY + 24 && gy != CY + 12 && gy != CY + 13);

    /**
     * In {@link #ROOM}, a pen against the east edge — cells x 24..31, y 3..21 — whose only
     * door is a 2-cell gap in its top wall. The player (10 px wide) fits through; the Wulf
     * (26 px) never does.
     */
    private static final CollisionWorld POCKET = (gx, gy) -> {
        if (OPEN.isSolid(gx, gy)) {
            return true;
        }
        int lx = gx - CX;
        int ly = gy - CY;
        if (lx < 0 || ly < 0 || lx >= 32 || ly >= 24) {
            return false;
        }
        return (ly == 3 && lx >= 24 && lx != 27 && lx != 28) || (ly == 21 && lx >= 24) || (lx == 24 && ly >= 3 && ly <= 21);
    };

    private static WulfData tuned(int chancePer10k, int minBetween, int grace, CreatureData.Speed speed, int giveUpTicks,
                                  int leaveMaxTicks) {
        WulfData.Appearance a = REAL.appearance();
        WulfData.Pursuit p = REAL.pursuit();
        return new WulfData(REAL.schemaVersion(), REAL.fidelity(), REAL.id(), REAL.displayName(), REAL.sprite(), REAL.size(),
                REAL.collisionBox(), speed,
                new WulfData.Appearance(chancePer10k, a.chancePerAmuletPiecePer10k(), 0, a.quietRoomsCap(), a.rollEveryTicks(),
                        minBetween, grace, a.neverInRooms(), a.warningTicks(), a.warningFlashPeriodTicks(), a.warningFlashOnTicks(),
                        a.minDistancePx()),
                new WulfData.Pursuit(p.turnCooldownTicks(), p.stallTicks(), giveUpTicks, p.giveUpRoomDistance(), leaveMaxTicks,
                        p.arrivalClearancePx()),
                REAL.parry(), REAL.audio());
    }

    /** The shipped Wulf, appearing with exactly this chance and no waiting. */
    private static WulfData chance(int per10k) {
        return tuned(per10k, 0, 0, REAL.speed(), REAL.pursuit().giveUpTicks(), REAL.pursuit().leaveMaxTicks());
    }

    private static Simulation sim(CollisionWorld world, int freeze, int localX, int localY, WulfData wulf, long seed,
                                  RoomAddress... never) {
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, RoomPopulator.NONE, 0,
                WulfRules.of(wulf, ARRIVAL_DELAY, EVADED, Set.of(never)));
        return Simulation.at(RULES, world, freeze, Fixed.fp(RX + localX), Fixed.fp(RY + localY), eco, seed);
    }

    /** The room edge the Wulf's box is against, or null. */
    private static Wulf.Edge edgeOf(Simulation s) {
        Creature b = s.wulf().body();
        CreatureData.Box box = b.species().collisionBox();
        int left = Fixed.px(b.xFp()) + box.x() - s.room().col() * Simulation.ROOM_W_PX;
        int top = Fixed.px(b.yFp()) + box.y() - s.room().row() * Simulation.ROOM_H_PX;
        if (left == 0) {
            return Wulf.Edge.WEST;
        }
        if (left + box.w() == Simulation.ROOM_W_PX) {
            return Wulf.Edge.EAST;
        }
        if (top == 0) {
            return Wulf.Edge.NORTH;
        }
        return top + box.h() == Simulation.ROOM_H_PX ? Wulf.Edge.SOUTH : null;
    }

    /** Centre to centre, the larger axis: how the clearances are measured. */
    private static int gap(Simulation s) {
        PlayerData.Box pb = RULES.collisionBox();
        int px = Fixed.px(s.player().xFp()) + pb.x() + pb.w() / 2;
        int py = Fixed.px(s.player().yFp()) + pb.y() + pb.h() / 2;
        Creature b = s.wulf().body();
        return Math.max(Math.abs(b.centreXPx() - px), Math.abs(b.centreYPx() - py));
    }

    private static void walkIntoTheRoomEast(Simulation s) {
        RoomAddress from = s.room();
        for (int i = 0; i < 400 && s.room().equals(from); i++) {
            s.tick(SimFixtures.RIGHT);
        }
        assertThat(s.room()).isEqualTo(new RoomAddress(from.col() + 1, from.row()));
    }

    @Test
    void itWarnsAtAnEdgeForFortyTicksBeforeItMoves() {
        Simulation s = sim(CORRIDOR, 0, 40, 108, chance(0), 5L);
        assertThat(s.summonWulf()).isTrue();
        Wulf w = s.wulf();
        assertThat(w.state()).isEqualTo(Wulf.State.WARNING);
        assertThat(edgeOf(s)).isEqualTo(Wulf.Edge.EAST);
        int x = w.body().xFp();
        int y = w.body().yFp();
        WulfData.Appearance a = REAL.appearance();
        List<Simulation.BorderFlash> border = new ArrayList<>();
        border.add(s.borderFlash());
        for (int t = 1; t < a.warningTicks(); t++) {
            s.tick(STILL);
            border.add(s.borderFlash());
            assertThat(w.state()).as("tick %d", t).isEqualTo(Wulf.State.WARNING);
            assertThat(w.body().xFp()).isEqualTo(x);
            assertThat(w.body().yFp()).isEqualTo(y);
        }
        s.tick(STILL);
        assertThat(w.state()).isEqualTo(Wulf.State.PURSUE);
        SimFixtures.run(s, STILL, 10);
        assertThat(w.body().xFp()).as("then it comes for you").isLessThan(x);
        for (int t = 0; t < a.warningTicks(); t++) {
            boolean on = t % a.warningFlashPeriodTicks() < a.warningFlashOnTicks();
            assertThat(border.get(t)).as("border on warning tick %d", t)
                    .isEqualTo(on ? Simulation.BorderFlash.ALARM : Simulation.BorderFlash.NONE);
        }
    }

    @Test
    void itNeverComesInByTheEdgeNearestThePlayer() {
        Object[][] spots = {
            {12, 96, Wulf.Edge.WEST}, {244, 96, Wulf.Edge.EAST}, {128, 20, Wulf.Edge.NORTH}, {128, 190, Wulf.Edge.SOUTH},
        };
        for (Object[] spot : spots) {
            Set<Wulf.Edge> used = EnumSet.noneOf(Wulf.Edge.class);
            for (long seed = 1; seed <= 40; seed++) {
                Simulation s = sim(OPEN, 0, (int) spot[0], (int) spot[1], chance(0), seed);
                assertThat(s.summonWulf()).isTrue();
                Wulf.Edge edge = edgeOf(s);
                assertThat(edge).as("against an edge").isNotNull();
                assertThat(edge).as("player near %s, seed %d", spot[2], seed).isNotEqualTo(spot[2]);
                assertThat(gap(s)).as("appears well away").isGreaterThanOrEqualTo(REAL.appearance().minDistancePx());
                used.add(edge);
            }
            assertThat(used).as("the other three edges all get used").hasSize(3);
        }
    }

    @Test
    void enteringARoomRollsForItUnlessTheRoomIsOffLimits() {
        Simulation sure = sim(OPEN, 0, 250, 100, chance(10000), 5L);
        walkIntoTheRoomEast(sure);
        assertThat(sure.wulf().state()).isEqualTo(Wulf.State.WARNING);
        assertThat(sure.wulf().origin()).isEqualTo(sure.room());

        Simulation never = sim(OPEN, 0, 250, 100, chance(0), 5L);
        walkIntoTheRoomEast(never);
        SimFixtures.run(never, STILL, 1000);
        assertThat(never.wulf().appearances()).isZero();

        Simulation offLimits = sim(OPEN, 0, 250, 100, chance(10000), 5L, new RoomAddress(4, 5));
        walkIntoTheRoomEast(offLimits);
        SimFixtures.run(offLimits, STILL, 1000);
        assertThat(offLimits.wulf().appearances()).as("no roll, on entry or later, in a room it avoids").isZero();
    }

    @Test
    void aboutOneRoomEntryInSevenBringsItWithTheShippedChances() {
        WulfData.Appearance a = REAL.appearance();
        int expectedPer10k = a.baseChancePer10k() + a.chancePerQuietRoomPer10k();   // one quiet room: this one
        int n = 2000;
        int came = 0;
        for (long seed = 0; seed < n; seed++) {
            Simulation s = sim(OPEN, 0, 250, 100, REAL, seed);
            walkIntoTheRoomEast(s);
            if (s.wulf().state() == Wulf.State.WARNING) {
                came++;
            }
        }
        int expected = n * expectedPer10k / 10_000;
        assertThat(came).as("appearances in %d first entries, expecting about %d", n, expected)
                .isBetween(expected - 60, expected + 60);
    }

    @Test
    void itWaitsBetweenAppearances() {
        int minBetween = 400;
        WulfData quick = tuned(10000, minBetween, 0, REAL.speed(), 5, 1);   // gives up at once, and is gone
        Simulation s = sim(CORRIDOR, 0, 128, 108, quick, 5L);
        Wulf w = s.wulf();
        List<Integer> appeared = new ArrayList<>();
        List<Integer> left = new ArrayList<>();
        Wulf.State last = w.state();
        for (int t = 1; t <= 3000; t++) {
            s.tick(STILL);
            if (last == Wulf.State.ABSENT && w.state() != Wulf.State.ABSENT) {
                appeared.add(t);
            }
            if (last != Wulf.State.ABSENT && w.state() == Wulf.State.ABSENT) {
                left.add(t);
            }
            last = w.state();
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(appeared).hasSizeGreaterThanOrEqualTo(3);
        for (int i = 0; i < left.size() && i + 1 < appeared.size(); i++) {
            assertThat(appeared.get(i + 1) - left.get(i)).as("gap after leaving at tick %d", left.get(i))
                    .isGreaterThanOrEqualTo(minBetween);
        }
    }

    @Test
    void itGivesYouTimeAfterARespawn() {
        int grace = REAL.appearance().graceTicksAfterPlayerDeath();
        Simulation s = sim(CORRIDOR, 0, 128, 108, tuned(10000, 0, grace, REAL.speed(), 900, 150), 5L);
        assertThat(s.kill()).isTrue();
        int t = 0;
        while (s.player().mode() != Player.Mode.ALIVE && t < 1000) {
            s.tick(STILL);
            t++;
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
        int after = 0;
        while (s.wulf().state() == Wulf.State.ABSENT && after < 2000) {
            s.tick(STILL);
            after++;
        }
        assertThat(s.wulf().state()).isEqualTo(Wulf.State.WARNING);
        assertThat(after).isGreaterThanOrEqualTo(grace);
    }

    @Test
    void itFollowsThroughAFlipAndArrivesABeatLaterWhereItWas() {
        int freeze = 6;
        Simulation s = sim(OPEN, freeze, 236, 60, chance(0), 5L);
        assertThat(s.summonWulf()).isTrue();
        Wulf w = s.wulf();
        SimFixtures.run(s, STILL, REAL.appearance().warningTicks());
        assertThat(w.state()).isEqualTo(Wulf.State.PURSUE);

        RoomAddress next = new RoomAddress(4, 5);
        int alongY = 0;
        for (int i = 0; i < 100 && !s.room().equals(next); i++) {
            alongY = Fixed.px(w.body().yFp()) - RY;
            s.tick(SimFixtures.RIGHT);
        }
        assertThat(s.room()).isEqualTo(next);
        assertThat(w.state()).isEqualTo(Wulf.State.ARRIVING);
        assertThat(w.visible()).as("not on screen until it arrives").isFalse();

        int ticks = 0;
        while (w.state() == Wulf.State.ARRIVING && ticks < 100) {
            s.tick(SimFixtures.RIGHT);
            ticks++;
        }
        assertThat(ticks).as("the flip's hitch, then the arrival delay").isEqualTo(freeze + ARRIVAL_DELAY);
        assertThat(w.state()).isEqualTo(Wulf.State.PURSUE);
        assertThat(edgeOf(s)).as("in by the edge the player came through").isEqualTo(Wulf.Edge.WEST);
        int clearance = REAL.pursuit().arrivalClearancePx();
        assertThat(gap(s)).as("a beat behind you, never on top of you").isGreaterThanOrEqualTo(clearance);
        assertThat(Math.abs(Fixed.px(w.body().yFp()) - RY - alongY)).as("near where it was along that edge")
                .isLessThanOrEqualTo(clearance + Reach.STEP_PX);
        assertThat(w.followedFlips()).isEqualTo(1);
    }

    /** Found by the escape forge: arrivals seated on a player just through the edge, a death with no chance to act. */
    @Test
    void itNeverArrivesOnTopOfYou() {
        int clearance = REAL.pursuit().arrivalClearancePx();
        int closeCalls = 0;
        for (long seed = 1; seed <= 60; seed++) {
            Simulation s = sim(OPEN, 6, 236, 100, chance(0), seed);
            assertThat(s.summonWulf()).isTrue();
            Wulf w = s.wulf();
            for (int t = 0; t < 400 && (w.state() != Wulf.State.PURSUE || gap(s) > 40); t++) {
                s.tick(STILL);   // let it come right up behind
            }
            RoomAddress from = s.room();
            for (int t = 0; t < 60 && s.room().equals(from) && s.player().mode() == Player.Mode.ALIVE; t++) {
                s.tick(SimFixtures.RIGHT);
            }
            if (s.player().mode() != Player.Mode.ALIVE || s.room().equals(from)) {
                continue;
            }
            closeCalls++;
            for (int t = 0; t < 200 && w.state() == Wulf.State.ARRIVING; t++) {
                s.tick(SimFixtures.RIGHT);
            }
            if (w.state() == Wulf.State.PURSUE) {
                assertThat(gap(s)).as("seed %d", seed).isGreaterThanOrEqualTo(clearance);
            }
            assertThat(s.player().mode()).as("seed %d: alive the tick it arrives", seed).isEqualTo(Player.Mode.ALIVE);
        }
        assertThat(closeCalls).as("flips made with the Wulf close behind").isGreaterThan(20);
    }

    @Test
    void itGivesUpWhenItCannotGetToYouAndRunsForAnEdge() {
        Simulation s = sim(POCKET, 0, 228, 100, chance(0), 5L);
        assertThat(s.summonWulf()).isTrue();
        Wulf w = s.wulf();
        SimFixtures.run(s, STILL, REAL.appearance().warningTicks());
        int giveUp = REAL.pursuit().giveUpTicks();
        SimFixtures.run(s, STILL, giveUp - 1);
        assertThat(w.state()).isEqualTo(Wulf.State.PURSUE);
        assertThat(s.score()).isZero();

        s.tick(STILL);
        assertThat(w.state()).isEqualTo(Wulf.State.LEAVING);
        assertThat(s.score()).as("an escape is worth something").isEqualTo(EVADED);
        assertThat(w.evasions()).isEqualTo(1);

        int ticks = 0;
        while (w.state() == Wulf.State.LEAVING && ticks < 1000) {
            s.tick(STILL);
            ticks++;
        }
        assertThat(w.state()).isEqualTo(Wulf.State.ABSENT);
        assertThat(ticks).isLessThanOrEqualTo(REAL.pursuit().leaveMaxTicks());
        assertThat(s.score()).as("scored once").isEqualTo(EVADED);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
    }

    @Test
    void outrunningItThreeRoomsFromWhereItAppearedEndsTheChase() {
        WulfData slow = tuned(0, 0, 0, new CreatureData.Speed(128, 128), REAL.pursuit().giveUpTicks(),
                REAL.pursuit().leaveMaxTicks());
        Simulation s = sim(OPEN, 0, 236, 100, slow, 5L);
        assertThat(s.summonWulf()).isTrue();
        Wulf w = s.wulf();
        int distance = REAL.pursuit().giveUpRoomDistance();
        for (int rooms = 1; rooms < distance; rooms++) {
            walkIntoTheRoomEast(s);
            assertThat(w.state()).as("still after you, %d rooms on", rooms).isIn(Wulf.State.ARRIVING, Wulf.State.PURSUE);
        }
        walkIntoTheRoomEast(s);
        assertThat(s.room().col() - ROOM.col()).isEqualTo(distance);
        assertThat(w.state()).isEqualTo(Wulf.State.ABSENT);
        assertThat(w.evasions()).isEqualTo(1);
        assertThat(w.followedFlips()).isEqualTo(distance - 1);
        assertThat(s.score()).isEqualTo(EVADED);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
    }

    @Test
    void aParryPushesItBackAndStunsItButScoresNothing() {
        Simulation s = sim(CORRIDOR, 0, 40, 108, chance(0), 5L);
        assertThat(s.summonWulf()).isTrue();
        Wulf w = s.wulf();
        int before = 0;
        for (int t = 0; t < 400 && w.parries() == 0; t++) {
            int gap = w.body().centreXPx() - Fixed.px(s.player().xFp());
            boolean swing = w.state() == Wulf.State.PURSUE && gap <= 30
                    && !s.player().swinging() && s.player().cooldown() == 0;
            before = w.body().xFp();
            s.tick(swing ? InputState.of(1, 0, true, true) : STILL);
        }
        assertThat(w.parries()).isEqualTo(1);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
        int push = RULES.sabre().repelWulfPx();
        int stun = RULES.sabre().repelWulfStunTicks();
        assertThat(w.body().xFp() - before).as("pushed back along the swing, net of its own step")
                .isGreaterThanOrEqualTo(Fixed.fp(push - 3));
        assertThat(w.body().aux()).isEqualTo(stun);
        assertThat(s.score()).isZero();

        int flash = REAL.parry().borderFlashTicks();
        assertThat(s.borderFlash()).isEqualTo(Simulation.BorderFlash.PARRY);
        SimFixtures.run(s, STILL, flash - 1);
        assertThat(s.borderFlash()).isEqualTo(Simulation.BorderFlash.PARRY);
        s.tick(STILL);
        assertThat(s.borderFlash()).isEqualTo(Simulation.BorderFlash.NONE);

        int x = w.body().xFp();
        SimFixtures.run(s, STILL, stun - flash - 1);
        assertThat(w.body().xFp()).as("stunned").isEqualTo(x);
        assertThat(w.parries()).as("one parry per swing").isEqualTo(1);
        SimFixtures.run(s, STILL, 10);
        assertThat(w.body().xFp()).as("and then it comes again").isLessThan(x);
    }

    @Test
    void itKillsOnTouchAndIsGoneWhenYouRespawn() {
        Simulation s = sim(CORRIDOR, 0, 40, 108, chance(0), 5L);
        assertThat(s.summonWulf()).isTrue();
        int t = 0;
        while (s.player().mode() == Player.Mode.ALIVE && t < 600) {
            s.tick(STILL);
            t++;
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.DYING);
        assertThat(s.wulf().visible()).as("it stands over you while you fall").isTrue();
        while (s.player().mode() != Player.Mode.ALIVE && t < 2000) {
            s.tick(STILL);
            t++;
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(s.wulf().state()).isEqualTo(Wulf.State.ABSENT);
        assertThat(s.wulf().evasions()).isZero();
    }

    @Test
    void withoutAWulfNothingComes() {
        Simulation s = SimFixtures.at(OPEN, 1000, 1000);
        assertThat(s.summonWulf()).isFalse();
        assertThat(s.wulf().state()).isEqualTo(Wulf.State.ABSENT);
    }

    @Test
    void theStartRoomIsOffLimits() {
        Content c = Content.load(Path.of("data"));
        assertThat(c.wulfRules().neverIn()).containsExactly(c.map().startRoom());
        assertThat(c.wulfRules().enabled()).isTrue();
    }
}
