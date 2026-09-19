package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.CreatureData;
import wulf.data.LandmarksData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.ai.GuardOrbit;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §14 — guardians, the amulet, the Keeper of the Arch, the shrines, and the way out. */
class QuestTest {

    private static final CollisionWorld OPEN = SimFixtures.OPEN;
    private static final InputState STILL = InputState.NONE;

    private static Content content;
    private static QuestRules real;
    private static LandmarksData marks;

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
        real = content.questRules();
        marks = content.landmarks();
    }

    /** The shipped quest with every guardian pinned to one spot on a ring of this radius, east of its pedestal. */
    private static QuestRules pinnedGuardians(int radiusPx, int lungePx) {
        List<CreatureData.Species> pinned = new ArrayList<>();
        for (CreatureData.Species s : real.guardianSpecies()) {
            Map<String, Integer> params = new LinkedHashMap<>();
            params.put("orbitRadiusPx", radiusPx);
            params.put("orbitTicksPerRev", 1_000_000);   // a turn every six hours: it stays at angle 0, due east
            params.put("lungePx", lungePx);
            params.put("lungeTicks", 90);
            pinned.add(new CreatureData.Species(s.id(), s.displayName(), s.sprite(), s.size(), s.collisionBox(),
                    new CreatureData.Speed(0, 0), s.hp(), s.score(), s.ignoresScenery(),
                    new CreatureData.Behaviour(s.behaviour().kind(), params)));
        }
        return new QuestRules(true, real.landmarks(), pinned, real.keeperSpecies(), real.orbit(), real.stepAsidePx(),
                real.stepAsideTicks(), real.nudgeZonePx(), real.nudgePx(), real.pieceScore(), real.escapeBonus(),
                real.timeBonusMax(), real.timeBonusTicksDivisor(), real.lifeRemainingBonus(), real.caveHints());
    }

    private static Simulation sim(CollisionWorld world, QuestRules rules, RoomAddress room, int localX, int localY) {
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, RoomPopulator.NONE, 0, WulfRules.NONE, rules);
        return Simulation.at(SimFixtures.RULES, world, 0, Fixed.fp(room.col() * Simulation.ROOM_W_PX + localX),
                Fixed.fp(room.row() * Simulation.ROOM_H_PX + localY), eco, 3L);
    }

    private static RoomAddress lairRoom(int i) {
        return LandmarksData.room(marks.lairs().get(i).room());
    }

    /** Walks along x to a world pixel, then stops. The player must survive the walk. */
    private static void walkX(Simulation s, int worldXPx) {
        for (int t = 0; t < 20_000 && Math.abs(Fixed.fp(worldXPx) - s.player().xFp()) > Fixed.ONE; t++) {
            s.tick(InputState.of(Integer.signum(Fixed.fp(worldXPx) - s.player().xFp()), 0, false, false));
            assertThat(s.player().mode()).as("alive walking to x %d, in %s", worldXPx, s.room()).isEqualTo(Player.Mode.ALIVE);
        }
    }

    private static void walkY(Simulation s, int worldYPx) {
        for (int t = 0; t < 20_000 && Math.abs(Fixed.fp(worldYPx) - s.player().yFp()) > Fixed.ONE
                && s.player().mode() == Player.Mode.ALIVE; t++) {
            s.tick(InputState.of(0, Integer.signum(Fixed.fp(worldYPx) - s.player().yFp()), false, false));
        }
    }

    private static int wx(RoomAddress room, int localX) {
        return room.col() * Simulation.ROOM_W_PX + localX;
    }

    private static int wy(RoomAddress room, int localY) {
        return room.row() * Simulation.ROOM_H_PX + localY;
    }

    @Test
    void aLairHoldsItsGuardianAndItsQuarterAndNothingElseAndTheWayOutIsClear() {
        WorldGrid grid = new WorldGrid(content.rooms());
        for (int i = 0; i < marks.lairs().size(); i++) {
            Simulation s = Simulation.startingIn(content.player(), grid, 6, lairRoom(i), content.ecosystem(), 9L);
            assertThat(s.quest().inLair()).isTrue();
            assertThat(s.quest().guardian().species().id()).isEqualTo(marks.lairs().get(i).guardian());
            assertThat(s.quest().taken(i)).isFalse();
            assertThat(s.creatures()).as("§14.3: a clean puzzle").isEmpty();
        }
        Simulation exit = Simulation.startingIn(content.player(), grid, 6, marks.exitRoom(), content.ecosystem(), 9L);
        assertThat(exit.quest().keeperHere()).isTrue();
        assertThat(exit.quest().keeperState()).isEqualTo(Quest.Keeper.BLOCKING);
        assertThat(exit.creatures()).isEmpty();
    }

    @Test
    void theGuardianCirclesItsPedestal() {
        RoomAddress lair = lairRoom(0);
        LandmarksData.Point pedestal = marks.lairs().get(0).pedestal();
        Simulation s = sim(OPEN, real, lair, 20, 180);
        int radius = real.orbit().radiusPx();
        boolean west = false;
        boolean east = false;
        boolean north = false;
        boolean south = false;
        for (int t = 0; t < real.orbit().ticksPerRev() + 20; t++) {
            s.tick(STILL);
            Creature g = s.quest().guardian();
            int dx = Fixed.px(g.xFp()) - wx(lair, pedestal.x());
            int dy = Fixed.px(g.yFp()) - wy(lair, pedestal.y());
            assertThat(Math.max(Math.abs(dx), Math.abs(dy))).as("tick %d", t).isLessThanOrEqualTo(radius + 3);
            west |= dx < -radius / 2;
            east |= dx > radius / 2;
            north |= dy < -radius / 2;
            south |= dy > radius / 2;
        }
        assertThat(List.of(west, east, north, south)).as("all the way round").containsOnly(true);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
    }

    @Test
    void comeTooCloseAndItCharges() {
        RoomAddress lair = lairRoom(0);
        LandmarksData.Point pedestal = marks.lairs().get(0).pedestal();
        Simulation s = sim(OPEN, real, lair, pedestal.x() - real.orbit().radiusPx() - 38, pedestal.y());
        boolean charged = false;
        for (int t = 0; t < 400 && s.player().mode() == Player.Mode.ALIVE; t++) {
            s.tick(STILL);
            charged |= s.quest().guardian().phase() == GuardOrbit.LUNGING;
        }
        assertThat(charged).isTrue();
        assertThat(s.player().mode()).as("standing still is not a plan").isEqualTo(Player.Mode.DYING);
    }

    @Test
    void aSwingPushesAGuardianBackAndStunsItButNeverHurtsIt() {
        QuestRules pinned = pinnedGuardians(48, 0);
        RoomAddress lair = lairRoom(0);
        LandmarksData.Point pedestal = marks.lairs().get(0).pedestal();
        // The guardian sits 48 px due east of the pedestal. Stand between them — clear of the
        // quarter, so taking it cannot muddy the score — within a blade of the guardian, facing it.
        Simulation s = sim(OPEN, pinned, lair, pedestal.x() + 24, pedestal.y());
        Creature g = s.quest().guardian();
        int before = g.xFp();
        s.tick(InputState.of(1, 0, true, true));
        SimFixtures.run(s, STILL, SimFixtures.RULES.sabre().windupTicks() + 1);
        assertThat(g.xFp() - before).as("pushed back").isGreaterThanOrEqualTo(Fixed.fp(real.orbit().repelPx() - 2));
        assertThat(g.aux()).as("stunned").isPositive();
        assertThat(g.hurtTicks()).isPositive();
        assertThat(s.quest().inLair()).as("and still there: guardians cannot be killed").isTrue();
        assertThat(s.score()).isZero();
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
    }

    @Test
    void takingAQuarterScoresFlashesAndSendsItToThePanel() {
        QuestRules pinned = pinnedGuardians(80, 0);
        int i = 3;
        LandmarksData.Lair lair = marks.lairs().get(i);
        Simulation s = sim(OPEN, pinned, lairRoom(i), lair.pedestal().x(), lair.pedestal().y());
        s.tick(STILL);
        Quest q = s.quest();
        assertThat(q.taken(i)).isTrue();
        assertThat(q.piecesHeld()).isEqualTo(1);
        assertThat(q.slotMask()).isEqualTo(1 << lair.piece().slot());
        assertThat(s.score()).isEqualTo(real.pieceScore());
        assertThat(q.flyTicks()).isEqualTo(marks.amulet().flyToPanelTicks());
        assertThat(q.flySlot()).isEqualTo(lair.piece().slot());
        for (int t = 0; t < marks.amulet().pickupFlashTicks(); t++) {
            assertThat(s.borderFlash()).as("flash tick %d", t).isEqualTo(Simulation.BorderFlash.PICKUP);
            s.tick(STILL);
        }
        assertThat(s.borderFlash()).isEqualTo(Simulation.BorderFlash.NONE);

        // Out of the lair and back: it stays taken.
        walkY(s, wy(lairRoom(i), 150));
        walkY(s, wy(lairRoom(i), 150) + 192);
        walkY(s, wy(lairRoom(i), lair.pedestal().y()));
        assertThat(s.room()).isEqualTo(lairRoom(i));
        assertThat(q.piecesHeld()).isEqualTo(1);
        assertThat(s.score()).isEqualTo(real.pieceScore());
    }

    @Test
    void theAmuletIsKeptThroughADeath() {
        QuestRules pinned = pinnedGuardians(80, 0);
        LandmarksData.Lair lair = marks.lairs().get(2);
        Simulation s = sim(OPEN, pinned, lairRoom(2), lair.pedestal().x(), lair.pedestal().y());
        s.tick(STILL);
        assertThat(s.kill()).isTrue();
        for (int t = 0; t < 500 && s.player().mode() != Player.Mode.ALIVE; t++) {
            s.tick(STILL);
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(s.quest().piecesHeld()).as("§11.7 keepAmulet").isEqualTo(1);
    }

    @Test
    void theKeeperNudgesYouBackFromTheFrontButKillsFromTheSide() {
        LandmarksData.Point keeper = marks.exit().keeper();
        Simulation front = sim(OPEN, real, marks.exitRoom(), keeper.x(), keeper.y() + 14);
        for (int t = 0; t < 80; t++) {
            front.tick(InputState.of(0, -1, false, false));
            assertThat(front.player().mode()).as("tick %d", t).isEqualTo(Player.Mode.ALIVE);
        }
        assertThat(Fixed.px(front.player().yFp()) - wy(marks.exitRoom(), 0)).as("never got past it")
                .isGreaterThan(keeper.y());

        Simulation side = sim(OPEN, real, marks.exitRoom(), keeper.x() - 28, keeper.y() - 2);
        for (int t = 0; t < 60 && side.player().mode() == Player.Mode.ALIVE; t++) {
            side.tick(InputState.of(1, 0, false, false));
        }
        assertThat(side.player().mode()).isEqualTo(Player.Mode.DYING);
    }

    @Test
    void aShrinePointsTheWayOncePerLife() {
        RoomAddress shrine = LandmarksData.room(marks.caveMouths().get(0));
        LandmarksData.Rect zone = marks.exit().zone();
        Simulation s = sim(OPEN, real, shrine, zone.x() + zone.w() / 2, 160);
        int inZone = wy(shrine, zone.y() + zone.h() - 6);
        walkY(s, inZone);
        Quest q = s.quest();
        assertThat(q.hintTicks()).isPositive();
        assertThat(q.hintToExit()).isFalse();
        // From 7,3 the nearest quarters are 2,7 and 12,7, nine rooms each; the first listed wins: south-west.
        assertThat(q.hintDirection()).isEqualTo(Direction8.SW);

        walkY(s, wy(shrine, 160));
        SimFixtures.run(s, STILL, marks.hint().messageTicks());
        walkY(s, inZone);
        assertThat(q.hintTicks()).as("once per life").isZero();

        s.kill();
        for (int t = 0; t < 500 && s.player().mode() != Player.Mode.ALIVE; t++) {
            s.tick(STILL);
        }
        walkY(s, inZone);
        assertThat(q.hintTicks()).as("a new life, a new hint").isPositive();
    }

    /**
     * The whole quest, scripted on open ground: every quarter, the Keeper stepping aside,
     * the walk into the arch, and the tally's sums (§14.7). Guardians are pinned out of
     * the way; the real ones are the full-run replay's business.
     */
    @Test
    void aWholeAmuletOpensTheWayOutAndTheEscapeIsTallied() {
        QuestRules pinned = pinnedGuardians(80, 0);
        int[] order = {3, 2, 0, 1};
        LandmarksData.Lair first = marks.lairs().get(order[0]);
        Simulation s = sim(OPEN, pinned, lairRoom(order[0]), first.pedestal().x(), first.pedestal().y());
        s.tick(STILL);
        for (int k = 1; k < order.length; k++) {
            RoomAddress here = s.room();
            RoomAddress next = lairRoom(order[k]);
            LandmarksData.Point pedestal = marks.lairs().get(order[k]).pedestal();
            // South of the pedestal, then along a lane clear of pedestals, shrines and the Keeper.
            walkY(s, wy(here, 150));
            walkX(s, wx(here, 60));
            walkY(s, wy(next, 150));
            walkX(s, wx(next, pedestal.x()));
            walkY(s, wy(next, pedestal.y()));
            assertThat(s.quest().taken(order[k])).as("quarter %d", order[k]).isTrue();
        }
        Quest q = s.quest();
        assertThat(q.piecesHeld()).isEqualTo(4);
        assertThat(q.slotMask()).isEqualTo(0b1111);
        assertThat(q.keeperState()).isEqualTo(Quest.Keeper.STEPPING_ASIDE);

        RoomAddress exit = marks.exitRoom();
        LandmarksData.Rect zone = marks.exit().zone();
        walkY(s, wy(s.room(), 150));
        walkX(s, wx(s.room(), 60));
        walkY(s, wy(exit, 170));
        assertThat(q.keeperState()).isEqualTo(Quest.Keeper.ASIDE);
        walkX(s, wx(exit, zone.x() + zone.w() / 2));
        int lives = s.player().lives();
        for (int t = 0; t < 200 && s.player().mode() == Player.Mode.ALIVE; t++) {
            s.tick(InputState.of(0, -1, false, false));
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ESCAPING);
        int x = s.player().xFp();
        s.tick(InputState.of(1, 0, true, true));
        assertThat(s.player().facing()).as("control locked").isEqualTo(Direction8.N);
        SimFixtures.run(s, STILL, marks.exit().escapeWalkTicks());
        assertThat(s.player().mode()).isEqualTo(Player.Mode.WON);
        assertThat(Math.abs(s.player().xFp() - x)).isLessThanOrEqualTo(Fixed.fp(2));

        long before = q.scoreBeforeBonuses();
        assertThat(before).isEqualTo(4L * real.pieceScore());
        assertThat(q.escapeBonus()).isEqualTo(real.escapeBonus());
        assertThat(q.livesBonus()).isEqualTo((long) lives * real.lifeRemainingBonus());
        assertThat(q.timeBonus()).isBetween(0L, (long) real.timeBonusMax());
        assertThat(s.score()).isEqualTo(before + q.escapeBonus() + q.timeBonus() + q.livesBonus());
        long frozen = s.stateHash();
        s.tick(STILL);
        assertThat(s.score()).as("nothing moves once won").isEqualTo(before + q.escapeBonus() + q.timeBonus() + q.livesBonus());
        assertThat(frozen).isNotZero();
    }
}
