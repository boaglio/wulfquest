package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.LandmarksData;
import wulf.data.OrchidData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.effects.EffectKind;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §15 — the orchids: how they grow, and what a bloom does to you. */
class OrchidTest {

    private static final CollisionWorld OPEN = SimFixtures.OPEN;
    private static final InputState STILL = InputState.NONE;
    private static final InputState RIGHT = SimFixtures.RIGHT;

    private static Content content;
    private static OrchidData data;
    private static OrchidField field;

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
        data = content.orchids();
        field = new OrchidField(data);
    }

    /** The shipped orchids, but none growing: for testing an effect without one underfoot. */
    private static OrchidData bare() {
        return new OrchidData(data.schemaVersion(), data.fidelity(), data.sprite(), data.cycle(),
                new OrchidData.Anchors(0, data.anchors().minSpacingPx(), data.anchors().attempts(),
                        data.anchors().insetPx()),
                data.orchids(), data.delirium(), data.immunity());
    }

    private static Simulation sim(CollisionWorld world, RoomAddress room, int localX, int localY, long seed) {
        return sim(world, room, localX, localY, seed, data);
    }

    private static Simulation sim(CollisionWorld world, RoomAddress room, int localX, int localY, long seed,
                                  OrchidData flowers) {
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, RoomPopulator.NONE, 0, WulfRules.NONE, QuestRules.NONE, flowers);
        return Simulation.at(SimFixtures.RULES, world, 0, Fixed.fp(room.col() * Simulation.ROOM_W_PX + localX),
                Fixed.fp(room.row() * Simulation.ROOM_H_PX + localY), eco, seed);
    }

    /** The tick a given anchor's cycle reaches a stage, counting from the run's start. */
    private static int ticksTo(OrchidField.Stage stage) {
        OrchidData.Cycle c = data.cycle();
        return switch (stage) {
            case SEED -> 0;
            case SPROUT -> c.seedTicks();
            case BUD -> c.seedTicks() + c.sproutTicks();
            case BLOOM -> c.seedTicks() + c.sproutTicks() + c.budTicks();
            case WILT -> c.seedTicks() + c.sproutTicks() + c.budTicks() + c.bloomTicks();
        };
    }

    @Test
    void theCycleRunsSeedSproutBudBloomWiltAndRoundAgain() {
        OrchidData.Cycle c = data.cycle();
        assertThat(c.totalTicks()).isEqualTo(680);
        for (OrchidField.Stage stage : OrchidField.Stage.values()) {
            int at = ticksTo(stage);
            assertThat(field.stageAt(at)).as("stage at %d", at).isEqualTo(stage);
            assertThat(field.stageTick(at)).as("stage tick at %d", at).isZero();
        }
        assertThat(field.stageAt(c.totalTicks() - 1)).isEqualTo(OrchidField.Stage.WILT);
        assertThat(field.stageAt(c.totalTicks())).as("round again").isEqualTo(OrchidField.Stage.SEED);
        assertThat(field.phase(1000, 330)).isEqualTo(1000 - 330);
        assertThat(field.phase(1000, 320)).as("a whole cycle later is the start of the next").isZero();
        assertThat(field.phase(10, 20)).as("a cycle that began after this tick still has a phase")
                .isEqualTo(c.totalTicks() - 10);
    }

    @Test
    void aRoomsOrchidsStandApartAndOnClearGround() {
        WorldGrid grid = new WorldGrid(content.rooms());
        OrchidData.Anchors rules = data.anchors();
        int rooms = 0;
        int flowers = 0;
        for (int col = 1; col < 15; col++) {
            for (int row = 1; row < 15; row++) {
                RoomAddress room = new RoomAddress(col, row);
                int[] anchors = field.anchors(room, grid, 7L);
                rooms++;
                flowers += anchors.length / 2;
                assertThat(anchors.length / 2).isLessThanOrEqualTo(rules.perRoom());
                for (int i = 0; i < anchors.length / 2; i++) {
                    int x = anchors[2 * i];
                    int y = anchors[2 * i + 1];
                    assertThat(x).as("%s inset", room).isBetween(rules.insetPx(), Simulation.ROOM_W_PX - rules.insetPx());
                    assertThat(y).as("%s inset", room).isBetween(rules.insetPx(), Simulation.ROOM_H_PX - rules.insetPx());
                    for (int cy = y - 16; cy < y; cy += 4) {
                        for (int cx = x - 8; cx < x + 8; cx += 4) {
                            assertThat(grid.isSolid(Math.floorDiv(room.col() * 256 + cx, 8),
                                    Math.floorDiv(room.row() * 192 + cy, 8))).as("%s bloom clear", room).isFalse();
                        }
                    }
                    for (int j = 0; j < i; j++) {
                        boolean apart = Math.abs(anchors[2 * j] - x) >= rules.minSpacingPx()
                                || Math.abs(anchors[2 * j + 1] - y) >= rules.minSpacingPx();
                        assertThat(apart).as("%s anchors %d and %d apart", room, j, i).isTrue();
                    }
                }
            }
        }
        assertThat(flowers).as("%d flowers over %d rooms", flowers, rooms).isGreaterThan(rooms);
    }

    @Test
    void aBudShowsTheColourItWillBloomIn() {
        RoomAddress room = new RoomAddress(5, 5);
        for (int anchor = 0; anchor < 3; anchor++) {
            long start = field.firstCycleStart(11L, room, anchor);
            int atBud = field.orchidAt(11L, room, anchor, start);
            int atBloom = field.orchidAt(11L, room, anchor, start);
            assertThat(atBud).as("the colour is settled for the whole cycle").isEqualTo(atBloom);
            assertThat(field.orchidAt(11L, room, anchor, start + data.cycle().totalTicks()))
                    .as("but is rolled again next time round").isNotNull();
        }
    }

    @Test
    void standingOnABloomTakesItAndSendsItBackToSeed() {
        RoomAddress room = new RoomAddress(5, 5);
        int[] anchors = field.anchors(room, OPEN, 3L);
        assertThat(anchors).as("an open room grows flowers").isNotEmpty();
        Simulation s = sim(OPEN, room, anchors[0], anchors[1] - 4, 3L);
        long start = field.firstCycleStart(3L, room, 0);
        int which = field.orchidAt(3L, room, 0, start);
        OrchidData.Orchid expected = data.orchid(which);

        int guard = 0;
        while (!s.effect().active() && guard++ < 2000) {
            s.tick(STILL);
        }
        assertThat(s.effect().active()).as("a flower bloomed under his feet").isTrue();
        assertThat(s.effect().orchid()).isEqualTo(which);
        assertThat(s.effect().kind()).isEqualTo(EffectKind.of(expected.effect()));
        assertThat(s.effect().remainingTicks()).isEqualTo(expected.ticks());
        assertThat(s.score()).isEqualTo(expected.score());
        assertThat(field.stageAt(s.orchidPhase(0))).as("picked: back to bare ground").isEqualTo(OrchidField.Stage.SEED);

        // It cannot be taken twice: the score does not climb while he stands there.
        SimFixtures.run(s, STILL, 100);
        assertThat(s.score()).isEqualTo(expected.score());
    }

    @Test
    void aNewBloomReplacesTheOneBefore() {
        Simulation s = sim(OPEN, new RoomAddress(4, 4), 20, 20, 5L, bare());
        assertThat(s.giveEffect("HASTE")).isTrue();
        SimFixtures.run(s, STILL, 20);
        assertThat(s.giveEffect("STILLNESS")).isTrue();
        assertThat(s.effect().kind()).isEqualTo(EffectKind.STILLNESS);
        assertThat(s.effect().remainingTicks()).as("replaced outright, never extended")
                .isEqualTo(data.orchid(data.indexOfEffect("STILLNESS")).ticks());
    }

    private static int walkedRight(Simulation s, int ticks) {
        int from = s.player().xFp();
        SimFixtures.run(s, RIGHT, ticks);
        return s.player().xFp() - from;
    }

    @Test
    void hasteAndTorporChangeTheLegs() {
        int plain = walkedRight(sim(OPEN, new RoomAddress(4, 4), 40, 96, 5L, bare()), 50);
        Simulation fast = sim(OPEN, new RoomAddress(4, 4), 40, 96, 5L, bare());
        fast.giveEffect("HASTE");
        Simulation slow = sim(OPEN, new RoomAddress(4, 4), 40, 96, 5L, bare());
        slow.giveEffect("TORPOR");
        assertThat(walkedRight(fast, 50)).isEqualTo(plain * 2);
        assertThat(walkedRight(slow, 50)).isEqualTo(plain / 2);
    }

    @Test
    void reversalTurnsTheSticksRound() {
        Simulation s = sim(OPEN, new RoomAddress(4, 4), 128, 96, 5L, bare());
        s.giveEffect("REVERSAL");
        int from = s.player().xFp();
        SimFixtures.run(s, RIGHT, 20);
        assertThat(s.player().xFp()).as("right is left").isLessThan(from);
        assertThat(s.player().facing()).isEqualTo(Direction8.W);
    }

    @Test
    void deliriumTakesTheLegsNowAndAgain() {
        Simulation s = sim(OPEN, new RoomAddress(4, 4), 128, 96, 5L, bare());
        s.giveEffect("DELIRIUM");
        int own = 0;
        int stolen = 0;
        for (int t = 0; t < 300; t++) {
            int x = s.player().xFp();
            int y = s.player().yFp();
            s.tick(RIGHT);
            boolean asAsked = s.player().xFp() > x && s.player().yFp() == y;
            if (asAsked) {
                own++;
            } else {
                stolen++;
            }
        }
        assertThat(stolen).as("his legs go their own way").isPositive();
        assertThat(own).as("but not always").isPositive();
    }

    @Test
    void immunityStopsContactKillingButNotTheClock() {
        Simulation s;
        RoomAddress room = EcoFixtures.ROOM;
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, (spawner, at, seed, visit) -> {
            if (at.equals(room)) {
                spawner.spawn("tribesman", 128, 100, Herd.NONE, new wulf.engine.Rng(4));
            }
        }, 0, WulfRules.NONE, QuestRules.NONE, bare());
        Simulation bare = Simulation.at(SimFixtures.RULES, OPEN, 0, Fixed.fp(room.col() * 256 + 128),
                Fixed.fp(room.row() * 192 + 100), eco, 5L);
        SimFixtures.run(bare, STILL, 60);
        assertThat(bare.player().mode()).as("without it, a touch kills").isNotEqualTo(Player.Mode.ALIVE);

        Simulation immune = Simulation.at(SimFixtures.RULES, OPEN, 0, Fixed.fp(room.col() * 256 + 128),
                Fixed.fp(room.row() * 192 + 100), eco, 5L);
        assertThat(immune.giveEffect("IMMUNITY")).isTrue();
        int ticks = data.orchid(data.indexOfEffect("IMMUNITY")).ticks();
        SimFixtures.run(immune, STILL, ticks - 1);
        assertThat(immune.player().mode()).as("nothing can touch him").isEqualTo(Player.Mode.ALIVE);
        assertThat(immune.effect().active()).isTrue();
        s = immune;
        s.tick(STILL);
        assertThat(s.effect().active()).as("and then it wears off").isFalse();
    }

    @Test
    void stillnessHoldsTheRoomButNotTheWulf() {
        RoomAddress room = EcoFixtures.ROOM;
        RoomPopulator one = (spawner, at, seed, visit) -> {
            if (at.equals(room)) {
                spawner.spawn("tribesman", 200, 40, Herd.NONE, new wulf.engine.Rng(4));
            }
        };
        wulf.data.WulfData wulfData = new wulf.data.JsonDb(Path.of("data")).load("entities/wulf", wulf.data.WulfData.class);
        Ecosystem eco = new Ecosystem(EcoFixtures.CREATURES, one, 0,
                WulfRules.of(wulfData, 12, 250, java.util.Set.of()), QuestRules.NONE, bare());
        Simulation s = Simulation.at(SimFixtures.RULES, OPEN, 0, Fixed.fp(room.col() * 256 + 40),
                Fixed.fp(room.row() * 192 + 150), eco, 5L);
        assertThat(s.summonWulf()).isTrue();
        SimFixtures.run(s, STILL, wulfData.appearance().warningTicks() + 5);
        assertThat(s.giveEffect("STILLNESS")).isTrue();
        Creature creature = s.creatures().get(0);
        int cx = creature.xFp();
        int cy = creature.yFp();
        int wx = s.wulf().body().xFp();
        int wy = s.wulf().body().yFp();
        SimFixtures.run(s, STILL, 40);
        assertThat(creature.xFp()).as("the room stands still").isEqualTo(cx);
        assertThat(creature.yFp()).isEqualTo(cy);
        assertThat(s.wulf().body().xFp() != wx || s.wulf().body().yFp() != wy).as("the Wulf does not").isTrue();
    }

    @Test
    void anEffectWearsOffAndDeathClearsIt() {
        Simulation s = sim(OPEN, new RoomAddress(4, 4), 128, 96, 5L, bare());
        s.giveEffect("REVERSAL");
        int ticks = data.orchid(data.indexOfEffect("REVERSAL")).ticks();
        SimFixtures.run(s, STILL, ticks - 1);
        assertThat(s.effect().active()).isTrue();
        assertThat(s.effect().remainingPerMille()).isBetween(0, 1000);
        s.tick(STILL);
        assertThat(s.effect().active()).as("it runs out").isFalse();

        s.giveEffect("HASTE");
        assertThat(s.kill()).isTrue();
        assertThat(s.effect().active()).as("death clears it").isFalse();
    }

    @Test
    void onlyTheRoomYouAreInCostsAnything() {
        RoomAddress room = new RoomAddress(5, 5);
        Simulation s = sim(OPEN, room, 20, 20, 3L);
        int flowers = s.orchidAnchors().length / 2;
        assertThat(flowers).isPositive();
        long before = s.orchidStagesRead();
        SimFixtures.run(s, STILL, 1000);
        assertThat(s.orchidStagesRead() - before).as("one look per flower per tick, and no more")
                .isEqualTo(1000L * flowers);
    }

    @Test
    void lairsAndTheWayOutDoNotFlower() {
        WorldGrid grid = new WorldGrid(content.rooms());
        Ecosystem eco = content.ecosystem();
        for (RoomAddress lair : content.landmarks().lairRooms()) {
            Simulation s = Simulation.startingIn(content.player(), grid, 6, lair, eco, 9L);
            assertThat(s.orchidAnchors()).as("%s is a clean puzzle", lair).isEmpty();
        }
        Simulation exit = Simulation.startingIn(content.player(), grid, 6, content.landmarks().exitRoom(), eco, 9L);
        assertThat(exit.orchidAnchors()).isEmpty();

        List<Integer> counts = new ArrayList<>();
        for (String mouth : content.landmarks().caveMouths()) {
            Simulation s = Simulation.startingIn(content.player(), grid, 6, LandmarksData.room(mouth), eco, 9L);
            counts.add(s.orchidAnchors().length / 2);
        }
        assertThat(counts).as("ordinary rooms do flower").anyMatch(n -> n > 0);
    }
}
