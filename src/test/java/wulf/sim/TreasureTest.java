package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.CreatureSpriteValidator;
import wulf.data.DataException;
import wulf.data.LandmarksData;
import wulf.data.TreasureData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §16.4: the original's treasures — laid at the start, taken by touch, a life now and then. */
class TreasureTest {

    private static final Content C = Content.load(Path.of("data"));
    private static final WorldGrid GRID = new WorldGrid(C.rooms());
    private static final Treasures PLACES = C.treasurePlaces();
    private static final InputState IDLE = InputState.of(0, 0, false, false);

    private static Simulation game(long seed) {
        return Simulation.startingIn(C.player(), GRID, 0, C.map().startRoom(), C.ecosystem(), seed);
    }

    /** A game whose feet start on place {@code i}, laid as {@code seed} lays it. */
    private static Simulation standingOn(int i, long seed) {
        RoomAddress room = new RoomAddress(i / 2 % RoomAddress.GRID_W, i / 2 / RoomAddress.GRID_W);
        return Simulation.at(C.player(), GRID, 0, Fixed.fp(room.col() * Simulation.ROOM_W_PX + PLACES.x()[i]),
                Fixed.fp(room.row() * Simulation.ROOM_H_PX + PLACES.y()[i]), C.ecosystem(), seed);
    }

    /** The first place, in the first seed from 1, that holds a kind that {@code life} says is or is not a life. */
    private static int[] find(boolean life) {
        for (long seed = 1; seed < 50; seed++) {
            Simulation sim = game(seed);
            for (int i = 0; i < 512; i++) {
                int kind = sim.treasureIn(i);
                if (kind >= 0 && PLACES.extraLife()[kind] == life) {
                    return new int[] {i, (int) seed};
                }
            }
        }
        throw new AssertionError("no seed lays one");
    }

    @Test
    void aboutHalfThePlacesHoldSomethingAndTheGameLaysTheSameForTheSameSeed() {
        Simulation a = game(11L);
        Simulation b = game(11L);
        Simulation other = game(12L);
        int places = 0;
        int laid = 0;
        boolean differs = false;
        for (int i = 0; i < 512; i++) {
            places += PLACES.hasPlace(i) ? 1 : 0;
            laid += a.treasureIn(i) >= 0 ? 1 : 0;
            assertThat(b.treasureIn(i)).isEqualTo(a.treasureIn(i));
            differs |= other.treasureIn(i) != a.treasureIn(i);
            if (!PLACES.hasPlace(i)) {
                assertThat(a.treasureIn(i)).as("place %d has no spot", i).isEqualTo(-1);
            }
        }
        assertThat(places).isGreaterThan(450);
        assertThat(laid * 100 / places).isBetween(40, 60);
        assertThat(differs).as("another seed, another jungle").isTrue();
    }

    @Test
    void theQuestsOwnRoomsHoldNoTreasure() {
        LandmarksData marks = C.landmarks();
        List<RoomAddress> kept = new java.util.ArrayList<>(marks.lairRooms());
        kept.add(marks.exitRoom());
        kept.add(LandmarksData.room(marks.items().map().room()));
        for (LandmarksData.Eye eye : marks.items().eyes()) {
            kept.add(LandmarksData.room(eye.room()));
        }
        for (RoomAddress room : kept) {
            for (int place = 0; place < 2; place++) {
                assertThat(PLACES.hasPlace(room.index() * 2 + place)).as("%s", room).isFalse();
            }
        }
    }

    @Test
    void theTwoPlacesOfARoomTakeTheSpotsTheOriginalsCounterPicks() {
        TreasureData data = C.treasures();
        // Room 0,0 is even: spots 0 and 3. Room 1,0 is odd: spots 2 and 1 (§16.4, $A2DC).
        int[][] want = {{0, 0, 0}, {0, 1, 3}, {1, 0, 2}, {1, 1, 1}};
        for (int[] w : want) {
            RoomAddress room = new RoomAddress(w[0], 0);
            int i = room.index() * 2 + w[1];
            if (!PLACES.hasPlace(i)) {
                continue;
            }
            LandmarksData.Point spot = data.slotsByRoomType().get(C.map().roomType(room)).get(w[2]);
            assertThat(PLACES.x()[i]).isEqualTo(spot.x() + data.fromScreen().dx());
            assertThat(PLACES.y()[i]).isEqualTo(spot.y() + data.fromScreen().dy());
        }
    }

    @Test
    void touchingATreasureTakesItAndScoresIt() {
        int[] found = find(false);
        Simulation sim = standingOn(found[0], found[1]);
        long before = sim.score();
        int lives = sim.player().lives();
        sim.play(IDLE, false);
        assertThat(sim.treasureIn(found[0])).as("taken").isEqualTo(-1);
        assertThat(sim.treasuresTaken()).isEqualTo(1);
        assertThat(sim.score() - before).isGreaterThanOrEqualTo(C.treasures().score());
        assertThat(sim.player().lives()).isEqualTo(lives);
    }

    @Test
    void aLifeIsALifeAsWellButNeverMoreThanTheMost() {
        int[] found = find(true);
        Simulation sim = standingOn(found[0], found[1]);
        int lives = sim.player().lives();
        sim.play(IDLE, false);
        assertThat(sim.treasureIn(found[0])).isEqualTo(-1);
        assertThat(sim.player().lives()).isEqualTo(lives + 1);

        Simulation full = standingOn(found[0], found[1]);
        full.startLives(C.player().lives().max());
        full.play(IDLE, false);
        assertThat(full.treasureIn(found[0])).as("taken all the same").isEqualTo(-1);
        assertThat(full.player().lives()).isEqualTo(C.player().lives().max());
    }

    @Test
    void aGameWithNothingTakenHashesAsItDidBeforeTreasures() {
        // Nothing taken: the treasure state stays out of the hash, so older recordings keep theirs.
        Simulation sim = game(5L);
        assertThat(sim.treasuresTaken()).isZero();
        Simulation none = Simulation.startingIn(C.player(), GRID, 0, C.map().startRoom(), withoutTreasures(), 5L);
        assertThat(sim.stateHash()).isEqualTo(none.stateHash());
    }

    private static Ecosystem withoutTreasures() {
        Ecosystem e = C.ecosystem();
        QuestRules q = e.quest();
        QuestRules bare = new QuestRules(q.enabled(), q.landmarks(), q.guardianSpecies(), q.keeperSpecies(), q.orbit(),
                q.stepAsidePx(), q.stepAsideTicks(), q.nudgeZonePx(), q.nudgePx(), q.pieceScore(), q.escapeBonus(),
                q.timeBonusMax(), q.timeBonusTicksDivisor(), q.lifeRemainingBonus(), q.caveHints(), Treasures.NONE);
        return new Ecosystem(e.creatures(), e.populator(), e.roomFirstVisitScore(), e.wulf(), bare, e.orchids());
    }

    @Test
    void aKindMustBeDrawnAndColoured() {
        TreasureData d = C.treasures();
        Map<String, Set<String>> frames = CreatureSpriteValidator.framesBySprite(C.sprites());
        TreasureData badInk = with(d, new TreasureData.Kind("ring", "mauve", "score"));
        assertThatThrownBy(() -> badInk.check(C.map(), C.palette(), frames))
                .isInstanceOf(DataException.class).hasMessageContaining("/kinds/0/ink");
        TreasureData badFrame = with(d, new TreasureData.Kind("anvil", "brightRed", "score"));
        assertThatThrownBy(() -> badFrame.check(C.map(), C.palette(), frames))
                .isInstanceOf(DataException.class).hasMessageContaining("/kinds/0/frame");
        TreasureData short1 = new TreasureData(d.schemaVersion(), d.fidelity(), d.source(), d.fillPercent(),
                d.fillPercentFidelity(), d.score(), d.pickupBox(), d.sprite(), d.fromScreen(), d.kinds(),
                d.slotsByRoomType().subList(0, 47));
        assertThatThrownBy(() -> short1.check(C.map(), C.palette(), frames))
                .isInstanceOf(DataException.class).hasMessageContaining("/slotsByRoomType");
    }

    private static TreasureData with(TreasureData d, TreasureData.Kind first) {
        List<TreasureData.Kind> kinds = new java.util.ArrayList<>(d.kinds());
        kinds.set(0, first);
        return new TreasureData(d.schemaVersion(), d.fidelity(), d.source(), d.fillPercent(), d.fillPercentFidelity(),
                d.score(), d.pickupBox(), d.sprite(), d.fromScreen(), kinds, d.slotsByRoomType());
    }
}
