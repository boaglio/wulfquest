package wulf.data;

import java.util.List;
import java.util.Map;

/** Bound view of {@code data/entities/player.json} (AGENTS.md §11.1). Integers only: it feeds the simulation. */
public record PlayerData(
        int schemaVersion,
        String id,
        String displayName,
        String sprite,
        Box collisionBox,
        Speed speed,
        Momentum momentum,
        Lives lives,
        Spawn spawn,
        Death death,
        Sabre sabre,
        Map<String, Animation> animations) {

    public PlayerData {
        animations = Map.copyOf(animations);
    }

    /** The feet box, relative to the sprite origin (bottom-centre), in pixels (§7.4). */
    public record Box(int x, int y, int w, int h) {
    }

    /** Fixed-point 8.8 pixels per tick (§11.2). */
    public record Speed(int xFp, int yFp, int diagonalScaleFp) {
    }

    /**
     * The original's inertia (§11.3, $AEEF): per axis, a velocity in its own units. Each
     * frame a held direction adds {@code accel}, the velocity is clamped to {@code max} and,
     * after moving, decays by {@code decay} toward rest. It moves at whole steps of
     * {@code step} — 0, ⅓, ⅔ or the full {@link Speed} — so it speeds up over a few frames
     * and glides to a stop. While fighting the velocity is held at {@code fight}, a steady
     * pace with no inertia ($ADD0). One original frame lasts {@code ticksPerFrame} of ours.
     */
    public record Momentum(int ticksPerFrame, int accel, int max, int decay, int step, int fight) {

        /** How many speed steps the clamp allows: 3 for the original's 48 / 16. */
        public int levels() {
            return max / step;
        }
    }

    public record Lives(int start, int max, List<Integer> extraAt) {
        public Lives {
            extraAt = List.copyOf(extraAt);
        }
    }

    /** Respawn rules (§11.7). {@code insideRoomPx} keeps the whole sprite on screen, off the flip-screen clip. */
    public record Spawn(int invulnTicks, int blinkPeriodTicks, Margin insideRoomPx) {
    }

    /** Minimum distances of the feet origin from each room edge, in pixels. */
    public record Margin(int left, int right, int top, int bottom) {
    }

    public record Death(int animTicks, int freezeTicks, boolean keepAmulet) {
    }

    /** @param holdRepeatTicks the gap between swings while fire is held; 0 chains them (§11.6) */
    public record Sabre(
            int windupTicks,
            int activeTicks,
            int recoverTicks,
            int cooldownTicks,
            int holdRepeatTicks,
            int reachPx,
            int thicknessPx,
            int diagonalOffsetPx,
            int repelWulfPx,
            int repelWulfStunTicks,
            Fence fence) {

        public int totalTicks() {
            return windupTicks + activeTicks + recoverTicks;
        }
    }

    /**
     * How Vale fences while the blade is live (§11.6): each stroke is on guard for
     * {@code everyTicks}, then lunges for {@code everyTicks} — the body in the sprite's
     * guard and lunge frames, the blade drawn back short in one of {@code guards} or out
     * at full reach in one of {@code thrusts}, picked at random as the original picks
     * its fighting poses ($AE4B, Rand8). A pose is {forward, side} in sixteenths of the
     * reach, relative to where Vale faces; side is clockwise, so facing east a negative
     * side is up. Facing straight up or down the blade points into or out of the screen,
     * so it is drawn {@code towardViewerPercent} of its length: foreshortened. Drawing
     * only: the hitbox never follows the pose, nor did the original's.
     */
    public record Fence(int everyTicks, List<List<Integer>> guards, List<List<Integer>> thrusts,
                        int towardViewerPercent) {
        public Fence {
            guards = copy(guards);
            thrusts = copy(thrusts);
        }

        private static List<List<Integer>> copy(List<List<Integer>> poses) {
            List<List<Integer>> copies = new java.util.ArrayList<>();
            for (List<Integer> pose : poses) {
                copies.add(List.copyOf(pose));
            }
            return List.copyOf(copies);
        }

        /** On guard for the first window of a stroke, lunging for the next: the fencer's in and out. */
        public boolean thrusting(int intoBlade) {
            return (intoBlade / everyTicks) % 2 == 1;
        }
    }

    /**
     * @param driver       {@value #TICKS}: frame = ticks / ticksPerFrame; {@value #SABRE_PHASE}: windup,
     *                     strike and recover follow the sabre's phases exactly (§11.6)
     * @param gaitVariants swing_* only: every frame also exists as {@code <frame>_walk<g>}, used while moving
     */
    public record Animation(List<String> frames, int ticksPerFrame, boolean loop, String driver, boolean gaitVariants) {

        public static final String TICKS = "ticks";
        public static final String SABRE_PHASE = "sabrePhase";

        public Animation {
            frames = List.copyOf(frames);
        }

        public boolean sabrePhase() {
            return SABRE_PHASE.equals(driver);
        }
    }

    public Animation animation(String name) {
        Animation a = animations.get(name);
        if (a == null) {
            throw new IllegalStateException("player.json has no animation '" + name + "'");
        }
        return a;
    }
}
