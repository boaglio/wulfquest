package wulf.ui;

import java.util.List;
import wulf.data.DisplayConfig;
import wulf.data.LandmarksData;
import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;
import wulf.render.Sprite;
import wulf.sim.Simulation;
import wulf.world.CollisionMask;
import wulf.world.RoomAddress;
import wulf.world.RoomBaker;

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

    MapScreen(DisplayConfig display, RoomBaker rooms, LandmarksData marks, Palette palette,
              ShellConfig.MapScreen config, Sprite icon) {
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
        if ((tick / blinkTicks) % 2 == 0) {
            fb.drawRect(field.x() + sim.room().col() * cw, field.y() + sim.room().row() * ch, cw, ch, here);
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
