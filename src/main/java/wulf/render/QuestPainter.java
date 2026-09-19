package wulf.render;

import wulf.data.DisplayConfig;
import wulf.data.LandmarksData;
import wulf.sim.Player;
import wulf.sim.Quest;
import wulf.sim.Simulation;

/**
 * What the quest adds to the screen (AGENTS.md §14): the quarter waiting on its
 * pedestal, the quarter flying to its panel slot once taken, and the playfield
 * going dark row by row as Vale walks into the arch.
 */
public final class QuestPainter {

    private final DisplayConfig.Playfield field;
    private final LandmarksData marks;
    private final Sprite amulet;
    private final int black;

    public QuestPainter(DisplayConfig display, SpriteBank sprites, LandmarksData marks, int black) {
        this.field = display.playfield();
        this.marks = marks;
        this.amulet = marks.lairs().isEmpty() ? null : sprites.get(marks.amulet().sprite());
        this.black = black;
    }

    /** The quarter on its pedestal, under the creatures (§5.3: loot before creatures). */
    public void paintLoot(Framebuffer fb, Simulation sim) {
        Quest q = sim.quest();
        if (amulet == null || !q.inLair() || q.taken(q.lair())) {
            return;
        }
        LandmarksData.Lair lair = marks.lairs().get(q.lair());
        fb.setClip(field.x(), field.y(), field.w(), field.h());
        try {
            amulet.blit(fb, lair.piece().frame(), field.x() + lair.pedestal().x(), field.y() + lair.pedestal().y());
        } finally {
            fb.clearClip();
        }
    }

    /** The playfield blacked out from the top, a row at a time, over the escape walk (§14.7). */
    public void paintEscape(Framebuffer fb, Simulation sim) {
        if (sim.player().mode() != Player.Mode.ESCAPING) {
            return;
        }
        int rows = field.h() * sim.player().modeTick() / Math.max(1, marks.exit().escapeWalkTicks());
        fb.fillRect(field.x(), field.y(), field.w(), rows, black);
    }

    /** The taken quarter, flying from the pedestal to its slot; drawn last, over the panel. */
    public void paintFlight(Framebuffer fb, Simulation sim, PanelPainter panel) {
        Quest q = sim.quest();
        if (amulet == null || q.flyTicks() <= 0 || q.flySlot() < 0) {
            return;
        }
        int total = marks.amulet().flyToPanelTicks();
        int done = total - q.flyTicks();
        int fromX = field.x() + q.flyFromXPx() - sim.room().col() * Simulation.ROOM_W_PX;
        int fromY = field.y() + q.flyFromYPx() - sim.room().row() * Simulation.ROOM_H_PX;
        int toX = panel.slotX(q.flySlot()) + 4;
        int toY = panel.slotY(q.flySlot()) + 4;
        int x = fromX + (toX - fromX) * done / total;
        int y = fromY + (toY - fromY) * done / total;
        String frame = frameForSlot(q.flySlot());
        LandmarksData.Amulet a = marks.amulet();
        // Blit takes the feet origin: bottom-centre of the 16x16 quarter.
        amulet.blit(fb, frame, x, y + a.size().h() / 2);
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
