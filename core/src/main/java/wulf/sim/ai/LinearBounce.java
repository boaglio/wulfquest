package wulf.sim.ai;

import java.util.List;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * LINEAR_BOUNCE (§12.5): a random 8-direction at spawn, straight lines, reflect
 * the blocked axis, and an occasional random about-turn. The original's
 * mindless patrol.
 */
public final class LinearBounce implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("reverseChancePer10k");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Moves.randomDirection(self, rng);
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        bounce(self, ctx, Moves.param(self, "reverseChancePer10k"));
    }

    static void bounce(Creature c, SimContext ctx, int reverseChancePer10k) {
        if (ctx.rng().chance(reverseChancePer10k)) {
            c.direction(-c.dirX(), -c.dirY());
        }
        int blocked = ctx.step(c, c.dirX(), c.dirY(), Fixed.mul(c.species().speed().xFp(), Fixed.ONE),
                c.species().speed().yFp());
        if ((blocked & SimContext.BLOCKED_X) != 0) {
            c.direction(-c.dirX(), c.dirY());
        }
        if ((blocked & SimContext.BLOCKED_Y) != 0) {
            c.direction(c.dirX(), -c.dirY());
        }
    }
}
