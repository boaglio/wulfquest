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
 * @param roaming   §13.3–§13.5, {@code features.roamingWulf} [NEW]: it appears anywhere and follows the
 *                  player. Off, it is the original's (§13.8): one Wulf prowling {@code patrolRoom}'s row
 * @param neverIn   only ever asked {@code contains} — never iterated, so its order cannot leak into the sim (§6.4)
 * @param neverInLairs whether it also keeps out of this game's lairs, wherever the game hid the amulet (§14.2)
 */
public record WulfRules(boolean enabled, boolean roaming, WulfData data, CreatureData.Species species,
                        int arrivalDelayTicks, int evadedScore, Set<RoomAddress> neverIn, boolean neverInLairs,
                        RoomAddress patrolRoom) {

    /** No Wulf: M4's jungle, and most tests. */
    public static final WulfRules NONE = new WulfRules(false, true, WulfData.NONE, WulfData.NONE.asSpecies(), 0, 0,
            Set.of(), false, new RoomAddress(0, 0));

    public WulfRules {
        neverIn = Set.copyOf(neverIn);
    }

    /** The roaming Wulf [NEW] (§13.3–§13.5), free of the lairs. */
    public static WulfRules of(WulfData data, int arrivalDelayTicks, int evadedScore, Set<RoomAddress> neverIn) {
        return of(data, arrivalDelayTicks, evadedScore, neverIn, false);
    }

    /** The roaming Wulf [NEW] (§13.3–§13.5). */
    public static WulfRules of(WulfData data, int arrivalDelayTicks, int evadedScore, Set<RoomAddress> neverIn,
                               boolean neverInLairs) {
        return new WulfRules(true, true, data, data.asSpecies(), arrivalDelayTicks, evadedScore, neverIn,
                neverInLairs, new RoomAddress(0, 0));
    }

    /** The original's Wulf (§13.8), starting where {@code wulf.json → patrol.room} says. */
    public static WulfRules patrol(WulfData data) {
        return new WulfRules(true, false, data, data.asSpecies(), 0, 0, Set.of(), false,
                RoomAddress.parse(data.patrol().room()));
    }
}
