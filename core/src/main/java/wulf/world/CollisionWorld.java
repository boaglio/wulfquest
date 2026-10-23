package wulf.world;

/**
 * What the simulation asks of the world: is this cell solid? (AGENTS.md §7.4)
 *
 * <p>Cells are world cells — 8x8 px, 512x384 across the whole map — and anything
 * outside the map must report solid. {@link WorldGrid} is the real one; tests
 * pass lambdas to build open fields and walls.
 */
@FunctionalInterface
public interface CollisionWorld {

    boolean isSolid(int gx, int gy);
}
