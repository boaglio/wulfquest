package wulf.sim.ai;

import java.util.List;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.engine.SinTable;
import wulf.sim.Creature;
import wulf.sim.Direction8;

/**
 * GUARD_ORBIT (AGENTS.md §12.5, §14.3): circles its anchor — the amulet pedestal —
 * at {@code orbitRadiusPx}, one turn every {@code orbitTicksPerRev}. Let the player
 * come within {@code lungePx} and it breaks off and charges for {@code lungeTicks},
 * then goes back to the ring. Unkillable: a swing only repels and stuns it.
 *
 * <p>State: {@code phase} 0 orbiting / 1 lunging, {@code timer} the lunge countdown,
 * {@code aux} the stun, {@code anchor} the pedestal. It walks to the point on the
 * ring it should be at, so scenery still stops it — in a lair's clear middle that
 * reads as a clean, timeable orbit.
 */
public final class GuardOrbit implements Behaviour {

    public static final int ORBITING = 0;
    public static final int LUNGING = 1;

    @Override
    public List<String> params() {
        return List.of("orbitRadiusPx", "orbitTicksPerRev", "lungePx", "lungeTicks");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        self.phase(ORBITING);
        self.timer(0);
        self.aux(0);
        seat(self, ctx);
    }

    /** Where on the ring it belongs this tick, in world fixed point. */
    public static int[] ringPoint(Creature self, long tick) {
        int radius = Moves.param(self, "orbitRadiusPx");
        int perRev = Moves.param(self, "orbitTicksPerRev");
        int index = Math.floorMod(tick * 256 / perRev, 256);
        int x = self.anchorXFp() + Fixed.mul(Fixed.fp(radius), SinTable.sin((index + 64) & 0xFF));
        int y = self.anchorYFp() + Fixed.mul(Fixed.fp(radius), SinTable.sin(index));
        return new int[] {x, y};
    }

    /** Puts it on its ring point without collision: only at spawn, on its own pedestal. */
    private static void seat(Creature self, SimContext ctx) {
        int[] p = ringPoint(self, ctx.tick());
        self.place(p[0], p[1]);
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        if (self.aux() > 0) {
            self.aux(self.aux() - 1);   // stunned by a swing
            return;
        }
        if (self.phase() == LUNGING) {
            if (self.timer() > 0) {
                self.timer(self.timer() - 1);
                if (ctx.playerAlive()) {
                    Moves.towardPlayer(self, ctx);
                    Moves.step(ctx, self, self.dirX(), self.dirY());
                }
                return;
            }
            self.phase(ORBITING);
        }
        if (ctx.playerAlive() && near(self, ctx, Moves.param(self, "lungePx"))) {
            self.phase(LUNGING);
            self.timer(Moves.param(self, "lungeTicks"));
            Moves.towardPlayer(self, ctx);
            return;
        }
        int[] want = ringPoint(self, ctx.tick());
        int dx = want[0] - self.xFp();
        int dy = want[1] - self.yFp();
        Direction8 d = Direction8.toward(Fixed.px(dx), Fixed.px(dy));
        if (Math.abs(dx) < Fixed.ONE && Math.abs(dy) < Fixed.ONE) {
            return;
        }
        self.direction(d.dx(), d.dy());
        Moves.step(ctx, self, d.dx(), d.dy());
    }

    private static boolean near(Creature self, SimContext ctx, int px) {
        return Math.max(Math.abs(Moves.dxToPlayer(self, ctx)), Math.abs(Moves.dyToPlayer(self, ctx))) <= px;
    }
}
