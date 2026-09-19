package wulf.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import wulf.data.CreatureData;
import wulf.data.CreatureSpriteValidator;
import wulf.data.DisplayConfig;
import wulf.data.Palette;
import wulf.engine.Fixed;
import wulf.sim.Creature;
import wulf.sim.Simulation;
import wulf.sim.Spear;
import wulf.sim.Wulf;
import wulf.sim.Quest;

/**
 * Draws the room's creatures and spears (AGENTS.md §12, §5.3): back to front by
 * feet, a two-frame gait while moving — fliers flap always — mirrored when facing
 * left, a white flash while hurt, the puff while dying, a spider's thread, and
 * spears as a shaft with a bright point. Then the Wulf, over the creatures and under
 * the player (§5.3): galloping, or howling at the edge while it warns. Clipped to the
 * playfield.
 */
public final class CreaturePainter {

    private static final String DROP_THREAD = "DROP_THREAD";
    private static final Comparator<Creature> BACK_TO_FRONT =
            Comparator.comparingInt(Creature::yFp).thenComparingInt(Creature::id);

    private final DisplayConfig.Playfield field;
    private final SpriteBank sprites;
    private final CreatureData roster;
    private final Sprite puff;
    private final int flash;
    private final int thread;
    private final int shaft;
    private final int point;
    private final List<Creature> order = new ArrayList<>();

    public CreaturePainter(DisplayConfig display, SpriteBank sprites, CreatureData roster, Palette palette) {
        this.field = display.playfield();
        this.sprites = sprites;
        this.roster = roster;
        this.puff = sprites.get(CreatureSpriteValidator.PUFF);
        this.flash = palette.indexOf("brightWhite");
        this.thread = palette.indexOf("white");
        this.shaft = palette.indexOf("white");
        this.point = palette.indexOf("brightCyan");
    }

    public void paint(Framebuffer fb, Simulation sim) {
        int ox = field.x() - sim.room().col() * Simulation.ROOM_W_PX;
        int oy = field.y() - sim.room().row() * Simulation.ROOM_H_PX;
        order.clear();
        order.addAll(sim.creatures());
        order.sort(BACK_TO_FRONT);
        fb.setClip(field.x(), field.y(), field.w(), field.h());
        try {
            for (int i = 0; i < order.size(); i++) {
                paintCreature(fb, order.get(i), sim.tick(), ox, oy);
            }
            Quest quest = sim.quest();
            if (quest.inLair()) {
                paintBody(fb, quest.guardian(), frameFor(quest.guardian(), sim.tick(), roster.walkTicksPerFrame()), ox, oy);
            }
            if (quest.keeperHere()) {
                paintBody(fb, quest.keeper(), keeperFrame(quest, sim.ecosystem().quest().stepAsideTicks()), ox, oy);
            }
            Wulf wulf = sim.wulf();
            if (wulf.visible()) {
                paintWulf(fb, wulf, sim.tick(), ox, oy);
            }
            if (!sim.spears().isEmpty()) {
                int length = roster.projectile("spear").lengthPx();
                for (int i = 0; i < sim.spears().size(); i++) {
                    paintSpear(fb, sim.spears().get(i), length, ox, oy);
                }
            }
        } finally {
            fb.clearClip();
        }
    }

    private void paintCreature(Framebuffer fb, Creature c, long tick, int ox, int oy) {
        int x = Fixed.px(c.xFp()) + ox;
        int y = Fixed.px(c.yFp()) + oy;
        if (!c.alive()) {
            puff.blit(fb, puffFrame(c, roster.puffTicks()), x, y);
            return;
        }
        CreatureData.Species s = c.species();
        if (s.behaviour().kind().equals(DROP_THREAD) && c.yFp() > c.anchorYFp()) {
            int top = Fixed.px(c.anchorYFp()) + oy - s.size().h();
            int bottom = y - s.size().h();
            fb.fillRect(c.centreXPx() + ox, top, 1, bottom - top + 1, thread);
        }
        Sprite sprite = sprites.get(s.sprite());
        String frame = frameFor(c, tick, roster.walkTicksPerFrame());
        if (flashing(c)) {
            sprite.blitTinted(fb, frame, x, y, flash);
        } else {
            sprite.blit(fb, frame, x, y);
        }
    }

    private void paintWulf(Framebuffer fb, Wulf wulf, long tick, int ox, int oy) {
        Creature body = wulf.body();
        Sprite sprite = sprites.get(body.species().sprite());
        String frame = wulfFrame(wulf, tick, roster.walkTicksPerFrame());
        int x = Fixed.px(body.xFp()) + ox;
        int y = Fixed.px(body.yFp()) + oy;
        if (flashing(body)) {
            sprite.blitTinted(fb, frame, x, y, flash);   // stunned by a parry
        } else {
            sprite.blit(fb, frame, x, y);
        }
    }

    private void paintBody(Framebuffer fb, Creature body, String frame, int ox, int oy) {
        Sprite sprite = sprites.get(body.species().sprite());
        int x = Fixed.px(body.xFp()) + ox;
        int y = Fixed.px(body.yFp()) + oy;
        if (flashing(body)) {
            sprite.blitTinted(fb, frame, x, y, flash);
        } else {
            sprite.blit(fb, frame, x, y);
        }
    }

    /** Standing; then its four-frame step aside once the amulet is whole (§14.4); then aside. */
    static String keeperFrame(Quest quest, int stepAsideTicks) {
        return switch (quest.keeperState()) {
            case BLOCKING -> "stand";
            case ASIDE -> "aside";
            case STEPPING_ASIDE -> {
                int i = Math.min(3, quest.keeperTick() * 4 / Math.max(1, stepAsideTicks));
                yield List.of("stand", "step0", "step1", "aside").get(i);
            }
        };
    }

    /** Howling while it warns (§13.3); otherwise the ordinary gait, facing its way. */
    static String wulfFrame(Wulf wulf, long tick, int ticksPerFrame) {
        Creature body = wulf.body();
        if (wulf.state() == Wulf.State.WARNING) {
            return body.faceX() < 0 ? "howl_l" : "howl";
        }
        return frameFor(body, tick, ticksPerFrame);
    }

    private void paintSpear(Framebuffer fb, Spear s, int length, int ox, int oy) {
        int x = Fixed.px(s.xFp()) + ox;
        int y = Fixed.px(s.yFp()) + oy;
        for (int i = 2; i < length; i++) {
            fb.set(x - s.dirX() * i, y - s.dirY() * i, shaft);
        }
        fb.set(x, y, point);
        fb.set(x - s.dirX(), y - s.dirY(), point);
    }

    /**
     * Walkers step while they move; fliers — anything that ignores scenery — flap on
     * the global clock, still or not.
     */
    static String frameFor(Creature c, long tick, int ticksPerFrame) {
        boolean flier = c.species().ignoresScenery();
        long clock = flier ? tick : c.moveTicks();
        int f = (flier || c.moveTicks() > 0) ? (int) ((clock / ticksPerFrame) % 2) : 0;
        return "walk" + f + (c.faceX() < 0 ? "_l" : "");
    }

    /** A hurt creature flashes white on alternate ticks. */
    static boolean flashing(Creature c) {
        return c.hurtTicks() > 0 && c.hurtTicks() % 2 == 0;
    }

    static String puffFrame(Creature c, int puffTicks) {
        return c.modeTick() < puffTicks / 2 ? "f0" : "f1";
    }
}
