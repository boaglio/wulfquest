package wulf.tools;

import java.util.List;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.Simulation;

/**
 * Plain directional input that follows a route of 8 px blocks with the player's inertia
 * (AGENTS.md §11.3), as a player does: aim down the straight ahead, and ease off — press the
 * other way — once the current speed would carry the feet to the target anyway. Shared by the
 * test bots; nothing here is the game.
 */
public final class Steering {

    private Steering() {
    }

    /** The feet position that puts the 10x9 box squarely in a 16x16 route block (§7.4). */
    public static int footX(int[] block) {
        return Fixed.fp(block[0] * 8 + 8);
    }

    public static int footY(int[] block) {
        return Fixed.fp(block[1] * 8 + 13);
    }

    /** Whether the feet are on a block, within the 1 px every route bot has always used. */
    public static boolean reached(Simulation sim, int[] block) {
        return Math.abs(footX(block) - sim.player().xFp()) <= Fixed.ONE
                && Math.abs(footY(block) - sim.player().yFp()) <= Fixed.ONE;
    }

    /**
     * Input that heads for route block {@code i}, looking down the straight it starts. Steering
     * only — the caller decides what else (fire, waiting) to press.
     */
    public static InputState along(Simulation sim, List<int[]> route, int i) {
        int[] here = route.get(i);
        int j = i;
        if (i + 1 < route.size()) {
            int sx = route.get(i + 1)[0] - here[0];
            int sy = route.get(i + 1)[1] - here[1];
            // Only look past this block when the feet are on its line and heading the same way the
            // route goes on: otherwise the cut corner meets a wall, or a U-turn's far end is where
            // the feet already are and this block is never reached.
            int toX = Integer.signum(footX(here) - sim.player().xFp());
            int toY = Integer.signum(footY(here) - sim.player().yFp());
            boolean onLine = sx != 0
                    ? Math.abs(footY(here) - sim.player().yFp()) <= Fixed.ONE && (toX == sx || toX == 0)
                    : Math.abs(footX(here) - sim.player().xFp()) <= Fixed.ONE && (toY == sy || toY == 0);
            while (onLine && j + 1 < route.size()
                    && route.get(j + 1)[0] - route.get(j)[0] == sx && route.get(j + 1)[1] - route.get(j)[1] == sy) {
                j++;
            }
        }
        int[] target = route.get(j);
        PlayerData rules = sim.rules();
        int dx = axis(footX(target) - sim.player().xFp(), sim.player().vx(), rules.speed().xFp(), rules.momentum());
        int dy = axis(footY(target) - sim.player().yFp(), sim.player().vy(), rules.speed().yFp(), rules.momentum());
        return InputState.of(dx, dy, false, false);
    }

    /** One axis: toward the target, or the other way to brake if the glide would reach it anyway. */
    public static int axis(int distFp, int v, int topFp, PlayerData.Momentum m) {
        if (Math.abs(distFp) <= Fixed.ONE) {
            return Math.abs(v) >= m.step() ? -Integer.signum(v) : 0;   // there: stop
        }
        int toward = Integer.signum(distFp);
        if (Integer.signum(v) == toward && brakingFp(Math.abs(v), topFp, m) >= Math.abs(distFp)) {
            return -toward;
        }
        return toward;
    }

    /** How far the feet travel while pressing the other way from velocity {@code v} until they stop. */
    static int brakingFp(int v, int topFp, PlayerData.Momentum m) {
        int perStepPerFrame = topFp / m.levels() * m.ticksPerFrame();
        int dist = 0;
        for (int w = v; w >= m.step(); w -= m.accel() + m.decay()) {
            dist += (w / m.step()) * perStepPerFrame;
        }
        return dist;
    }
}
