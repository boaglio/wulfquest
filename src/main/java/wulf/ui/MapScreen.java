package wulf.ui;

import java.util.List;
import wulf.data.DisplayConfig;
import wulf.data.LandmarksData;
import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.Framebuffer;
import wulf.sim.Simulation;
import wulf.world.CollisionMask;
import wulf.world.RoomAddress;
import wulf.world.RoomBaker;

/**
 * The map (AGENTS.md §17.7, {@code [NEW]}): the whole 16x16 jungle over the playfield,
 * one room to 16x11 pixels — its open ground in miniature, a pixel for every 2x2 cells.
 * Rooms Vale has been in are drawn; once the map scroll is found, every other room is
 * drawn too, dimmer (§14.8). The room he is in blinks. A lair an eye has shown fills its
 * room in its quarter's colour; a lair whose quarter is taken, grey.
 */
final class MapScreen {

    private final DisplayConfig.Playfield field;
    private final RoomBaker rooms;
    private final LandmarksData marks;
    private final int ground;
    private final int visited;
    private final int revealed;
    private final int here;
    private final int[] quarterColours;
    private final int takenColour;
    private final int blinkTicks;

    MapScreen(DisplayConfig display, RoomBaker rooms, LandmarksData marks, Palette palette,
              ShellConfig.MapScreen config) {
        this.field = display.playfield();
        this.rooms = rooms;
        this.marks = marks;
        this.ground = palette.indexOf("black");
        this.visited = palette.indexOf(config.visited());
        this.revealed = palette.indexOf(config.revealed());
        this.here = palette.indexOf(config.here());
        this.quarterColours = new int[config.quarters().size()];
        for (int i = 0; i < quarterColours.length; i++) {
            quarterColours[i] = palette.indexOf(config.quarters().get(i));
        }
        this.takenColour = palette.indexOf(config.taken());
        this.blinkTicks = config.blinkTicks();
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
        paintQuarters(fb, sim, cw, ch);
        if ((tick / blinkTicks) % 2 == 0) {
            fb.drawRect(field.x() + sim.room().col() * cw, field.y() + sim.room().row() * ch, cw, ch, here);
        }
    }

    /**
     * The lairs (§14.8): one an eye has shown fills its room in its quarter's colour, outlined
     * in black so it stands out from the rooms around it; one whose quarter is taken turns
     * {@code taken}, shown or not — so the map says where to go and what is done.
     */
    private void paintQuarters(Framebuffer fb, Simulation sim, int cw, int ch) {
        List<LandmarksData.Eye> eyes = marks.items().eyes();
        for (int l = 0; l < marks.lairs().size(); l++) {
            LandmarksData.Lair lair = marks.lairs().get(l);
            boolean taken = sim.quest().taken(l);
            boolean shown = false;
            for (int i = 0; i < eyes.size() && !shown; i++) {
                shown = sim.quest().eyeTaken(i) && eyes.get(i).reveals().contains(lair.id());
            }
            if (!taken && !shown) {
                continue;
            }
            int slot = lair.piece().slot();
            int colour = taken ? takenColour : quarterColours[slot % quarterColours.length];
            RoomAddress room = LandmarksData.room(lair.room());
            int left = field.x() + room.col() * cw;
            int top = field.y() + room.row() * ch;
            fb.fillRect(left, top, cw, ch, colour);
            fb.drawRect(left, top, cw, ch, ground);
        }
    }
}
