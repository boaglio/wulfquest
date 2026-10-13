package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.world.CollisionMask;
import wulf.world.RoomAddress;

/** AGENTS.md §14, §20.6 — what guardians.json and landmarks.json must get right beyond their schemas. */
class QuestDataValidationTest {

    private static Content c;
    private static Map<String, Set<String>> frames;
    private static LandmarkValidator.Solid solid;

    @BeforeAll
    static void load() {
        c = Content.load(Path.of("data"));
        frames = CreatureSpriteValidator.framesBySprite(c.sprites());
        solid = (gx, gy) -> gx < 0 || gy < 0 || gx >= RoomAddress.GRID_W * CollisionMask.COLS
                || gy >= RoomAddress.GRID_H * CollisionMask.ROWS
                || c.rooms().room(new RoomAddress(gx / CollisionMask.COLS, gy / CollisionMask.ROWS))
                        .mask().isSolid(gx % CollisionMask.COLS, gy % CollisionMask.ROWS);
    }

    private static GuardianData withFirstGuardian(String basedOn, String colour) {
        GuardianData g = c.guardians();
        List<GuardianData.Guardian> all = new ArrayList<>(g.guardians());
        GuardianData.Guardian f = all.get(0);
        all.set(0, new GuardianData.Guardian(f.id(), f.displayName(), f.sprite(), basedOn, colour, f.size(),
                f.collisionBox(), f.speed()));
        return new GuardianData(g.schemaVersion(), g.fidelity(), g.orbit(), all, g.keeper());
    }

    private static DataException guardianError(GuardianData g, Map<String, Set<String>> f) {
        return catchThrowableOfType(DataException.class, () -> GuardianValidator.check(g, c.creatures(), c.palette(), f));
    }

    private static DataException landmarkError(LandmarksData m, LandmarkValidator.Solid s) {
        return catchThrowableOfType(DataException.class, () -> LandmarkValidator.check(m, c.guardians(), c.creatures(),
                c.map(), c.scenery().ids(), frames, s));
    }

    private static LandmarksData withExit(String room) {
        LandmarksData m = c.landmarks();
        LandmarksData.Exit e = m.exit();
        return new LandmarksData(m.schemaVersion(), m.fidelity(), m.startFacing(),
                new LandmarksData.Exit(room, e.arch(), e.zone(), e.keeper(), e.requiresPieces(), e.escapeWalkTicks()),
                m.lairs(), m.amuletSets(), m.amulet(), m.shrines(), m.items(), m.hint(), m.temple());
    }

    /** The shipped landmarks with entry {@code j} of amulet set {@code k} moved to {@code room}. */
    private static LandmarksData withSetRoom(int k, int j, String room) {
        LandmarksData m = c.landmarks();
        LandmarksData.AmuletSets a = m.amuletSets();
        List<List<String>> sets = new ArrayList<>(a.sets());
        List<String> set = new ArrayList<>(sets.get(k));
        set.set(j, room);
        sets.set(k, set);
        Map<String, LandmarksData.Pedestal> pedestals = new java.util.HashMap<>(a.pedestals());
        pedestals.put(room, new LandmarksData.Pedestal(128, 96, null));
        return new LandmarksData(m.schemaVersion(), m.fidelity(), m.startFacing(), m.exit(), m.lairs(),
                new LandmarksData.AmuletSets(a.source(), a.quarters(), sets, pedestals), m.amulet(),
                m.shrines(), m.items(), m.hint(), m.temple());
    }

    private static LandmarksData withGlass(LandmarksData.Glass glass) {
        LandmarksData m = c.landmarks();
        LandmarksData.Items i = m.items();
        return new LandmarksData(m.schemaVersion(), m.fidelity(), m.startFacing(), m.exit(), m.lairs(), m.amuletSets(),
                m.amulet(), m.shrines(), new LandmarksData.Items(i.pickupBox(), i.glanceTicks(), i.map(), i.eyes(), glass),
                m.hint(), m.temple());
    }

    @Test
    void aLookingGlassLiesOnClearGroundInARoomOfItsOwn() {
        // §14.10: not where a set may hide a quarter, not an eye's room, not in the scenery, its frames drawn.
        LandmarksData.Glass g = c.landmarks().items().glass();
        LandmarksData.Place first = g.places().get(0);
        String lairRoom = c.landmarks().amuletSets().sets().get(0).get(0);
        assertThat(landmarkError(withGlass(new LandmarksData.Glass(g.sprite(), g.showWayTicks(), g.warnTicks(), g.arrow(),
                List.of(new LandmarksData.Place(lairRoom, first.spot()), g.places().get(1)))), solid).pointer())
                .isEqualTo("/items/glass/places/0/room");
        String eyeRoom = c.landmarks().items().eyes().get(0).room();
        assertThat(landmarkError(withGlass(new LandmarksData.Glass(g.sprite(), g.showWayTicks(), g.warnTicks(), g.arrow(),
                List.of(first, new LandmarksData.Place(eyeRoom, first.spot())))), solid).pointer())
                .isEqualTo("/items/glass/places/1/room");
        assertThat(landmarkError(withGlass(new LandmarksData.Glass(g.sprite(), g.showWayTicks(), g.warnTicks(), g.arrow(),
                List.of(first, first))), solid).pointer()).isEqualTo("/items/glass/places/1/room");
        RoomAddress at = LandmarksData.room(first.room());
        LandmarkValidator.Solid overgrown = (gx, gy) -> gx / CollisionMask.COLS == at.col() && gy / CollisionMask.ROWS == at.row()
                || solid.at(gx, gy);
        assertThat(landmarkError(withGlass(g), overgrown).pointer()).isEqualTo("/items/glass/places/0/spot");
        assertThat(landmarkError(withGlass(new LandmarksData.Glass(g.sprite(), g.showWayTicks(), g.warnTicks(),
                new LandmarksData.Arrow("item_eye", 1, 1, 0), g.places())), solid).pointer())
                .isEqualTo("/items/glass/arrow/sprite");
        assertThat(landmarkError(withGlass(new LandmarksData.Glass(g.sprite(), 10, 10, g.arrow(), g.places())), solid)
                .pointer()).isEqualTo("/items/glass/warnTicks");
    }

    @Test
    void anEyeMustShowRealLairsAndTogetherShowThemAll() {
        // §14.8: an eye naming a lair that does not exist, or the eyes missing one, is a DATA ERROR.
        LandmarksData m = c.landmarks();
        LandmarksData.Items items = m.items();
        LandmarksData.Eye first = items.eyes().get(0);
        LandmarksData.Items wrong = new LandmarksData.Items(items.pickupBox(), items.glanceTicks(), items.map(), List.of(
                new LandmarksData.Eye(first.room(), first.spot(), first.sprite(), List.of("lair_nowhere")),
                items.eyes().get(1)));
        LandmarksData broken = new LandmarksData(m.schemaVersion(), m.fidelity(), m.startFacing(), m.exit(), m.lairs(),
                m.amuletSets(), m.amulet(), m.shrines(), wrong, m.hint(), m.temple());
        assertThat(landmarkError(broken, solid).pointer()).isEqualTo("/items/eyes/0/reveals/0");

        LandmarksData.Items short1 = new LandmarksData.Items(items.pickupBox(), items.glanceTicks(), items.map(),
                List.of(first));
        LandmarksData missing = new LandmarksData(m.schemaVersion(), m.fidelity(), m.startFacing(), m.exit(), m.lairs(),
                m.amuletSets(), m.amulet(), m.shrines(), short1, m.hint(), m.temple());
        assertThat(landmarkError(missing, solid).pointer()).isEqualTo("/items/eyes");
    }

    @Test
    void theShippedQuestDataIsValid() {
        GuardianValidator.check(c.guardians(), c.creatures(), c.palette(), frames);
        LandmarkValidator.check(c.landmarks(), c.guardians(), c.creatures(), c.map(), c.scenery().ids(), frames, solid);
    }

    @Test
    void aGuardianOfNoKnownFamilyOrColourIsReported() {
        assertThat(guardianError(withFirstGuardian("dragon", "brightGreen"), frames).pointer()).isEqualTo("/guardians/0/basedOn");
        assertThat(guardianError(withFirstGuardian("hippo", "chartreuse"), frames).pointer()).isEqualTo("/guardians/0/colour");
    }

    @Test
    void aKeeperWithoutItsStepAsideFramesIsReported() {
        Map<String, Set<String>> missing = CreatureSpriteValidator.framesBySprite(c.sprites());
        missing.get(c.guardians().keeper().sprite()).remove("step1");
        DataException e = guardianError(c.guardians(), missing);
        assertThat(e.pointer()).isEqualTo("/keeper/sprite");
        assertThat(e.detail()).contains("step1");
    }

    @Test
    void aWayOutWithNoArchIsReported() {
        DataException e = landmarkError(withExit("9,10"), solid);
        assertThat(e.pointer()).isEqualTo("/exit/room");
    }

    @Test
    void aQuarterHiddenInTheWayOutIsReported() {
        DataException e = landmarkError(withSetRoom(2, 1, c.landmarks().exit().room()), solid);
        assertThat(e.pointer()).isEqualTo("/amuletSets/sets/2/1");
    }

    @Test
    void aPedestalInsideTheSceneryIsReported() {
        String key = c.landmarks().amuletSets().sets().get(0).get(0);
        RoomAddress first = LandmarksData.room(key);
        LandmarkValidator.Solid walledLair = (gx, gy) -> solid.at(gx, gy)
                || (gx / CollisionMask.COLS == first.col() && gy / CollisionMask.ROWS == first.row());
        DataException e = landmarkError(c.landmarks(), walledLair);
        assertThat(e.pointer()).isEqualTo("/amuletSets/pedestals/" + key);
    }

    @Test
    void everySetLaysEveryQuarterInARoomOfItsOwn() {
        // §14.2, $A29D: the eight sets, each four rooms, every lair given one.
        LandmarksData m = c.landmarks();
        assertThat(m.amuletSets().sets()).hasSize(8);
        assertThat(m.placed()).as("as loaded, no set is picked").isFalse();
        for (int k = 0; k < 8; k++) {
            LandmarksData laid = m.withAmuletSet(k);
            assertThat(laid.placed()).isTrue();
            assertThat(laid.amuletSet()).isEqualTo(k);
            assertThat(laid.lairRooms()).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(m.amuletSets().sets().get(k).stream().map(LandmarksData::room).toList());
        }
    }
}
