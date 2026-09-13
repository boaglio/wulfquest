package wulf.sim.ai;

import java.util.List;
import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * WANDER_ERRATIC (§12.5): a new random direction every
 * {@code rerollMinTicks + rng(rerollRandomTicks)} ticks, and at once on hitting
 * anything. Never targets the player; lethal by accident.
 *
 * <p>State: {@code timer} counts down to the next re-roll.
 */
public final class WanderErratic implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("rerollMinTicks", "rerollRandomTicks");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Moves.randomDirection(self, rng);
        self.timer(Moves.param(self, "rerollMinTicks") + rng.nextInt(Moves.param(self, "rerollRandomTicks")));
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        int t = self.timer() - 1;
        if (t <= 0) {
            Moves.randomDirection(self, ctx.rng());
            t = Moves.param(self, "rerollMinTicks") + ctx.rng().nextInt(Moves.param(self, "rerollRandomTicks"));
        }
        self.timer(t);
        if (Moves.step(ctx, self, self.dirX(), self.dirY()) != 0) {
            Moves.randomDirection(self, ctx.rng());
        }
    }
}
