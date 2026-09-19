package wulf.sim.ai;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import wulf.data.CreatureData;
import wulf.data.DataException;

/**
 * Behaviour kinds by name (AGENTS.md §12.5), and the load-time check that every
 * species names a known kind with exactly the parameters it reads.
 *
 * <p>GUARD_ORBIT and BLOCK_STATIC belong to the guardians and the Keeper (§14), which
 * are not roster species: {@code guardians.json} names them, so no creature declares
 * them and {@link #validate} never sees them.
 */
public final class BehaviourCatalog {

    private static final String SOURCE = "data/entities/creatures.json";

    private static final Map<String, Behaviour> KINDS = Map.ofEntries(
            Map.entry("LINEAR_BOUNCE", new LinearBounce()),
            Map.entry("PATROL_THROW", new PatrolThrow()),
            Map.entry("CHASE_AXIS", new ChaseAxis()),
            Map.entry("CHASE_DIRECT", new ChaseDirect()),
            Map.entry("WANDER_ERRATIC", new WanderErratic()),
            Map.entry("WALL_FOLLOW", new WallFollow()),
            Map.entry("DROP_THREAD", new DropThread()),
            Map.entry("SINE_FLIGHT", new SineFlight()),
            Map.entry("HOP", new Hop()),
            Map.entry("AMBUSH_BURST", new AmbushBurst()),
            Map.entry("HERD_BOUNCE", new HerdBounce()),
            Map.entry("GUARD_ORBIT", new GuardOrbit()),
            Map.entry("BLOCK_STATIC", new BlockStatic()));

    private BehaviourCatalog() {
    }

    public static Behaviour of(String kind) {
        Behaviour b = KINDS.get(kind);
        if (b == null) {
            throw new IllegalStateException("no behaviour kind '" + kind + "'");
        }
        return b;
    }

    public static Set<String> kinds() {
        return new TreeSet<>(KINDS.keySet());
    }

    public static void validate(CreatureData data) {
        List<CreatureData.Species> roster = data.creatures();
        for (int i = 0; i < roster.size(); i++) {
            CreatureData.Behaviour declared = roster.get(i).behaviour();
            String at = "/creatures/" + i + "/behaviour";
            Behaviour kind = KINDS.get(declared.kind());
            if (kind == null) {
                throw new DataException(SOURCE, at + "/kind",
                        "is '" + declared.kind() + "', which is not one of " + kinds());
            }
            for (String needed : kind.params()) {
                if (!declared.params().containsKey(needed)) {
                    throw new DataException(SOURCE, at + "/params",
                            "is missing '" + needed + "', which " + declared.kind() + " needs");
                }
            }
            for (String given : declared.params().keySet()) {
                if (!kind.params().contains(given)) {
                    throw new DataException(SOURCE, at + "/params/" + given,
                            "is not a parameter of " + declared.kind() + ", which takes " + kind.params());
                }
            }
        }
    }
}
