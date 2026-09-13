package wulf.sim.ai;

import java.util.List;
import wulf.data.CreatureData;
import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * How one kind of creature moves (AGENTS.md §12.5). Implementations are
 * stateless singletons: all per-creature state lives on the {@link Creature}.
 */
public interface Behaviour {

    /** The integer parameters this behaviour reads from creatures.json; checked at load. */
    List<String> params();

    /** Lets a behaviour adjust a proposed spawn height — spiders hang from the top of the room. */
    default int preferredLocalYPx(CreatureData.Species species, int proposedLocalYPx) {
        return proposedLocalYPx;
    }

    /** Starting state, drawn from the room's own random stream so a room replays exactly. */
    default void spawn(Creature self, SimContext ctx, Rng rng) {
    }

    void tick(Creature self, SimContext ctx);
}
