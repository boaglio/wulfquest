package wulf.sim.ai;

import java.util.List;
import wulf.sim.Creature;

/**
 * BLOCK_STATIC (AGENTS.md §12.5, §14.4): never moves. The Keeper of the Arch is an
 * impassable, lethal volume until the amulet is whole, and the simulation — not this
 * behaviour — walks it aside then (§14.4).
 */
public final class BlockStatic implements Behaviour {

    @Override
    public List<String> params() {
        return List.of();
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        // Nothing. It stands.
    }
}
