package wulf.render;

import wulf.data.AnimationValidator;
import wulf.data.DisplayConfig;
import wulf.data.Palette;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.sim.Direction8;
import wulf.sim.PixelRect;
import wulf.sim.Player;
import wulf.sim.Simulation;

/**
 * Draws Ranger Vale and his blade (AGENTS.md §11.4–§11.7).
 *
 * <p>Frame choice is a pure function of simulation state ({@link #frameFor}),
 * so it is tested without a window. The blade is drawn from the live hitbox,
 * not from the sprite: what you see is exactly what hits.
 */
public final class PlayerPainter {

    private final DisplayConfig.Playfield field;
    private final Sprite sprite;
    private final PlayerData rules;
    private final int blade;
    private final int hilt;

    public PlayerPainter(DisplayConfig display, SpriteBank sprites, PlayerData rules, Palette palette) {
        this.field = display.playfield();
        this.sprite = sprites.get(rules.sprite());
        this.rules = rules;
        this.blade = palette.indexOf("brightWhite");
        this.hilt = palette.indexOf("brightYellow");
    }

    public void paint(Framebuffer fb, Simulation sim) {
        Player p = sim.player();
        // World pixels to screen: subtract the room origin, add the playfield origin.
        int ox = field.x() - sim.room().col() * Simulation.ROOM_W_PX;
        int oy = field.y() - sim.room().row() * Simulation.ROOM_H_PX;
        // A player straddling a room edge is cut at the playfield: flip-screen, not scrolling.
        fb.setClip(field.x(), field.y(), field.w(), field.h());
        try {
            if (visible(p, rules)) {
                sprite.blit(fb, frameFor(p, rules), Fixed.px(p.xFp()) + ox, Fixed.px(p.yFp()) + oy);
            }
            PixelRect r = sim.sabre();
            if (!r.isEmpty()) {
                paintBlade(fb, r, p.facing(), ox, oy);
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
        String suffix = view.equals("side") && p.facing().dx() < 0 ? AnimationValidator.MIRROR_SUFFIX : "";
        if (p.swinging()) {
            return frameAt(rules.animation("swing_" + view), p.swingTick(), false) + suffix;
        }
        // Idle snaps to frame 0 at once; the gait is driven by ticks actually moved (§11.5).
        return frameAt(rules.animation("walk_" + view), p.walkTicks(), true) + suffix;
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

    private void paintBlade(Framebuffer fb, PixelRect r, Direction8 facing, int ox, int oy) {
        int x = r.x() + ox;
        int y = r.y() + oy;
        if (facing.dx() != 0) {
            int mid = y + r.h() / 2;
            fb.fillRect(x, mid - 1, r.w(), 2, blade);
            int base = facing.dx() > 0 ? x : x + r.w() - 2;          // the end nearest the hand
            fb.fillRect(base, mid - 3, 2, 6, hilt);
        } else {
            int mid = x + r.w() / 2;
            fb.fillRect(mid - 1, y, 2, r.h(), blade);
            int base = facing.dy() > 0 ? y : y + r.h() - 2;
            fb.fillRect(mid - 3, base, 6, 2, hilt);
        }
    }
}
