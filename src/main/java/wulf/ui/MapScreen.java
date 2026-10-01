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
 * drawn too, dimmer (§14.8). The room he is in blinks. A quarter an eye has shown, and
 * not yet taken, sits in its room as a small amulet piece.
 */
final class MapScreen {

    private final DisplayConfig.Playfield field;
    private final RoomBaker rooms;
    private final LandmarksData marks;
    private final Sprite amulet;
    private final int ground;
    private final int visited;
    private final int revealed;
    private final int here;
    private final int blinkTicks;

    MapScreen(DisplayConfig display, RoomBaker rooms, LandmarksData marks, Sprite amulet, Palette palette,
              ShellConfig.MapScreen config) {
        this.field = display.playfield();
        this.rooms = rooms;
        this.marks = marks;
        this.amulet = amulet;
        this.ground = palette.indexOf("black");
        this.visited = palette.indexOf(config.visited());
        this.revealed = palette.indexOf(config.revealed());
        this.here = palette.indexOf(config.here());
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

    /** The quarters the eyes have shown (§14.8), until they are taken. */
    private void paintQuarters(Framebuffer fb, Simulation sim, int cw, int ch) {
        List<LandmarksData.Eye> eyes = marks.items().eyes();
        for (int i = 0; i < eyes.size(); i++) {
            if (!sim.quest().eyeTaken(i)) {
                continue;
            }
            for (String id : eyes.get(i).reveals()) {
                for (int l = 0; l < marks.lairs().size(); l++) {
                    LandmarksData.Lair lair = marks.lairs().get(l);
                    if (!lair.id().equals(id) || sim.quest().taken(l)) {
                        continue;
                    }
                    RoomAddress room = LandmarksData.room(lair.room());
                    int size = amulet.w() / 2;
                    amulet.blitHalf(fb, lair.piece().frame(), field.x() + room.col() * cw + (cw - size) / 2,
                            field.y() + room.row() * ch + (ch - size) / 2, -1);
                }
            }
        }
    }
}
