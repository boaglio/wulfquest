package wulf.sim.ai;

import wulf.data.CreatureData;
import wulf.engine.Rng;
import wulf.sim.Creature;
import wulf.sim.Direction8;

/** Small helpers the behaviours share. */
final class Moves {

    private Moves() {
    }

    static int param(Creature c, String name) {
        return c.species().behaviour().param(name);
    }

    /** One tick in a unit direction at the species' own speed. */
    static int step(SimContext ctx, Creature c, int dx, int dy) {
        CreatureData.Speed s = c.species().speed();
        return ctx.step(c, dx, dy, s.xFp(), s.yFp());
    }

    static void randomDirection(Creature c, Rng rng) {
        Direction8 d = Direction8.N.rotate(rng.nextInt(8));
        c.direction(d.dx(), d.dy());
    }

    static void randomCardinal(Creature c, Rng rng) {
        Direction8 d = Direction8.N.rotate(2 * rng.nextInt(4));
        c.direction(d.dx(), d.dy());
    }

    static int dxToPlayer(Creature c, SimContext ctx) {
        return ctx.playerCentreXPx() - c.centreXPx();
    }

    static int dyToPlayer(Creature c, SimContext ctx) {
        return ctx.playerCentreYPx() - c.centreYPx();
    }

    static void towardPlayer(Creature c, SimContext ctx) {
        Direction8 d = Direction8.toward(dxToPlayer(c, ctx), dyToPlayer(c, ctx));
        c.direction(d.dx(), d.dy());
    }

    static Direction8 heading(Creature c) {
        return Direction8.of(c.dirX(), c.dirY());
    }
}
