package wulf.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bound view of {@code data/entities/flame.json} (AGENTS.md §12.7, [CANON]): the flame the
 * original sends after a player who lingers in a room ({@code $9ABC}, {@code $9D91}).
 *
 * @param lingerTicks  how long Vale may stay in a room before it comes; 0, never
 * @param lifeTicks    how long it burns
 * @param steerTicks   how often it picks a new course
 * @param homingPer10k the chance a new course heads straight for him; else one of eight at random
 * @param spawnInsetPx how near the room's edges it may appear
 */
public record FlameData(int schemaVersion, String fidelity, String id, String displayName, String sprite,
                        CreatureData.Size size, CreatureData.Box collisionBox, CreatureData.Speed speed,
                        int lingerTicks, int lifeTicks, int steerTicks, int homingPer10k, int spawnInsetPx) {

    /** The behaviour that steers it (§12.5). */
    public static final String BEHAVIOUR = "FLAME";

    /** No flame: most tests, and a jungle that lets you stand still. */
    public static final FlameData NONE = new FlameData(1, "none", "flame", "The Flame", "flame",
            new CreatureData.Size(1, 1), new CreatureData.Box(0, 0, 1, 1), new CreatureData.Speed(0, 0), 0, 1, 1, 0, 0);

    public boolean comes() {
        return lingerTicks > 0;
    }

    /**
     * The flame as a creature: {@code hp} 0, which the sabre cannot take (§12.2) — it cannot be
     * killed or held off, only outrun or left behind.
     */
    public CreatureData.Species asSpecies() {
        Map<String, Integer> params = new LinkedHashMap<>();
        params.put("steerTicks", steerTicks);
        params.put("homingPer10k", homingPer10k);
        return new CreatureData.Species(id, displayName, sprite, size, collisionBox, speed, 0, 0, false,
                new CreatureData.Behaviour(BEHAVIOUR, params));
    }
}
