package wulf.ui;

import java.util.List;
import wulf.data.DisplayConfig;
import wulf.data.LandmarksData;
import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;
import wulf.render.Sprite;
import wulf.render.WayPainter;
import wulf.sim.Simulation;
import wulf.world.CollisionMask;
import wulf.world.RoomAddress;
import wulf.world.RoomBaker;
import wulf.world.Wayfinder;

/**
 * The map (AGENTS.md §17.7, {@code [NEW]}): the whole 16x16 jungle over the playfield,
 * one room to 16x11 pixels — its open ground in miniature, a pixel for every 2x2 cells.
 * Rooms Vale has been in are drawn; once the map scroll is found, every other room is
 * drawn too, dimmer (§14.8). The room he is in blinks. A lair an eye has shown carries a
 * shiny diamond over its own map; a lair whose quarter is taken, a dull one.
 */
final class MapScreen {

    private final DisplayConfig.Playfield field;
    private final RoomBaker rooms;
    private final LandmarksData marks;
    private final int ground;
    private final int visited;
    private final int revealed;
    private final int here;
    private final int blinkTicks;
    private final Sprite icon;
    private final ShellConfig.QuarterIcon iconFrames;
    private final WayPainter way;
    private final ShellConfig.Trail trail;
    private final int trailColour;

    MapScreen(DisplayConfig display, RoomBaker rooms, LandmarksData marks, Palette palette,
              ShellConfig.MapScreen config, Sprite icon, WayPainter way) {
        this.field = display.playfield();
        this.rooms = rooms;
        this.marks = marks;
        this.ground = palette.indexOf("black");
        this.visited = palette.indexOf(config.visited());
        this.revealed = palette.indexOf(config.revealed());
        this.here = palette.indexOf(config.here());
        this.blinkTicks = config.blinkTicks();
        this.icon = icon;
        this.iconFrames = config.quarterIcon();
        this.way = way;
        this.trail = config.trail();
        this.trailColour = palette.indexOf(trail.colour());
    }

    void paint(Framebuffer fb, Simulation sim, long tick) {
        fb.fillRect(field.x(), field.y(), field.w(), field.h(), ground);
        int cw = field.w() / RoomAddress.GRID_W;
        int ch = field.h() / RoomAddress.GRID_H;
        boolean all = sim.quest().mapTaken();
        for (int row = 0; row < RoomAddress.GRID_H; row++) {
            for (int col = 0; col < RoomAddress.GRID_W; col++) {
                RoomAddress room = new RoomAddress(col, row);
                boolean been = sim.visits(room) > 0;
                if (!been && !all) {
                    continue;   // unknown jungle stays dark
                }
                int colour = been ? visited : revealed;
                CollisionMask mask = rooms.room(room).mask();
                int left = field.x() + col * cw;
                int top = field.y() + row * ch;
                for (int v = 0; v < ch; v++) {
                    for (int u = 0; u < cw; u++) {
                        // The ways through are what a map is for: open ground lit, walls left dark.
                        if (!mask.isSolid(u * CollisionMask.COLS / cw, v * CollisionMask.ROWS / ch)) {
                            fb.set(left + u, top + v, colour);
                        }
                    }
                }
            }
        }
        paintQuarters(fb, sim, cw, ch, tick);
        paintTrail(fb, sim, cw, ch, tick);
        if ((tick / blinkTicks) % 2 == 0) {
            fb.drawRect(field.x() + sim.room().col() * cw, field.y() + sim.room().row() * ch, cw, ch, here);
        }
    }

    /**
     * The way a looking glass shows (§14.10): dots along it from Vale's feet to the quarter, a
     * pixel for every 2x2 cells as the map draws the rooms, marching towards the end.
     */
    private void paintTrail(Framebuffer fb, Simulation sim, int cw, int ch, long tick) {
        if (way == null) {
            return;
        }
        int[] route = way.route(sim);
        int last = -1;
        int step = 0;
        int phase = (int) (tick / trail.marchTicks() % trail.dotEveryPx());
        for (int cell : route) {
            int u = Wayfinder.gx(cell) * cw / CollisionMask.COLS;
            int v = Wayfinder.gy(cell) * ch / CollisionMask.ROWS;
            int px = v * field.w() + u;
            if (px == last) {
                continue;   // two cells, one pixel
            }
            last = px;
            if ((step++ + trail.dotEveryPx() - phase) % trail.dotEveryPx() == 0) {
                fb.set(field.x() + u, field.y() + v, trailColour);
            }
        }
        if (route.length > 0 && (tick / blinkTicks) % 2 == 0) {
            // A cross where it ends, blinking with Vale's room: the quarter, or the arch.
            int end = route[route.length - 1];
            int u = field.x() + Wayfinder.gx(end) * cw / CollisionMask.COLS;
            int v = field.y() + Wayfinder.gy(end) * ch / CollisionMask.ROWS;
            for (int d = -2; d <= 2; d++) {
                fb.set(u + d, v, trailColour);
                fb.set(u, v + d, trailColour);
            }
        }
    }

    /**
     * The lairs (§14.8): one an eye has shown carries a shiny diamond, twinkling, over the map
     * of its room; one whose quarter is taken a dull one, shown or not — so the map says where
     * to go and what is done, and the room underneath still reads.
     */
    private void paintQuarters(Framebuffer fb, Simulation sim, int cw, int ch, long tick) {
        List<LandmarksData.Eye> eyes = marks.items().eyes();
        List<LandmarksData.Lair> lairs = sim.landmarks().lairs();   // where this game hid them (§14.2)
        List<String> shiny = iconFrames.shiny();
        for (int l = 0; l < lairs.size(); l++) {
            LandmarksData.Lair lair = lairs.get(l);
            boolean taken = sim.quest().taken(l);
            boolean shown = false;
            for (int i = 0; i < eyes.size() && !shown; i++) {
                shown = sim.quest().eyeTaken(i) && eyes.get(i).reveals().contains(lair.id());
            }
            if (!taken && !shown) {
                continue;
            }
            // Each diamond a frame ahead of the last, so they do not all twinkle at once.
            String frame = taken ? iconFrames.taken()
                    : shiny.get((int) ((tick / iconFrames.frameTicks() + l) % shiny.size()));
            RoomAddress room = LandmarksData.room(lair.room());
            icon.blit(fb, frame, field.x() + room.col() * cw + cw / 2, field.y() + room.row() * ch + ch);
        }
    }
}
