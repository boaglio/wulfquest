package wulf.sim.ai;

import java.util.List;
import wulf.sim.Creature;

/**
 * HOME_IN (AGENTS.md §12.8, [CANON], {@code $A4D7}): the original's bat, lizard and snake.
 * Each axis leans towards the player by {@code accelFp} a tick, up to {@code maxFp}, and stops
 * dead when level with him; so it swings in, overshoots, and comes round again. The room's edges
 * turn it about; the scenery it slides along.
 *
 * <p>{@code phase} and {@code aux} are the velocity, x and y, in 8.8 per tick.
 */
public final class HomeIn implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("accelFp", "maxFp");
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        int accel = Moves.param(self, "accelFp");
        int max = Moves.param(self, "maxFp");
        self.phase(lean(self.phase(), Moves.dxToPlayer(self, ctx), accel, max));
        self.aux(lean(self.aux(), Moves.dyToPlayer(self, ctx), accel, max));
        self.face(Integer.signum(self.phase()));   // $A4C3
        Dart.glance(self, ctx);
        ctx.move(self, self.phase(), self.aux());
    }

    /** {@code $A4F3}: level with him, that axis stops; otherwise it leans his way, clamped ({@code $AFD0}). */
    private static int lean(int v, int gap, int accel, int max) {
        if (gap == 0) {
            return 0;
        }
        return Math.max(-max, Math.min(max, v + Integer.signum(gap) * accel));
    }
}
