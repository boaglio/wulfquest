package wulf.sim.ai;

import java.util.List;
import wulf.data.CreatureData;
import wulf.engine.Rng;
import wulf.sim.Creature;
import wulf.sim.Direction8;

/**
 * AMBUSH_BURST (§12.5): waits, watching the player. When the player is within
 * {@code triggerPx} with a clear line of cells between them, charges along that
 * direction at {@code chargeSpeedFp} for {@code chargeTicks}, ignoring the player
 * from then on. Hitting anything stuns it for {@code stunTicks}.
 *
 * <p>State: {@code phase} (WAIT, CHARGE, STUN) and {@code timer}.
 */
public final class AmbushBurst implements Behaviour {

    public static final int WAIT = 0;
    public static final int CHARGE = 1;
    public static final int STUN = 2;

    @Override
    public List<String> params() {
        return List.of("triggerPx", "chargeSpeedFp", "chargeTicks", "stunTicks");
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Moves.randomDirection(self, rng);
        self.phase(WAIT);
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        switch (self.phase()) {
            case WAIT -> {
                if (!ctx.playerAlive()) {
                    return;
                }
                int dx = Moves.dxToPlayer(self, ctx);
                int dy = Moves.dyToPlayer(self, ctx);
                self.face(Integer.signum(dx));
                long trigger = Moves.param(self, "triggerPx");
                if ((long) dx * dx + (long) dy * dy <= trigger * trigger
                        && ctx.lineClear(self.centreXPx(), self.centreYPx(), ctx.playerCentreXPx(), ctx.playerCentreYPx())) {
                    Direction8 d = Direction8.toward(dx, dy);
                    self.direction(d.dx(), d.dy());
                    self.phase(CHARGE);
                    self.timer(Moves.param(self, "chargeTicks"));
                }
            }
            case CHARGE -> {
                CreatureData.Speed s = self.species().speed();
                int chargeX = Moves.param(self, "chargeSpeedFp");
                int chargeY = s.xFp() == 0 ? chargeX : (int) ((long) chargeX * s.yFp() / s.xFp());
                if (ctx.step(self, self.dirX(), self.dirY(), chargeX, chargeY) != 0) {
                    self.phase(STUN);
                    self.timer(Moves.param(self, "stunTicks"));
                    return;
                }
                int t = self.timer() - 1;
                self.timer(t);
                if (t <= 0) {
                    self.phase(WAIT);
                }
            }
            default -> {
                int t = self.timer() - 1;
                self.timer(t);
                if (t <= 0) {
                    self.phase(WAIT);
                }
            }
        }
    }
}
