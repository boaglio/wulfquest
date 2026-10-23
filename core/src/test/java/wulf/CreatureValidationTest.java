package wulf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import wulf.data.CreatureData;
import wulf.data.CreatureRefValidator;
import wulf.data.DataException;
import wulf.data.RoomEntitiesData;
import wulf.sim.ai.BehaviourCatalog;

/** AGENTS.md §20.6 — creature and room-population data fails loudly, with a pointer. */
class CreatureValidationTest {

    private static final Content C = Content.load(Path.of("data"));

    private static CreatureData withFirstBehaviour(CreatureData.Behaviour behaviour) {
        List<CreatureData.Species> roster = new ArrayList<>(C.creatures().creatures());
        CreatureData.Species s = roster.get(0);
        roster.set(0, new CreatureData.Species(s.id(), s.displayName(), s.sprite(), s.size(), s.collisionBox(),
                s.speed(), s.hp(), s.score(), s.ignoresScenery(), behaviour));
        CreatureData d = C.creatures();
        return new CreatureData(d.schemaVersion(), d.fidelity(), d.diagonalScaleFp(), d.puffTicks(),
                d.hurtFlashTicks(), d.walkTicksPerFrame(), roster, d.projectiles());
    }

    private static RoomEntitiesData rooms(Map<String, RoomEntitiesData.Biome> biomes,
                                          Map<String, RoomEntitiesData.AuthoredRoom> authored,
                                          RoomEntitiesData.BiomeMarkers markers) {
        RoomEntitiesData r = C.roomEntities();
        return new RoomEntitiesData(r.schemaVersion(), r.fidelity(), r.spawn(), markers, authored, biomes);
    }

    private static DataException refFailure(RoomEntitiesData rooms) {
        DataException e = catchThrowableOfType(DataException.class,
                () -> CreatureRefValidator.check(C.creatures(), rooms, C.biomes().inUse(), C.scenery().ids()));
        assertThat(e).as("expected a DataException").isNotNull();
        return e;
    }

    @Test
    void theShippedDataIsValid() {
        BehaviourCatalog.validate(C.creatures());
        CreatureRefValidator.check(C.creatures(), C.roomEntities(), C.biomes().inUse(), C.scenery().ids());
    }

    @Test
    void anUnknownBehaviourKindIsReported() {
        DataException e = catchThrowableOfType(DataException.class, () -> BehaviourCatalog.validate(
                withFirstBehaviour(new CreatureData.Behaviour("TELEPORT", Map.of()))));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/creatures/0/behaviour/kind");
    }

    @Test
    void aMissingParameterIsReported() {
        DataException e = catchThrowableOfType(DataException.class, () -> BehaviourCatalog.validate(
                withFirstBehaviour(new CreatureData.Behaviour("LINEAR_BOUNCE", Map.of()))));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/creatures/0/behaviour/params");
        assertThat(e.detail()).contains("reverseChancePer10k");
    }

    @Test
    void anUnknownParameterIsReported() {
        DataException e = catchThrowableOfType(DataException.class, () -> BehaviourCatalog.validate(
                withFirstBehaviour(new CreatureData.Behaviour("LINEAR_BOUNCE",
                        Map.of("reverseChancePer10k", 40, "bogus", 1)))));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/creatures/0/behaviour/params/bogus");
    }

    @Test
    void aWeightNamingNoCreatureIsReported() {
        Map<String, RoomEntitiesData.Biome> biomes = new LinkedHashMap<>(C.roomEntities().biomes());
        RoomEntitiesData.Biome jungle = biomes.get("jungle");
        Map<String, Integer> weights = new LinkedHashMap<>(jungle.weights());
        weights.put("dragon", 5);
        biomes.put("jungle", new RoomEntitiesData.Biome(jungle.budget(), weights, jungle.extras()));
        DataException e = refFailure(rooms(biomes, C.roomEntities().authored(), C.roomEntities().biomeMarkers()));
        assertThat(e.pointer()).isEqualTo("/biomes/jungle/weights/dragon");
    }

    @Test
    void aBiomeTheMapUsesButNothingPopulatesIsReported() {
        Map<String, RoomEntitiesData.Biome> biomes = new LinkedHashMap<>(C.roomEntities().biomes());
        biomes.remove("water");
        DataException e = refFailure(rooms(biomes, C.roomEntities().authored(), C.roomEntities().biomeMarkers()));
        assertThat(e.pointer()).isEqualTo("/biomes");
        assertThat(e.detail()).contains("water");
    }

    @Test
    void aMarkerThatIsNotSceneryIsReported() {
        RoomEntitiesData.BiomeMarkers m = C.roomEntities().biomeMarkers();
        DataException e = refFailure(rooms(C.roomEntities().biomes(), C.roomEntities().authored(),
                new RoomEntitiesData.BiomeMarkers("FFFF", m.water(), m.swamp())));
        assertThat(e.pointer()).isEqualTo("/biomeMarkers/hut");
    }

    @Test
    void anAuthoredRoomOffTheMapIsReported() {
        Map<String, RoomEntitiesData.AuthoredRoom> authored = new LinkedHashMap<>(C.roomEntities().authored());
        authored.put("16,3", new RoomEntitiesData.AuthoredRoom("nowhere", List.of()));
        DataException e = refFailure(rooms(C.roomEntities().biomes(), authored, C.roomEntities().biomeMarkers()));
        assertThat(e.pointer()).isEqualTo("/authored/16,3");
    }
}
