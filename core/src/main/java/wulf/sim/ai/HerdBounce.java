package wulf.sim.ai;

import java.util.List;
import wulf.engine.Rng;
import wulf.sim.Creature;
import wulf.sim.Direction8;
import wulf.sim.Herd;

/**
 * HERD_BOUNCE (§12.5): members of a herd share one direction and reflect as one
 * when any of them is blocked. The herd rolls its random about-turn once per
 * tick, not once per member. Members that die leave the rest going.
 */
public final class HerdBounce implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("herdSize", "spreadPx", "reverseChancePer10k");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Herd herd = self.herd();
        if (!herd.hasDirection()) {
            Direction8 d = Direction8.N.rotate(rng.nextInt(8));
            herd.direction(d.dx(), d.dy());
        }
        self.direction(herd.dirX(), herd.dirY());
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        Herd herd = self.herd();
        if (herd.claimRoll(ctx.tick()) && ctx.rng().chance(Moves.param(self, "reverseChancePer10k"))) {
            herd.direction(-herd.dirX(), -herd.dirY());
        }
        int blocked = Moves.step(ctx, self, herd.dirX(), herd.dirY());
        if ((blocked & SimContext.BLOCKED_X) != 0) {
            herd.direction(-herd.dirX(), herd.dirY());
        }
        if ((blocked & SimContext.BLOCKED_Y) != 0) {
            herd.direction(herd.dirX(), -herd.dirY());
        }
        self.direction(herd.dirX(), herd.dirY());
    }
}
