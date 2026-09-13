package wulf.sim;

import java.util.Set;
import wulf.data.CreatureData;
import wulf.data.WulfData;
import wulf.world.RoomAddress;

/**
 * Everything the simulation needs to run the Wulf (AGENTS.md §13): its data, its
 * body as a species, the arrival delay after a flip, what an escape scores, and the
 * rooms it never appears in, already resolved to addresses.
 *
 * @param neverIn only ever asked {@code contains} — never iterated, so its order cannot leak into the sim (§6.4)
 */
public record WulfRules(boolean enabled, WulfData data, CreatureData.Species species, int arrivalDelayTicks,
                        int evadedScore, Set<RoomAddress> neverIn) {

    /** No Wulf: M4's jungle, and most tests. */
    public static final WulfRules NONE = new WulfRules(false, WulfData.NONE, WulfData.NONE.asSpecies(), 0, 0, Set.of());

    public WulfRules {
        neverIn = Set.copyOf(neverIn);
    }

    public static WulfRules of(WulfData data, int arrivalDelayTicks, int evadedScore, Set<RoomAddress> neverIn) {
        return new WulfRules(true, data, data.asSpecies(), arrivalDelayTicks, evadedScore, neverIn);
    }
}
