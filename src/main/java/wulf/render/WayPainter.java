package wulf.render;

import java.util.List;
import wulf.data.DisplayConfig;
import wulf.data.LandmarksData;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.sim.Quest;
import wulf.sim.Simulation;
import wulf.world.CollisionMask;
import wulf.world.RoomAddress;
import wulf.world.Wayfinder;

/**
 * The way a looking glass shows (AGENTS.md §14.10, {@code [NEW]}): from where Vale stands to
 * the nearest quarter still out there — or, the amulet whole, to the arch — walked through
 * the jungle by {@link Wayfinder}. In the jungle, an arrow at the edge of the room he should
 * leave by; the map draws the rest of it from {@link #route}.
 *
 * <p>Drawing only, worked out from what the simulation already holds: the simulation keeps
 * nothing but the glasses taken and the time left ({@link Quest#wayTicks()}).
 */
public final class WayPainter {

    private final DisplayConfig.Playfield field;
    private final Wayfinder finder;
    private final PlayerData.Box feet;
    private final LandmarksData.Glass glass;
    private final Sprite arrow;

    /** The field to the current targets, kept until a quarter is taken or the game changes. */
    private LandmarksData fieldFor;
    private int fieldTaken = -1;
    private int[] dist;
    /** The last route, kept while Vale stays in the same cell. */
    private int routeFrom = -1;
    private int[] route = new int[0];

    public WayPainter(DisplayConfig display, SpriteBank sprites, LandmarksData marks, PlayerData.Box feet,
                      Wayfinder finder) {
        this.field = display.playfield();
        this.finder = finder;
        this.feet = feet;
        this.glass = marks.items().glass();
        this.arrow = glass.places().isEmpty() ? null : sprites.get(glass.arrow().sprite());
    }

    /** Whether the way is showing now. */
    public static boolean showing(Simulation sim) {
        return sim.quest().wayTicks() > 0;
    }

    /**
     * The cells of the way, packed as {@link Wayfinder#cell}, from Vale's feet to the target;
     * empty when there is none to show.
     */
    public int[] route(Simulation sim) {
        if (!showing(sim)) {
            return new int[0];
        }
        LandmarksData marks = sim.landmarks();
        Quest q = sim.quest();
        int takenMask = 0;
        for (int i = 0; i < marks.lairs().size(); i++) {
            takenMask |= q.taken(i) ? 1 << i : 0;
        }
        if (marks != fieldFor || takenMask != fieldTaken) {
            dist = finder.distances(targets(marks, q));
            fieldFor = marks;
            fieldTaken = takenMask;
            routeFrom = -1;
        }
        int gx = Math.floorDiv(Fixed.px(sim.player().xFp()) + feet.x(), CELL_PX);
        int gy = Math.floorDiv(Fixed.px(sim.player().yFp()) + feet.y(), CELL_PX);
        int from = finder.nearest(dist, gx, gy, NEAR_CELLS);
        if (from != routeFrom) {
            route = finder.path(dist, from);
            routeFrom = from;
        }
        return route;
    }

    /** Where the feet would stand at each quarter still out there, or, none left, in the arch's mouth. */
    private int[] targets(LandmarksData marks, Quest q) {
        List<LandmarksData.Lair> lairs = marks.lairs();
        int[] out = new int[lairs.size()];
        int n = 0;
        for (int i = 0; i < lairs.size(); i++) {
            if (!q.taken(i)) {
                LandmarksData.Lair lair = lairs.get(i);
                out[n++] = cellAt(LandmarksData.room(lair.room()), lair.pedestal().x(), lair.pedestal().y());
            }
        }
        if (n == 0) {
            LandmarksData.Rect zone = marks.exit().zone();
            return new int[] {cellAt(marks.exitRoom(), zone.x() + zone.w() / 2, zone.y() + zone.h())};
        }
        return java.util.Arrays.copyOf(out, n);
    }

    private int cellAt(RoomAddress room, int localX, int localY) {
        int gx = Math.floorDiv(room.col() * Simulation.ROOM_W_PX + localX + feet.x(), CELL_PX);
        int gy = Math.floorDiv(room.row() * Simulation.ROOM_H_PX + localY + feet.y(), CELL_PX);
        return finder.nearest(null, gx, gy, NEAR_CELLS);
    }

    /**
     * The arrow at the edge of the room where the way leaves it, blinking — faster once time is
     * short. Nothing when the way ends in this room.
     */
    public void paintArrow(Framebuffer fb, Simulation sim) {
        if (arrow == null || !showing(sim)) {
            return;
        }
        int ticks = sim.quest().wayTicks();
        LandmarksData.Arrow a = glass.arrow();
        int blink = ticks <= glass.warnTicks() ? a.warnBlinkTicks() : a.blinkTicks();
        if ((sim.tick() / blink) % 2 != 0) {
            return;
        }
        int[] way = route(sim);
        RoomAddress room = sim.room();
        for (int i = 1; i < way.length; i++) {
            int gx = Wayfinder.gx(way[i]);
            int gy = Wayfinder.gy(way[i]);
            if (gx / CollisionMask.COLS == room.col() && gy / CollisionMask.ROWS == room.row()) {
                continue;
            }
            int lx = Wayfinder.gx(way[i - 1]) - room.col() * CollisionMask.COLS;
            int ly = Wayfinder.gy(way[i - 1]) - room.row() * CollisionMask.ROWS;
            int dx = gx - Wayfinder.gx(way[i - 1]);
            int dy = gy - Wayfinder.gy(way[i - 1]);
            String frame = dx > 0 ? "e" : dx < 0 ? "w" : dy > 0 ? "s" : "n";
            int inset = a.insetPx();
            // The middle of the feet where they cross, held clear of the corners.
            int x = clamp(lx * CELL_PX + CELL_PX, inset, field.w() - inset);
            int y = clamp(ly * CELL_PX + CELL_PX, inset, field.h() - inset);
            switch (frame) {
                case "e" -> x = field.w() - inset;
                case "w" -> x = inset;
                case "s" -> y = field.h() - inset;
                default -> y = inset;
            }
            fb.setClip(field.x(), field.y(), field.w(), field.h());
            try {
                arrow.blit(fb, frame, field.x() + x, field.y() + y + arrow.h() / 2);
            } finally {
                fb.clearClip();
            }
            return;
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static final int CELL_PX = 8;
    /** How far from a point to look for somewhere the feet fit: a pedestal, or feet half in a wall. */
    private static final int NEAR_CELLS = 3;
}
