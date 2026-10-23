package wulf.sim.ai;

import java.util.List;
import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * FLAME (AGENTS.md §12.7, [CANON], {@code $9D91}, {@code $9E0E}): the flame that comes for a
 * player who lingers. Every {@code steerTicks} it takes a new course — straight at him, a
 * diagonal, {@code homingPer10k} of the time, else one of eight at random — and whatever stops
 * it, it glances off. Its timer counts down in {@code timer}; how long it has left to burn is
 * {@code aux}, which the simulation reads.
 */
public final class Flame implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("steerTicks", "homingPer10k");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        self.timer(0);   // a course at once
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        if (self.timer() <= 0) {
            self.timer(Moves.param(self, "steerTicks"));
            if (ctx.rng().chance(Moves.param(self, "homingPer10k"))) {
                // $9E2F: a diagonal at him, each axis by which side he is on.
                self.direction(ctx.playerCentreXPx() < self.centreXPx() ? -1 : 1,
                        ctx.playerCentreYPx() < self.centreYPx() ? -1 : 1);
            } else {
                Moves.randomDirection(self, ctx.rng());
            }
        }
        self.timer(self.timer() - 1);
        self.aux(self.aux() - 1);
        int blocked = Moves.step(ctx, self, self.dirX(), self.dirY());
        if ((blocked & SimContext.BLOCKED_X) != 0) {
            self.direction(-self.dirX(), self.dirY());
        }
        if ((blocked & SimContext.BLOCKED_Y) != 0) {
            self.direction(self.dirX(), -self.dirY());
        }
    }
}
