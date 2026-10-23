package wulf.sim;

import java.nio.file.Path;
import wulf.data.JsonDb;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.CollisionWorld;
import wulf.world.WorldGrid;

/** Shared test worlds and helpers for the simulation tests. */
final class SimFixtures {

    static final PlayerData RULES = new JsonDb(Path.of("data")).load("entities/player", PlayerData.class);

    /** No scenery at all; only the edge of the map is solid. */
    static final CollisionWorld OPEN = (gx, gy) -> gx < 0 || gy < 0 || gx >= WorldGrid.COLS || gy >= WorldGrid.ROWS;

    static final InputState RIGHT = InputState.of(1, 0, false, false);
    static final InputState LEFT = InputState.of(-1, 0, false, false);
    static final InputState DOWN = InputState.of(0, 1, false, false);
    static final InputState DOWN_RIGHT = InputState.of(1, 1, false, false);
    static final InputState UP_LEFT = InputState.of(-1, -1, false, false);
    static final InputState FIRE = InputState.of(0, 0, true, true);
    static final InputState HOLD_FIRE = InputState.of(0, 0, true, false);

    private SimFixtures() {
    }

    /** A player at a world pixel position, with no flip-screen hitch. */
    static Simulation at(CollisionWorld world, int px, int py) {
        return Simulation.at(RULES, world, 0, Fixed.fp(px), Fixed.fp(py));
    }

    static void run(Simulation sim, InputState in, int ticks) {
        for (int i = 0; i < ticks; i++) {
            sim.tick(in);
        }
    }

    /** OPEN plus a solid column of cells at world cell x = wallCx. */
    static CollisionWorld verticalWall(int wallCx) {
        return (gx, gy) -> OPEN.isSolid(gx, gy) || gx == wallCx;
    }

    /** OPEN plus a solid row of cells at world cell y = wallCy. */
    static CollisionWorld horizontalWall(int wallCy) {
        return (gx, gy) -> OPEN.isSolid(gx, gy) || gy == wallCy;
    }
}
