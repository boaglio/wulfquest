package wulf.sim;

import java.util.List;
import java.util.Map;
import wulf.data.CreatureData;
import wulf.data.GameConfig;
import wulf.data.RoomEntitiesData;
import wulf.engine.Rng;
import wulf.world.BiomeResolver;
import wulf.world.RoomAddress;

/**
 * The shipped room populator (AGENTS.md §12.6): an authored list for rooms that
 * have one, otherwise a budget of weighted picks for the room's biome, plus
 * chance-based extras. Everything is drawn from a room seed
 * {@code hash(runSeed, col, row, visit)}, so a room's contents replay exactly.
 */
public final class BiomePopulator implements RoomPopulator {

    private static final String HERD = "HERD_BOUNCE";

    private final CreatureData creatures;
    private final RoomEntitiesData rooms;
    private final BiomeResolver biomes;
    private final GameConfig.Difficulty difficulty;

    public BiomePopulator(CreatureData creatures, RoomEntitiesData rooms, BiomeResolver biomes,
                          GameConfig.Difficulty difficulty) {
        this.creatures = creatures;
        this.rooms = rooms;
        this.biomes = biomes;
        this.difficulty = difficulty;
    }

    @Override
    public void populate(Spawner spawner, RoomAddress room, long runSeed, int visit) {
        Rng rng = new Rng(Rng.hash(runSeed, room.col(), room.row(), Math.min(visit, rooms.spawn().visitSeedCap())));
        RoomEntitiesData.AuthoredRoom authored = rooms.authored().get(room.toString());
        if (authored != null) {
            for (RoomEntitiesData.Placed p : authored.creatures()) {
                spawner.spawn(p.id(), p.x(), p.y(), herdFor(p.id(), spawner), rng);
            }
            return;
        }
        RoomEntitiesData.Biome biome = rooms.biomes().get(biomes.biomeOf(room));
        List<Integer> bonus = difficulty.budgetBonusByPieces();
        int pieces = Math.max(0, Math.min(spawner.amuletPieces(), bonus.size() - 1));
        int span = biome.budget().max() - biome.budget().min() + 1;
        int count = Math.min(difficulty.maxRoomBudget(), biome.budget().min() + rng.nextInt(span) + bonus.get(pieces));
        for (int i = 0; i < count; i++) {
            place(spawner, pick(biome.weights(), rng), rng);
        }
        for (RoomEntitiesData.Extra extra : biome.extras()) {
            if (rng.chance(extra.chancePer10k())) {
                place(spawner, extra.id(), rng);
            }
        }
    }

    /** A weighted pick, walking the weights in file order. */
    static String pick(Map<String, Integer> weights, Rng rng) {
        int total = 0;
        for (int w : weights.values()) {
            total += w;
        }
        int roll = rng.nextInt(total);
        for (Map.Entry<String, Integer> e : weights.entrySet()) {
            roll -= e.getValue();
            if (roll < 0) {
                return e.getKey();
            }
        }
        throw new IllegalStateException("weighted pick fell through");
    }

    private void place(Spawner spawner, String id, Rng rng) {
        CreatureData.Species species = creatures.species(id);
        boolean herded = species.behaviour().kind().equals(HERD);
        for (int attempt = 0; attempt < rooms.spawn().attempts(); attempt++) {
            int x = rng.nextInt(Simulation.ROOM_W_PX);
            int y = spawner.preferredLocalYPx(id, 1 + rng.nextInt(Simulation.ROOM_H_PX));
            if (!farEnough(spawner, x, y) || !spawner.reachable(id, x, y)) {
                continue;
            }
            if (!herded) {
                if (spawner.spawn(id, x, y, Herd.NONE, rng)) {
                    return;
                }
                continue;
            }
            Herd herd = spawner.newHerd();
            if (spawner.spawn(id, x, y, herd, rng)) {
                int size = species.behaviour().param("herdSize");
                int spread = species.behaviour().param("spreadPx");
                for (int m = 1; m < size; m++) {
                    // A loose triangle behind the leader; a member that will not fit is left out.
                    int mx = x + (m % 2 == 1 ? -spread : spread);
                    int my = y + spread * ((m + 1) / 2);
                    if (farEnough(spawner, mx, my) && spawner.reachable(id, mx, my)) {
                        spawner.spawn(id, mx, my, herd, rng);
                    }
                }
                return;
            }
        }
    }

    private boolean farEnough(Spawner spawner, int x, int y) {
        long dx = x - spawner.entryLocalXPx();
        long dy = y - spawner.entryLocalYPx();
        long min = rooms.spawn().minDistanceFromEntryPx();
        return dx * dx + dy * dy >= min * min;
    }

    private Herd herdFor(String id, Spawner spawner) {
        return creatures.species(id).behaviour().kind().equals(HERD) ? spawner.newHerd() : Herd.NONE;
    }
}
