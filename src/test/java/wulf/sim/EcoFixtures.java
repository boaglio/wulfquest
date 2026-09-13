package wulf.sim;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import wulf.data.CreatureData;
import wulf.data.JsonDb;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/** Worlds, rosters and hand-placed creatures for the M4 behaviour and combat tests. */
final class EcoFixtures {

    static final PlayerData RULES = SimFixtures.RULES;
    static final CreatureData CREATURES = new JsonDb(Path.of("data")).load("entities/creatures", CreatureData.class);
    static final CollisionWorld OPEN = SimFixtures.OPEN;

    /** Every hand-placed test happens in this room. */
    static final RoomAddress ROOM = new RoomAddress(3, 5);
    static final int ROOM_X = ROOM.col() * Simulation.ROOM_W_PX;
    static final int ROOM_Y = ROOM.row() * Simulation.ROOM_H_PX;
    static final int CELL_X = ROOM.col() * 32;
    static final int CELL_Y = ROOM.row() * 24;

    static final CreatureData.Speed STILL = new CreatureData.Speed(0, 0);

    /** A pen of cells round a player standing at room-local (20, 180): nothing outside can reach in. */
    static final CollisionWorld FENCED = fenced(1, 20, 4, 23);

    record Spot(String id, int x, int y) {
    }

    private EcoFixtures() {
    }

    /** The player at room-local (playerX, playerY) in {@link #ROOM}, with exactly these creatures there. */
    static Simulation with(CollisionWorld world, int playerX, int playerY, CreatureData roster, Spot... spots) {
        RoomPopulator populator = (spawner, room, seed, visit) -> {
            if (!room.equals(ROOM)) {
                return;
            }
            Rng rng = new Rng(7);
            Herd herd = spawner.newHerd();
            for (Spot s : spots) {
                boolean herded = roster.species(s.id()).behaviour().kind().equals("HERD_BOUNCE");
                spawner.spawn(s.id(), s.x(), s.y(), herded ? herd : Herd.NONE, rng);
            }
        };
        return Simulation.at(RULES, world, 0, Fixed.fp(ROOM_X + playerX), Fixed.fp(ROOM_Y + playerY),
                new Ecosystem(roster, populator, 0), 99L);
    }

    static CreatureData tuned(String id, Map<String, Integer> params) {
        return tuned(id, params, null, -1);
    }

    static CreatureData tuned(String id, Map<String, Integer> params, CreatureData.Speed speed) {
        return tuned(id, params, speed, -1);
    }

    /** The shipped roster with one species' parameters merged, and optionally its speed and score replaced. */
    static CreatureData tuned(String id, Map<String, Integer> params, CreatureData.Speed speed, int score) {
        List<CreatureData.Species> roster = new ArrayList<>();
        for (CreatureData.Species s : CREATURES.creatures()) {
            if (!s.id().equals(id)) {
                roster.add(s);
                continue;
            }
            Map<String, Integer> merged = new LinkedHashMap<>(s.behaviour().params());
            merged.putAll(params);
            roster.add(new CreatureData.Species(s.id(), s.displayName(), s.sprite(), s.size(), s.collisionBox(),
                    speed == null ? s.speed() : speed, s.hp(), score < 0 ? s.score() : score, s.ignoresScenery(),
                    new CreatureData.Behaviour(s.behaviour().kind(), merged)));
        }
        return new CreatureData(CREATURES.schemaVersion(), CREATURES.fidelity(), CREATURES.diagonalScaleFp(),
                CREATURES.puffTicks(), CREATURES.hurtFlashTicks(), CREATURES.walkTicksPerFrame(), roster, CREATURES.projectiles());
    }

    /** OPEN plus solid room-local cell rectangles, each {x0, y0, x1, y1} inclusive. */
    static CollisionWorld walls(int[]... rects) {
        return (gx, gy) -> {
            if (OPEN.isSolid(gx, gy)) {
                return true;
            }
            for (int[] r : rects) {
                if (gx >= CELL_X + r[0] && gx <= CELL_X + r[2] && gy >= CELL_Y + r[1] && gy <= CELL_Y + r[3]) {
                    return true;
                }
            }
            return false;
        };
    }

    /** OPEN plus a one-cell ring round room-local cells [x0..x1] x [y0..y1]. */
    static CollisionWorld fenced(int x0, int y0, int x1, int y1) {
        return (gx, gy) -> {
            if (OPEN.isSolid(gx, gy)) {
                return true;
            }
            int lx = gx - CELL_X;
            int ly = gy - CELL_Y;
            boolean inRing = lx >= x0 - 1 && lx <= x1 + 1 && ly >= y0 - 1 && ly <= y1 + 1;
            boolean inside = lx >= x0 && lx <= x1 && ly >= y0 && ly <= y1;
            return inRing && !inside;
        };
    }

    static CollisionWorld either(CollisionWorld a, CollisionWorld b) {
        return (gx, gy) -> a.isSolid(gx, gy) || b.isSolid(gx, gy);
    }

    static Creature only(Simulation s) {
        if (s.creatures().size() != 1) {
            throw new AssertionError("expected exactly one creature, found " + s.creatures().size());
        }
        return s.creatures().get(0);
    }

    static int lx(Creature c) {
        return Fixed.px(c.xFp()) - ROOM_X;
    }

    static int ly(Creature c) {
        return Fixed.px(c.yFp()) - ROOM_Y;
    }

    static void run(Simulation s, int ticks) {
        for (int i = 0; i < ticks; i++) {
            s.tick(wulf.input.InputState.NONE);
        }
    }
}
