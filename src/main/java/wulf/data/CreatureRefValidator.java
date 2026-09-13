package wulf.data;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The {@code CreatureRefValidator} of AGENTS.md §20.6: every creature a room or
 * biome names exists, every biome the map resolves to is populated, budgets are
 * sane, authored placements are real rooms, and the biome markers are real
 * scenery objects.
 */
public final class CreatureRefValidator {

    private static final String CREATURES = "data/entities/creatures.json";
    private static final String ROOMS = "data/world/room_entities.json";

    private CreatureRefValidator() {
    }

    public static void check(CreatureData creatures, RoomEntitiesData rooms, Collection<String> biomesInUse,
                             Set<String> sceneryIds) {
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < creatures.creatures().size(); i++) {
            String id = creatures.creatures().get(i).id();
            if (!ids.add(id)) {
                throw new DataException(CREATURES, "/creatures/" + i + "/id", "repeats the creature id '" + id + "'");
            }
        }

        RoomEntitiesData.BiomeMarkers m = rooms.biomeMarkers();
        for (Map.Entry<String, String> marker : Map.of("hut", m.hut(), "water", m.water(), "swamp", m.swamp()).entrySet()) {
            if (!sceneryIds.contains(marker.getValue())) {
                throw new DataException(ROOMS, "/biomeMarkers/" + marker.getKey(),
                        "is '" + marker.getValue() + "', which is not a scenery object in data/world/scenery.json");
            }
        }

        for (String biome : biomesInUse) {
            if (!rooms.biomes().containsKey(biome)) {
                throw new DataException(ROOMS, "/biomes",
                        "has no entry for biome '" + biome + "', which rooms on the map resolve to");
            }
        }

        for (Map.Entry<String, RoomEntitiesData.Biome> e : rooms.biomes().entrySet()) {
            String at = "/biomes/" + e.getKey();
            RoomEntitiesData.Biome b = e.getValue();
            if (b.budget().min() > b.budget().max()) {
                throw new DataException(ROOMS, at + "/budget",
                        "has min " + b.budget().min() + " above max " + b.budget().max());
            }
            if (b.budget().max() > 0 && b.weights().isEmpty()) {
                throw new DataException(ROOMS, at + "/weights", "is empty but the budget allows creatures");
            }
            for (String id : b.weights().keySet()) {
                requireCreature(creatures, id, at + "/weights/" + id);
            }
            List<RoomEntitiesData.Extra> extras = b.extras();
            for (int i = 0; i < extras.size(); i++) {
                requireCreature(creatures, extras.get(i).id(), at + "/extras/" + i + "/id");
            }
        }

        for (Map.Entry<String, RoomEntitiesData.AuthoredRoom> e : rooms.authored().entrySet()) {
            String at = "/authored/" + e.getKey();
            try {
                wulf.world.RoomAddress.parse(e.getKey());
            } catch (IllegalArgumentException bad) {
                throw new DataException(ROOMS, at, "is not a room on the 16x16 map");
            }
            List<RoomEntitiesData.Placed> placed = e.getValue().creatures();
            for (int i = 0; i < placed.size(); i++) {
                RoomEntitiesData.Placed p = placed.get(i);
                requireCreature(creatures, p.id(), at + "/creatures/" + i + "/id");
                if (p.x() < 0 || p.x() > 256 || p.y() < 0 || p.y() > 192) {
                    throw new DataException(ROOMS, at + "/creatures/" + i,
                            "places " + p.id() + " at " + p.x() + "," + p.y() + ", outside the 256x192 room");
                }
            }
        }
    }

    private static void requireCreature(CreatureData creatures, String id, String pointer) {
        if (!creatures.has(id)) {
            throw new DataException(ROOMS, pointer, "names creature '" + id + "', which is not in creatures.json");
        }
    }
}
