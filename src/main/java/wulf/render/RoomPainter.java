package wulf.render;

import wulf.data.DisplayConfig;
import wulf.data.Placement;
import wulf.world.CollisionMask;
import wulf.world.Room;
import wulf.world.SceneryCatalog;

/** Draws a room's scenery into the playfield, in the order the original data lists it (AGENTS.md §5.3). */
public final class RoomPainter {

    private final DisplayConfig.Playfield field;
    private final SpriteBank sprites;
    private final SceneryCatalog catalog;
    private final int ground;

    public RoomPainter(DisplayConfig display, SpriteBank sprites, SceneryCatalog catalog, int groundIndex) {
        this.field = display.playfield();
        this.sprites = sprites;
        this.catalog = catalog;
        this.ground = groundIndex;
    }

    public void paint(Framebuffer fb, Room room) {
        fb.fillRect(field.x(), field.y(), field.w(), field.h(), ground);
        for (Placement p : room.placements()) {
            SceneryCatalog.Piece piece = catalog.piece(p.graphic());
            sprites.get(piece.sprite()).blit(fb, field.x() + p.x() * field.cell(), field.y() + p.y() * field.cell());
        }
    }

    /** Debug overlay: a checkerboard over every solid cell. */
    public void paintMask(Framebuffer fb, Room room, int colour) {
        CollisionMask mask = room.mask();
        int cell = field.cell();
        for (int cy = 0; cy < CollisionMask.ROWS; cy++) {
            for (int cx = 0; cx < CollisionMask.COLS; cx++) {
                if (!mask.isSolid(cx, cy)) {
                    continue;
                }
                for (int y = 0; y < cell; y++) {
                    for (int x = 0; x < cell; x++) {
                        if (((x + y) & 1) == 0) {
                            fb.set(field.x() + cx * cell + x, field.y() + cy * cell + y, colour);
                        }
                    }
                }
            }
        }
    }
}
