package wulf.sim.ai;

import java.util.List;
import wulf.data.CreatureData;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.sim.Creature;

/**
 * DROP_THREAD (§12.5): hangs from the top of the room. When the player passes
 * beneath within {@code triggerPx}, it drops up to {@code dropMaxPx}, pauses
 * {@code pauseTicks}, and climbs back at {@code ascendScaleFp} of its speed. The
 * renderer draws its thread from the anchor.
 *
 * <p>State: {@code phase} (IDLE, DROP, PAUSE, CLIMB), {@code timer} for the
 * pause; the anchor is where it hangs.
 */
public final class DropThread implements Behaviour {

    public static final int IDLE = 0;
    public static final int DROP = 1;
    public static final int PAUSE = 2;
    public static final int CLIMB = 3;

    @Override
    public List<String> params() {
        return List.of("triggerPx", "dropMaxPx", "pauseTicks", "ascendScaleFp");
    }

    @Override
    public int preferredLocalYPx(CreatureData.Species species, int proposedLocalYPx) {
        return species.size().h() + 1;
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        self.anchor(self.xFp(), self.yFp());
        self.phase(IDLE);
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        switch (self.phase()) {
            case IDLE -> {
                if (ctx.playerAlive() && Math.abs(Moves.dxToPlayer(self, ctx)) <= Moves.param(self, "triggerPx")
                        && Moves.dyToPlayer(self, ctx) > 0) {
                    self.phase(DROP);
                }
            }
            case DROP -> {
                int blocked = ctx.move(self, 0, self.species().speed().yFp());
                if ((blocked & SimContext.BLOCKED_Y) != 0
                        || self.yFp() - self.anchorYFp() >= Fixed.fp(Moves.param(self, "dropMaxPx"))) {
                    self.phase(PAUSE);
                    self.timer(Moves.param(self, "pauseTicks"));
                }
            }
            case PAUSE -> {
                int t = self.timer() - 1;
                self.timer(t);
                if (t <= 0) {
                    self.phase(CLIMB);
                }
            }
            default -> {
                int climb = Math.max(1, Fixed.mul(self.species().speed().yFp(), Moves.param(self, "ascendScaleFp")));
                ctx.move(self, 0, -climb);
                if (self.yFp() <= self.anchorYFp()) {
                    self.place(self.xFp(), self.anchorYFp());
                    self.phase(IDLE);
                }
            }
        }
    }
}
