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
                m.lairs(), m.amulet(), m.caveMouths(), m.hint(), m.stillWater());
    }

    private static LandmarksData withLairRoom(int i, String room) {
        LandmarksData m = c.landmarks();
        List<LandmarksData.Lair> lairs = new ArrayList<>(m.lairs());
        LandmarksData.Lair l = lairs.get(i);
        lairs.set(i, new LandmarksData.Lair(l.id(), room, l.guardian(), l.piece(), l.pedestal()));
        return new LandmarksData(m.schemaVersion(), m.fidelity(), m.startFacing(), m.exit(), lairs, m.amulet(),
                m.caveMouths(), m.hint(), m.stillWater());
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
    void twoLairsInOneQuadrantAreReported() {
        DataException e = landmarkError(withLairRoom(1, "3,6"), solid);
        assertThat(e.pointer()).isEqualTo("/lairs/1/room");
    }

    @Test
    void aPedestalInsideTheSceneryIsReported() {
        RoomAddress first = LandmarksData.room(c.landmarks().lairs().get(0).room());
        LandmarkValidator.Solid walledLair = (gx, gy) -> solid.at(gx, gy)
                || (gx / CollisionMask.COLS == first.col() && gy / CollisionMask.ROWS == first.row());
        DataException e = landmarkError(c.landmarks(), walledLair);
        assertThat(e.pointer()).isEqualTo("/lairs/0/pedestal");
    }
}
