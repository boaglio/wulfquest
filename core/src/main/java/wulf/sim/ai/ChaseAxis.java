package wulf.sim.ai;

import java.util.List;
import wulf.data.CreatureData;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * CHASE_AXIS (§12.5): full speed along the axis with the larger gap to the
 * player, {@code minorAxisScaleFp} of speed on the other. Determined, dumb, and
 * bad at corners.
 */
public final class ChaseAxis implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("minorAxisScaleFp");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Moves.randomDirection(self, rng);
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        if (!ctx.playerAlive()) {
            return;
        }
        int dx = Moves.dxToPlayer(self, ctx);
        int dy = Moves.dyToPlayer(self, ctx);
        int sx = Math.abs(dx) >= 2 ? Integer.signum(dx) : 0;   // a 2 px dead band: no jitter on top of the target
        int sy = Math.abs(dy) >= 2 ? Integer.signum(dy) : 0;
        CreatureData.Speed s = self.species().speed();
        int minor = Moves.param(self, "minorAxisScaleFp");
        int vx;
        int vy;
        if (Math.abs(dx) >= Math.abs(dy)) {
            vx = sx * s.xFp();
            vy = sy * Fixed.mul(s.yFp(), minor);
        } else {
            vx = sx * Fixed.mul(s.xFp(), minor);
            vy = sy * s.yFp();
        }
        if (sx != 0 || sy != 0) {
            self.direction(sx, sy);
        }
        ctx.move(self, vx, vy);
    }
}
