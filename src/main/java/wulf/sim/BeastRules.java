package wulf.sim;

import java.util.List;
import wulf.data.CreatureData;
import wulf.world.RoomAddress;

/**
 * Everything the simulation needs to run the original's big beasts (AGENTS.md §12.9), resolved:
 * the kinds as species, each room's spot from its type, and the actor table's placings in our
 * world numbers.
 *
 * @param spotXPx  by {@link RoomAddress#index()}: the original's x of the spot — a sprite's left edge
 * @param spotYPx  by room: our room-local feet y of the spot
 * @param sleepers the kinds a sleeper may wake as, by index into {@code kinds}
 */
public record BeastRules(boolean on, int followRows, int followColumns, List<CreatureData.Species> kinds, List<Beast.Kind> kindOf,
                         int[] spotXPx, int[] spotYPx, int[] sleepers, List<Placing> placings) {

    /** No beasts: the invented jungle, and most tests. */
    public static final BeastRules NONE = new BeastRules(false, 1, 1, List.of(), List.of(), new int[0], new int[0],
            new int[0], List.of());

    public BeastRules {
        kinds = List.copyOf(kinds);
        kindOf = List.copyOf(kindOf);
        spotXPx = spotXPx.clone();
        spotYPx = spotYPx.clone();
        sleepers = sleepers.clone();
        placings = List.copyOf(placings);
    }

    /**
     * One beast as a new game sets it down.
     *
     * @param kind  an index into {@code kinds}
     * @param xPx   world feet x
     * @param yPx   world feet y
     * @param tint  a palette index
     */
    public record Placing(int kind, RoomAddress room, int xPx, int yPx, int heading, int tint, boolean asleep) {
    }

    /** The paces a beast goes at on its own: what Easy mode slows (§17.8). A shove is not one. */
    private static final List<String> PACES = List.of("paceFp", "walkSlowFp", "walkFastFp", "chargeFp");

    /**
     * The same beasts at {@code percent} of their own pace (§17.8), never slower than a step
     * a tick; how hard a blade shoves them, where they stand and how they follow are the same.
     */
    public BeastRules slower(int percent) {
        List<CreatureData.Species> slowed = new java.util.ArrayList<>(kinds.size());
        for (CreatureData.Species s : kinds) {
            CreatureData.Species k = s;
            for (String p : PACES) {
                Integer v = s.behaviour().params().get(p);
                if (v != null) {
                    k = k.withParam(p, Math.max(1, v * percent / 100));
                }
            }
            slowed.add(k);
        }
        return new BeastRules(on, followRows, followColumns, slowed, kindOf, spotXPx, spotYPx, sleepers, placings);
    }

    /** A behaviour parameter of kind {@code k}. */
    int param(int k, String name) {
        return kinds.get(k).behaviour().param(name);
    }
}
