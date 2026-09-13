package wulf.sim.ai;

import java.util.List;
import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * HOP (§12.5): rest for {@code restTicks}, then hop for {@code hopTicks} at its
 * burst speed. Each hop picks a direction afresh, toward the player with
 * probability {@code towardPlayerPer10k}.
 *
 * <p>State: {@code phase} (REST, HOP) and {@code timer}, ticks left in it.
 */
public final class Hop implements Behaviour {

    public static final int REST = 0;
    public static final int HOP = 1;

    @Override
    public List<String> params() {
        return List.of("hopTicks", "restTicks", "towardPlayerPer10k");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Moves.randomDirection(self, rng);
        self.phase(REST);
        self.timer(rng.nextInt(Moves.param(self, "restTicks")));
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        if (self.phase() == REST) {
            if (self.timer() > 0) {
                self.timer(self.timer() - 1);
                return;
            }
            self.phase(HOP);
            self.timer(Moves.param(self, "hopTicks"));
            if (ctx.playerAlive() && ctx.rng().chance(Moves.param(self, "towardPlayerPer10k"))) {
                Moves.towardPlayer(self, ctx);
            } else {
                Moves.randomDirection(self, ctx.rng());
            }
        }
        Moves.step(ctx, self, self.dirX(), self.dirY());
        int t = self.timer() - 1;
        if (t > 0) {
            self.timer(t);
        } else {
            self.phase(REST);
            self.timer(Moves.param(self, "restTicks"));
        }
    }
}
