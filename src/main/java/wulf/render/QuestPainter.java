package wulf.render;

import wulf.data.DisplayConfig;
import wulf.data.LandmarksData;
import wulf.sim.Player;
import wulf.sim.Quest;
import wulf.sim.Simulation;
import wulf.sim.Treasures;

/**
 * What the quest adds to the screen (AGENTS.md §14): the treasures lying about
 * (§16.4), the quarter waiting on its pedestal, the quarter flying to its panel slot once taken, and the playfield
 * going dark row by row as Vale walks into the arch.
 */
public final class QuestPainter {

    private final DisplayConfig.Playfield field;
    private final LandmarksData marks;
    private final Sprite amulet;
    private final SpriteBank sprites;
    private final int black;
    private final TreasureArt treasures;

    /**
     * How the treasures look (§16.4): their places, the sprite, and each kind's frame and
     * colour — one ink, as on the Spectrum.
     */
    public record TreasureArt(Treasures places, String sprite, String[] frames, int[] inks) {
        public static final TreasureArt NONE = new TreasureArt(Treasures.NONE, "", new String[0], new int[0]);
    }

    public QuestPainter(DisplayConfig display, SpriteBank sprites, LandmarksData marks, int black) {
        this(display, sprites, marks, black, TreasureArt.NONE);
    }

    public QuestPainter(DisplayConfig display, SpriteBank sprites, LandmarksData marks, int black,
                        TreasureArt treasures) {
        this.treasures = treasures;
        this.field = display.playfield();
        this.marks = marks;
        this.amulet = marks.lairs().isEmpty() ? null : sprites.get(marks.amulet().sprite());
        this.sprites = sprites;
        this.black = black;
    }

    /** The quarter on its pedestal, under the creatures (§5.3: loot before creatures). */
    public void paintLoot(Framebuffer fb, Simulation sim) {
        paintTreasures(fb, sim);
        paintItems(fb, sim);
        Quest q = sim.quest();
        if (amulet == null || !q.inLair() || q.taken(q.lair())) {
            return;
        }
        LandmarksData.Lair lair = sim.landmarks().lairs().get(q.lair());
        fb.setClip(field.x(), field.y(), field.w(), field.h());
        try {
            blitPiece(fb, lair.slotMask(), field.x() + lair.pedestal().x(), field.y() + lair.pedestal().y());
        } finally {
            fb.clearClip();
        }
    }

    /** §16.4: whatever still lies in this room's two places. */
    private void paintTreasures(Framebuffer fb, Simulation sim) {
        Treasures places = treasures.places();
        if (!places.any()) {
            return;
        }
        Sprite sprite = sprites.get(treasures.sprite());
        fb.setClip(field.x(), field.y(), field.w(), field.h());
        try {
            for (int place = 0; place < Treasures.PLACES_PER_ROOM; place++) {
                int i = sim.room().index() * Treasures.PLACES_PER_ROOM + place;
                int kind = sim.treasureIn(i);
                if (kind >= 0) {
                    sprite.blitTinted(fb, treasures.frames()[kind], field.x() + places.x()[i],
                            field.y() + places.y()[i], treasures.inks()[kind]);
                }
            }
        } finally {
            fb.clearClip();
        }
    }

    /** §14.8: the map scroll and the eyes still lying in this room. An eye glances about. */
    private void paintItems(Framebuffer fb, Simulation sim) {
        LandmarksData.Items items = marks.items();
        if (items.map() == null) {
            return;
        }
        Quest q = sim.quest();
        fb.setClip(field.x(), field.y(), field.w(), field.h());
        try {
            LandmarksData.Item map = items.map();
            if (!q.mapTaken() && LandmarksData.room(map.room()).equals(sim.room())) {
                sprites.get(map.sprite()).blit(fb, field.x() + map.spot().x(), field.y() + map.spot().y());
            }
            for (int i = 0; i < items.eyes().size(); i++) {
                LandmarksData.Eye eye = items.eyes().get(i);
                if (!q.eyeTaken(i) && LandmarksData.room(eye.room()).equals(sim.room())) {
                    String frame = "f" + GLANCE[(int) (sim.tick() / items.glanceTicks() % GLANCE.length)];
                    sprites.get(eye.sprite()).blit(fb, frame, field.x() + eye.spot().x(), field.y() + eye.spot().y());
                }
            }
        } finally {
            fb.clearClip();
        }
    }

    /** Look ahead, right, ahead, left: the eye's frames, a beat each. Drawing only. */
    private static final int[] GLANCE = {0, 1, 0, 2};

    /** The playfield blacked out from the top, a row at a time, over the escape walk (§14.7). */
    public void paintEscape(Framebuffer fb, Simulation sim) {
        if (sim.player().mode() != Player.Mode.ESCAPING) {
            return;
        }
        int rows = field.h() * sim.player().modeTick() / Math.max(1, marks.exit().escapeWalkTicks());
        fb.fillRect(field.x(), field.y(), field.w(), rows, black);
    }

    /**
     * The taken piece flying from the pedestal to the panel; drawn last, over it. A later job's
     * half or whole (§14.9) comes apart on the way, each quarter to its own slot.
     */
    public void paintFlight(Framebuffer fb, Simulation sim, PanelPainter panel) {
        Quest q = sim.quest();
        if (amulet == null || q.flyTicks() <= 0 || q.flySlot() < 0) {
            return;
        }
        int total = marks.amulet().flyToPanelTicks();
        int done = total - q.flyTicks();
        int centreX = field.x() + q.flyFromXPx() - sim.room().col() * Simulation.ROOM_W_PX;
        int centreY = field.y() + q.flyFromYPx() - sim.room().row() * Simulation.ROOM_H_PX;
        int mask = q.flyMask() == 0 ? 1 << q.flySlot() : q.flyMask();
        Bounds b = new Bounds(mask);
        int w = amulet.w();
        int h = amulet.h();
        for (int slot = 0; slot < Quest.SLOTS; slot++) {
            if ((mask & 1 << slot) == 0) {
                continue;
            }
            // Where this quarter sits in the piece, from the piece's centre.
            int fromX = centreX + (slot % 2 - b.minCol) * w - (b.cols - 1) * w / 2;
            int fromY = centreY + (slot / 2 - b.minRow) * h - (b.rows - 1) * h / 2;
            int toX = panel.slotX(slot) + 4;
            int toY = panel.slotY(slot) + 4;
            int x = fromX + (toX - fromX) * done / total;
            int y = fromY + (toY - fromY) * done / total;
            // Blit takes the feet origin: bottom-centre of the 16x16 quarter.
            amulet.blit(fb, frameForSlot(slot), x, y + marks.amulet().size().h() / 2);
        }
    }

    /**
     * A piece filling {@code mask}'s slots, its quarters where they sit in the amulet, the
     * piece's feet at ({@code x}, {@code y}): a quarter, or a later job's half or whole (§14.9).
     */
    private void blitPiece(Framebuffer fb, int mask, int x, int y) {
        Bounds b = new Bounds(mask);
        int w = amulet.w();
        int h = amulet.h();
        for (int slot = 0; slot < Quest.SLOTS; slot++) {
            if ((mask & 1 << slot) != 0) {
                int fx = x - (b.cols - 1) * w / 2 + (slot % 2 - b.minCol) * w;
                int fy = y - (b.maxRow - slot / 2) * h;
                amulet.blit(fb, frameForSlot(slot), fx, fy);
            }
        }
    }

    /** The columns and rows of the panel's 2x2 assembly a piece's slots cover. */
    private static final class Bounds {
        final int minCol;
        final int minRow;
        final int maxRow;
        final int cols;
        final int rows;

        Bounds(int mask) {
            int c0 = 1;
            int c1 = 0;
            int r0 = 1;
            int r1 = 0;
            for (int slot = 0; slot < Quest.SLOTS; slot++) {
                if ((mask & 1 << slot) != 0) {
                    c0 = Math.min(c0, slot % 2);
                    c1 = Math.max(c1, slot % 2);
                    r0 = Math.min(r0, slot / 2);
                    r1 = Math.max(r1, slot / 2);
                }
            }
            minCol = c0;
            minRow = r0;
            maxRow = r1;
            cols = c1 - c0 + 1;
            rows = r1 - r0 + 1;
        }
    }

    public String frameForSlot(int slot) {
        for (LandmarksData.Lair lair : marks.lairs()) {
            if (lair.piece().slot() == slot) {
                return lair.piece().frame();
            }
        }
        throw new IllegalStateException("no amulet quarter for slot " + slot);
    }

    /** Frames by slot, 0..3, for the panel. */
    public static java.util.List<String> framesBySlot(LandmarksData marks) {
        String[] frames = new String[marks.lairs().size()];
        for (LandmarksData.Lair lair : marks.lairs()) {
            frames[lair.piece().slot()] = lair.piece().frame();
        }
        return java.util.List.of(frames);
    }
}
