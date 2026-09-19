package wulf.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bound view of {@code data/entities/guardians.json} (AGENTS.md §14.3, §14.4): the
 * four unkillable lair beasts and the Keeper of the Arch. Integers only.
 */
public record GuardianData(int schemaVersion, String fidelity, Orbit orbit, List<Guardian> guardians, Keeper keeper) {

    public static final String GUARD_ORBIT = "GUARD_ORBIT";
    public static final String BLOCK_STATIC = "BLOCK_STATIC";

    public GuardianData {
        guardians = List.copyOf(guardians);
    }

    /**
     * @param lungePx  how near the player must come for it to break orbit
     * @param repelPx  how far a swing pushes it: the sabre never kills a guardian (§14.3)
     */
    public record Orbit(int radiusPx, int ticksPerRev, int lungePx, int lungeTicks, int repelPx, int stunTicks,
                        int hurtFlashTicks) {
    }

    /**
     * @param basedOn the roster species it is a larger sibling of, so the player knows the family (§14.3)
     * @param colour  its palette accent, which matches its amulet quarter
     */
    public record Guardian(String id, String displayName, String sprite, String basedOn, String colour,
                           CreatureData.Size size, CreatureData.Box collisionBox, CreatureData.Speed speed) {
    }

    /** @param stepAsidePx how far it moves aside once the amulet is whole (§14.4) */
    public record Keeper(String id, String displayName, String sprite, CreatureData.Size size,
                         CreatureData.Box collisionBox, int stepAsidePx) {
    }

    public Guardian guardian(String id) {
        for (Guardian g : guardians) {
            if (g.id().equals(id)) {
                return g;
            }
        }
        throw new IllegalStateException("no guardian '" + id + "' in data/entities/guardians.json");
    }

    /** A guardian as a species the GUARD_ORBIT behaviour can steer. */
    public CreatureData.Species asSpecies(Guardian g) {
        Map<String, Integer> params = new LinkedHashMap<>();
        params.put("orbitRadiusPx", orbit.radiusPx());
        params.put("orbitTicksPerRev", orbit.ticksPerRev());
        params.put("lungePx", orbit.lungePx());
        params.put("lungeTicks", orbit.lungeTicks());
        return new CreatureData.Species(g.id(), g.displayName(), g.sprite(), g.size(), g.collisionBox(), g.speed(), 1, 0,
                false, new CreatureData.Behaviour(GUARD_ORBIT, params));
    }

    /** The Keeper as a species: it never moves, and BLOCK_STATIC takes no parameters. */
    public CreatureData.Species keeperSpecies() {
        return new CreatureData.Species(keeper.id(), keeper.displayName(), keeper.sprite(), keeper.size(),
                keeper.collisionBox(), new CreatureData.Speed(0, 0), 1, 0, false,
                new CreatureData.Behaviour(BLOCK_STATIC, new LinkedHashMap<>()));
    }
}
