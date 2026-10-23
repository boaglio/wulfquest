package wulf.sim.ai;

import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * Everything a behaviour may see and do (AGENTS.md §12.5): the player, the room,
 * the simulation's random stream and the tick. Never the renderer, the audio, or
 * the wall clock.
 */
public interface SimContext {

    int BLOCKED_X = 1;
    int BLOCKED_Y = 2;

    Rng rng();

    long tick();

    boolean playerAlive();

    int playerCentreXPx();

    int playerCentreYPx();

    int roomLeftPx();

    int roomTopPx();

    /**
     * Moves by a fixed-point delta, axis-separated, against the room's edges and —
     * unless the species ignores it — its scenery.
     *
     * @return {@link #BLOCKED_X} and/or {@link #BLOCKED_Y} for each axis that was stopped short
     */
    int move(Creature self, int dxFp, int dyFp);

    /** One tick in a unit direction at the given speeds, diagonals scaled. */
    int step(Creature self, int dx, int dy, int speedXFp, int speedYFp);

    /** Whether the creature's box would be blocked if offset by whole pixels. */
    boolean probe(Creature self, int dxPx, int dyPx);

    /** Whether a straight line between two world points crosses no solid cell (§12.5, AMBUSH_BURST). */
    boolean lineClear(int x0Px, int y0Px, int x1Px, int y1Px);

    void throwSpear(Creature from, int dx, int dy);
}
