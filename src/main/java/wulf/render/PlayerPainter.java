package wulf.render;

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
            if (!sim.sabre().isEmpty()) {
                paintBlade(fb, p.facing(), sprite.anchor(frame, AnimationValidator.HAND), feetX, feetY);
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

    /** 0 windup, 1 strike, 2 recover — exactly the sabre's phases (§11.6). */
    static int phaseOf(int swingTick, PlayerData.Sabre sabre) {
        if (swingTick < sabre.windupTicks()) {
            return 0;
        }
        return swingTick < sabre.windupTicks() + sabre.activeTicks() ? 1 : 2;
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

    /** The blade: out of the hand along the facing, as long as the hitbox reaches, 2 px thick. */
    private void paintBlade(Framebuffer fb, Direction8 d, SpriteData.Point hand, int feetX, int feetY) {
        int hx = feetX - sprite.originX() + hand.x();
        int hy = feetY - sprite.originY() + hand.y();
        PlayerData.Sabre s = rules.sabre();
        int reach = s.reachPx();
        if (d.dx() != 0) {
            for (int i = 1; i <= reach; i++) {
                // Diagonals tilt toward their vertical by the same offset the hitbox is shifted.
                int y = hy + (d.diagonal() ? d.dy() * (i * s.diagonalOffsetPx() / reach) : 0);
                fb.fillRect(hx + d.dx() * i, y, 1, 2, blade);
            }
            fb.fillRect(hx + d.dx(), hy - 2, 1, 6, hilt);       // crossguard just past the hand
        } else {
            for (int i = 1; i <= reach; i++) {
                fb.fillRect(hx, hy + d.dy() * i, 2, 1, blade);
            }
            fb.fillRect(hx - 2, hy + d.dy(), 6, 1, hilt);
        }
    }
}
