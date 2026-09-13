package wulf.sim.ai;

import java.util.List;
import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * PATROL_THROW (§12.5): LINEAR_BOUNCE, plus a spear along its heading every
 * {@code throwPeriodTicks} — but only when the player is within range and within
 * the cone ahead ({@code throwConeCos10k} is the cone's half-angle cosine, x10000).
 */
public final class PatrolThrow implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("reverseChancePer10k", "throwPeriodTicks", "throwRangePx", "throwConeCos10k");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Moves.randomDirection(self, rng);
        self.timer(rng.nextInt(Moves.param(self, "throwPeriodTicks")));   // stagger a room's throwers
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        LinearBounce.bounce(self, ctx, Moves.param(self, "reverseChancePer10k"));
        int t = self.timer() + 1;
        if (t >= Moves.param(self, "throwPeriodTicks")) {
            t = 0;
            if (ctx.playerAlive() && inCone(self, ctx)) {
                ctx.throwSpear(self, self.dirX(), self.dirY());
            }
        }
        self.timer(t);
    }

    /** Within range and within the cone, in integers: dot^2 >= cos^2 * |d|^2 * |f|^2. */
    static boolean inCone(Creature c, SimContext ctx) {
        long dx = Moves.dxToPlayer(c, ctx);
        long dy = Moves.dyToPlayer(c, ctx);
        long dist2 = dx * dx + dy * dy;
        long range = Moves.param(c, "throwRangePx");
        if (dist2 > range * range) {
            return false;
        }
        long dot = dx * c.dirX() + dy * c.dirY();
        if (dot <= 0) {
            return false;
        }
        long heading2 = (long) c.dirX() * c.dirX() + (long) c.dirY() * c.dirY();
        long cos = Moves.param(c, "throwConeCos10k");
        return dot * dot * 100_000_000L >= cos * cos * dist2 * heading2;
    }
}
