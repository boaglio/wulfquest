package wulf.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bound view of {@code data/world/room_entities.json} (AGENTS.md §12.6, §7.6). */
public record RoomEntitiesData(
        int schemaVersion,
        String fidelity,
        Spawn spawn,
        BiomeMarkers biomeMarkers,
        Map<String, AuthoredRoom> authored,
        Map<String, Biome> biomes) {

    public RoomEntitiesData {
        // Order-preserving: weighted picks walk these maps, and Map.copyOf's order is randomised per JVM.
        authored = Collections.unmodifiableMap(new LinkedHashMap<>(authored));
        biomes = Collections.unmodifiableMap(new LinkedHashMap<>(biomes));
    }

    /**
     * @param visitSeedCap visits beyond this reuse the last seed, so long sessions do
     *                     not drift into unbounded variety (§7.6)
     */
    public record Spawn(int minDistanceFromEntryPx, int attempts, int visitSeedCap) {
    }

    /** The scenery objects whose presence decides a room's biome (§12.6). */
    public record BiomeMarkers(String hut, String water, String swamp) {
    }

    public record AuthoredRoom(String note, List<Placed> creatures) {
        public AuthoredRoom {
            creatures = List.copyOf(creatures);
        }
    }

    /** A creature at a room-local feet position, in pixels. */
    public record Placed(String id, int x, int y) {
    }

    public record Biome(Budget budget, Map<String, Integer> weights, List<Extra> extras) {
        public Biome {
            weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
            extras = List.copyOf(extras);
        }
    }

    public record Budget(int min, int max) {
    }

    /** Placed in addition to the budget, with a chance: e.g. one chief per hut room, 35%. */
    public record Extra(String id, int chancePer10k) {
    }
}
