package wulf.sim.ai;

import java.util.List;
import wulf.engine.Rng;
import wulf.sim.Creature;
import wulf.sim.Direction8;

/**
 * WALL_FOLLOW (§12.5): classic maze-hugging on four directions.
 *
 * <ul>
 *   <li>A wall beside it, on its wall side: straight on, turning away from anything ahead.</li>
 *   <li>The wall has just ended: one turn toward the wall side, to wrap round the corner.</li>
 *   <li>Otherwise — rounding that corner, or searching open ground — straight on, turning
 *       away from anything ahead, until a wall is beside it again.</li>
 * </ul>
 *
 * <p>The single turn matters. Turning toward the wall side on every tick without a wall
 * spins the creature in place above the corner, because its box has not yet cleared it
 * (found in M4).
 *
 * <p>State: {@code aux} is the wall side (+1 clockwise, -1 counter-clockwise);
 * {@code timer} is 0 while a wall was beside it last tick, 1 otherwise.
 */
public final class WallFollow implements Behaviour {

    private static final int PROBE_PX = 2;
    private static final int FOLLOWING = 0;
    private static final int LOOSE = 1;

    @Override
    public List<String> params() {
        return List.of();
    }

    @Override
    public void spawn(Creature self, SimContext ctx, Rng rng) {
        Moves.randomCardinal(self, rng);
        self.aux(rng.nextInt(2) == 0 ? 1 : -1);
        self.timer(LOOSE);   // start searching, not wrapping
    }

    @Override
    public void tick(Creature self, SimContext ctx) {
        Direction8 heading = Moves.heading(self);
        int side = self.aux();
        Direction8 toWall = heading.rotate(2 * side);
        Direction8 away = heading.rotate(-2 * side);
        Direction8 go;
        if (blockedToward(ctx, self, toWall)) {
            self.timer(FOLLOWING);
            go = ahead(ctx, self, heading, away);
        } else if (self.timer() == FOLLOWING) {
            self.timer(LOOSE);
            go = toWall;
        } else {
            go = ahead(ctx, self, heading, away);
        }
        self.direction(go.dx(), go.dy());
        Moves.step(ctx, self, go.dx(), go.dy());
    }

    private static Direction8 ahead(SimContext ctx, Creature c, Direction8 heading, Direction8 away) {
        if (!blockedToward(ctx, c, heading)) {
            return heading;
        }
        if (!blockedToward(ctx, c, away)) {
            return away;
        }
        return heading.rotate(4);
    }

    private static boolean blockedToward(SimContext ctx, Creature c, Direction8 d) {
        return ctx.probe(c, d.dx() * PROBE_PX, d.dy() * PROBE_PX);
    }
}
