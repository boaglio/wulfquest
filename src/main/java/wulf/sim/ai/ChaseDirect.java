package wulf.sim.ai;

import java.util.List;
import wulf.engine.Rng;
import wulf.sim.Creature;
import wulf.sim.Direction8;

/**
 * CHASE_DIRECT (§12.5): head for the player along the 8-direction that best
 * matches the gap, turning at most one 45-degree step per {@code turnCooldownTicks}.
 * Blocked: try the two neighbouring directions, then stall for {@code stallTicks}.
 * Never pathfinds — the stupidity is the design.
 *
 * <p>State: {@code timer} is the turn cooldown, {@code aux} the stall countdown.
 */
public final class ChaseDirect implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("turnCooldownTicks", "stallTicks");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Moves.randomDirection(self, rng);
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        if (self.aux() > 0) {
            self.aux(self.aux() - 1);
            return;
        }
        if (!ctx.playerAlive()) {
            return;
        }
        Direction8 heading = Moves.heading(self);
        Direction8 want = Direction8.toward(Moves.dxToPlayer(self, ctx), Moves.dyToPlayer(self, ctx));
        if (self.timer() > 0) {
            self.timer(self.timer() - 1);
        } else if (want != heading) {
            heading = turnToward(heading, want);
            self.direction(heading.dx(), heading.dy());
            self.timer(Moves.param(self, "turnCooldownTicks"));
        }
        if (moved(ctx, self, heading)) {
            return;
        }
        if (!moved(ctx, self, heading.rotate(1)) && !moved(ctx, self, heading.rotate(-1))) {
            self.aux(Moves.param(self, "stallTicks"));
        }
    }

    /** One 45-degree step the short way round. */
    static Direction8 turnToward(Direction8 from, Direction8 to) {
        int diff = Math.floorMod(to.ordinal() - from.ordinal(), 8);
        if (diff == 0) {
            return from;
        }
        return from.rotate(diff <= 4 ? 1 : -1);
    }

    private static boolean moved(SimContext ctx, Creature c, Direction8 d) {
        int x = c.xFp();
        int y = c.yFp();
        Moves.step(ctx, c, d.dx(), d.dy());
        return c.xFp() != x || c.yFp() != y;
    }
}
