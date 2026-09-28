package wulf.render;

import java.util.List;
import wulf.data.AnimationValidator;
import wulf.data.DisplayConfig;
import wulf.data.Palette;
import wulf.data.PlayerData;
import wulf.data.SpriteData;
import wulf.engine.Fixed;
import wulf.sim.Direction8;
import wulf.sim.Player;
import wulf.sim.Simulation;

/**
 * Draws Ranger Vale and his blade (AGENTS.md §11.4–§11.7).
 *
 * <p>Frame choice is a pure function of simulation state ({@link #frameFor}), so
 * it is tested without a window. A swing's pose follows the sabre's phases, so
 * the strike pose is on screen exactly while the blade can hit; while moving,
 * the pose is drawn on walking legs. The blade comes out of the hand anchor with
 * the hitbox's exact reach — the hitbox itself lies on the ground plane beneath
 * it, where creatures' feet are.
 */
public final class PlayerPainter {

    private final DisplayConfig.Playfield field;
    private final Sprite sprite;
    private final PlayerData rules;
    private final int blade;
    private final int immuneFlash;
    private final int hilt;

    public PlayerPainter(DisplayConfig display, SpriteBank sprites, PlayerData rules, Palette palette) {
        this.field = display.playfield();
        this.sprite = sprites.get(rules.sprite());
        this.rules = rules;
        this.blade = palette.indexOf("brightWhite");
        this.immuneFlash = palette.indexOf("brightWhite");
        this.hilt = palette.indexOf("brightYellow");
    }

    public void paint(Framebuffer fb, Simulation sim) {
        Player p = sim.player();
        // World pixels to screen: subtract the room origin, add the playfield origin.
        int feetX = Fixed.px(p.xFp()) + field.x() - sim.room().col() * Simulation.ROOM_W_PX;
        int feetY = Fixed.px(p.yFp()) + field.y() - sim.room().row() * Simulation.ROOM_H_PX;
        String frame = frameFor(p, rules);
        boolean fighting = !sim.sabre().isEmpty();
        // A player straddling a room edge is cut at the playfield: flip-screen, not scrolling.
        fb.setClip(field.x(), field.y(), field.w(), field.h());
        try {
            if (!visible(p, rules)) {
                return;
            }
            if (sim.effectFlash()) {
                sprite.blitTinted(fb, frame, feetX, feetY, immuneFlash);   // §15.2: nothing can touch him
            } else {
                sprite.blit(fb, frame, feetX, feetY);
            }
            if (fighting) {
                paintBlade(fb, p.facing(), sprite.anchor(frame, AnimationValidator.HAND), feetX, feetY, p.swingTick(), sim.tick());
            }
        } finally {
            fb.clearClip();
        }
    }

    /** The sprite frame for this state. */
    static String frameFor(Player p, PlayerData rules) {
        switch (p.mode()) {
            case DYING -> {
                return frameAt(rules.animation("die"), p.modeTick(), false);
            }
            case DOWN, GAME_OVER -> {
                PlayerData.Animation die = rules.animation("die");
                return die.frames().get(die.frames().size() - 1);
            }
            case ALIVE -> {
                // chosen below
            }
        }
        String view = viewOf(p.facing());
        String mirror = view.equals("side") && p.facing().dx() < 0 ? AnimationValidator.MIRROR_SUFFIX : "";
        PlayerData.Animation walk = rules.animation("walk_" + view);
        if (p.swinging()) {
            PlayerData.Animation swing = rules.animation("swing_" + view);
            String frame = swing.sabrePhase()
                    ? swing.frames().get(phaseOf(p.swingTick(), rules.sabre()))
                    : frameAt(swing, p.swingTick(), false);
            if (swing.gaitVariants() && p.walkTicks() > 0) {
                // Swinging on the move: the same pose on walking legs, so he never glides (§11.6).
                frame = frame + AnimationValidator.GAIT_INFIX + gaitIndex(walk, p.walkTicks());
            }
            return frame + mirror;
        }
        // Idle snaps to frame 0 at once; the gait is driven by ticks actually moved (§11.5).
        return walk.frames().get(gaitIndex(walk, p.walkTicks())) + mirror;
    }

    /**
     * The pose for a swing tick (§11.6): 0 guard, 1 lunge, 2 recover. While the blade is live
     * it fences — on guard, then the lunge, a window of {@code fence.everyTicks} each — and any
     * windup or recovery ticks hold the guard and the recovery.
     */
    static int phaseOf(int swingTick, PlayerData.Sabre sabre) {
        if (swingTick < sabre.windupTicks()) {
            return 0;
        }
        int intoBlade = swingTick - sabre.windupTicks();
        if (intoBlade >= sabre.activeTicks()) {
            return 2;
        }
        return sabre.fence().thrusting(intoBlade) ? 1 : 0;
    }

    private static int gaitIndex(PlayerData.Animation walk, int walkTicks) {
        return walk.ticksPerFrame() == 0 ? 0 : (walkTicks / walk.ticksPerFrame()) % walk.frames().size();
    }

    private static String frameAt(PlayerData.Animation a, int ticks, boolean loop) {
        if (a.ticksPerFrame() == 0) {
            return a.frames().get(0);
        }
        int i = ticks / a.ticksPerFrame();
        i = loop ? i % a.frames().size() : Math.min(i, a.frames().size() - 1);
        return a.frames().get(i);
    }

    /** Diagonals use the side view, mirrored by the sign of x; pure up and down have their own (§11.4). */
    static String viewOf(Direction8 facing) {
        if (facing.dx() != 0) {
            return "side";
        }
        return facing.dy() < 0 ? "up" : "down";
    }

    /** Spawn invulnerability blinks: half a period shown, half hidden (§11.7). */
    static boolean visible(Player p, PlayerData rules) {
        if (p.mode() != Player.Mode.ALIVE || p.invulnTicks() == 0) {
            return true;
        }
        int period = rules.spawn().blinkPeriodTicks();
        return p.invulnTicks() % period < (period + 1) / 2;
    }

    /**
     * The blade fences (§11.6): out of the hand of the guard or lunge frame, drawn back
     * short on guard and out at full reach in the lunge, its angle picked at random from
     * the pose lists as the original picks its fighting poses. The pick is a hash of the
     * tick, so it is the same on every run and the simulation never sees it; the hitbox
     * stays where §11.6 puts it.
     */
    private void paintBlade(Framebuffer fb, Direction8 d, SpriteData.Point hand, int feetX, int feetY, int swingTick,
                            long tick) {
        int hx = feetX - sprite.originX() + hand.x();
        int hy = feetY - sprite.originY() + hand.y();
        PlayerData.Sabre s = rules.sabre();
        List<Integer> pose = pose(s.fence(), swingTick - s.windupTicks(), tick);
        // Forward is the way Vale faces; side is a quarter-turn clockwise of it.
        int fx = d.dx();
        int fy = d.dy();
        int vx = fx * pose.get(0) - fy * pose.get(1);
        int vy = fy * pose.get(0) + fx * pose.get(1);
        // Straight up or down the blade points into or out of the screen: foreshortened.
        int reach = d.dx() == 0 ? s.reachPx() * s.fence().towardViewerPercent() / 100 : s.reachPx();
        boolean flat = Math.abs(vx) >= Math.abs(vy);
        for (int i = 1; i <= reach; i++) {
            int x = hx + Math.floorDiv(vx * i, 16);
            int y = hy + Math.floorDiv(vy * i, 16);
            fb.fillRect(x, y, flat ? 1 : 2, flat ? 2 : 1, blade);
        }
        // The crossguard, across the blade just past the hand.
        int gx = hx + Math.floorDiv(vx, 16);
        int gy = hy + Math.floorDiv(vy, 16);
        if (flat) {
            fb.fillRect(gx, gy - 2, 1, 6, hilt);
        } else {
            fb.fillRect(gx - 2, gy, 6, 1, hilt);
        }
    }

    /**
     * The blade's pose: a guard or a thrust by where the stroke is, as the body's frame is;
     * which one of those, by a hash of the game tick's window, fixed within it.
     */
    static List<Integer> pose(PlayerData.Fence fence, int intoBlade, long tick) {
        long window = tick / fence.everyTicks();
        long h = (window + 1) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        List<List<Integer>> poses = fence.thrusting(intoBlade) ? fence.thrusts() : fence.guards();
        return poses.get((int) Math.floorMod(h, (long) poses.size()));
    }
}
