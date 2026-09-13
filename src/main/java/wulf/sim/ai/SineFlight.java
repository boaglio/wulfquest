package wulf.sim.ai;

import java.util.List;
import wulf.data.CreatureData;
import wulf.engine.Rng;
import wulf.engine.SinTable;
import wulf.sim.Creature;
import wulf.sim.Simulation;

/**
 * SINE_FLIGHT (§12.5): a straight horizontal course at its speed, with a
 * vertical offset from the integer sine table of {@code amplitudePx} over
 * {@code periodTicks}. Ignores scenery; bounces only off the room's edges.
 *
 * <p>State: {@code timer} is the phase within the period; the anchor is the
 * centre line of the wave.
 */
public final class SineFlight implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("amplitudePx", "periodTicks");
    }

    @Override
    public int preferredLocalYPx(CreatureData.Species species, int proposedLocalYPx) {
        int amplitude = species.behaviour().param("amplitudePx");
        int highest = -species.collisionBox().y() + amplitude;   // the crest keeps the box inside the room
        int lowest = Simulation.ROOM_H_PX - amplitude;
        return Math.max(highest, Math.min(lowest, proposedLocalYPx));
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        self.direction(rng.nextInt(2) == 0 ? 1 : -1, 0);
        self.anchor(self.xFp(), self.yFp());
        self.timer(rng.nextInt(Moves.param(self, "periodTicks")));
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        int period = Moves.param(self, "periodTicks");
        int t = (self.timer() + 1) % period;
        self.timer(t);
        // amplitude in px times sin in 8.8 is exactly the offset in 8.8.
        int targetY = self.anchorYFp() + Moves.param(self, "amplitudePx") * SinTable.sin(t * 256 / period);
        int blocked = ctx.move(self, self.dirX() * self.species().speed().xFp(), targetY - self.yFp());
        if ((blocked & SimContext.BLOCKED_X) != 0) {
            self.direction(-self.dirX(), 0);
        }
    }
}
