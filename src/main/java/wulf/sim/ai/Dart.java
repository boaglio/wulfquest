package wulf.sim.ai;

import java.util.List;
import wulf.data.CreatureData;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.sim.Creature;
import wulf.sim.Simulation;

/**
 * DART (AGENTS.md §12.8, [CANON], {@code $A54F}, {@code $A5CD}): most of the original's small
 * creatures. Off in a straight line at a random speed on each axis — never straight across or
 * up, always slanting — for {@code runTicks}; off a room edge it glances, and if the scenery
 * stops it dead it stands still a moment, then sets off on a new line.
 *
 * <p>{@code timer} counts down to the next change; {@code phase} and {@code aux} are the
 * velocity, x and y, in 8.8 per tick — both 0 while it stands.
 */
public final class Dart implements Behaviour {

    @Override
    public List<String> params() {
        return List.of("speedMinFp", "speedStepFp", "speedSteps", "runTicks", "pauseMinTicks", "pauseSpanTicks");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        setOff(self, rng);   // $A3E0 → $A5CD: it is on its way the moment it forms
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        self.timer(self.timer() - 1);
        if (self.timer() <= 0) {
            if (moving(self)) {
                stand(self, ctx.rng());
            } else {
                setOff(self, ctx.rng());
            }
        }
        if (!moving(self)) {
            return;
        }
        glance(self, ctx);
        int ox = self.xFp();
        int oy = self.yFp();
        ctx.move(self, self.phase(), self.aux());
        if (self.xFp() == ox && self.yFp() == oy) {
            stand(self, ctx.rng());   // $AAED: the scenery has it fast
        }
    }

    private static boolean moving(Creature self) {
        return self.phase() != 0 || self.aux() != 0;
    }

    /** {@code $A5D5}: a speed for each axis from the table, each either way. */
    private static void setOff(Creature self, Rng rng) {
        self.phase(speed(self, rng));
        self.aux(speed(self, rng));
        self.face(Integer.signum(self.phase()));
        self.timer(Moves.param(self, "runTicks"));
    }

    private static int speed(Creature self, Rng rng) {
        int v = Moves.param(self, "speedMinFp") + Moves.param(self, "speedStepFp") * rng.nextInt(Moves.param(self, "speedSteps"));
        return rng.nextInt(2) == 0 ? -v : v;
    }

    /** {@code $A609}: still, for a moment of even ticks — whole updates of the original's. */
    private static void stand(Creature self, Rng rng) {
        self.phase(0);
        self.aux(0);
        self.timer(Moves.param(self, "pauseMinTicks") + 2 * rng.nextInt(Math.max(1, Moves.param(self, "pauseSpanTicks") / 2)));
    }

    /** {@code $A44B}: a step that would leave the room turns that axis about instead ({@code $A5A9}, {@code $A5BA}). */
    static void glance(Creature self, SimContext ctx) {
        CreatureData.Box b = self.species().collisionBox();
        int left = Fixed.px(self.xFp() + self.phase()) + b.x() - ctx.roomLeftPx();
        if (left < 0 || left + b.w() > Simulation.ROOM_W_PX) {
            self.phase(-self.phase());
            self.face(Integer.signum(self.phase()));
        }
        int top = Fixed.px(self.yFp() + self.aux()) + b.y() - ctx.roomTopPx();
        if (top < 0 || top + b.h() > Simulation.ROOM_H_PX) {
            self.aux(-self.aux());
        }
    }
}
