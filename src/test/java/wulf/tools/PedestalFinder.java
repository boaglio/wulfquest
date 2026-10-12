package wulf.tools;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import wulf.Content;
import wulf.data.CreatureData;
import wulf.data.GuardianData;
import wulf.data.LandmarkValidator;
import wulf.data.LandmarksData;
import wulf.world.CollisionMask;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/**
 * Finds each amulet room's pedestal (AGENTS.md §14.2): the nearest spot to the original's
 * own piece position ({@code $DC6C}, the room type's first spot) where the quarter rests, the
 * guardian fits, and its whole orbit clears the scenery and stays in the room — the usual
 * circle, or where the room is too tight for it the widest that fits ({@code orbitPx}). Prints
 * one {@code "room": {x, y}} line per room of every set in {@code landmarks.json → amuletSets}.
 *
 * <p>{@code java -cp … wulf.tools.PedestalFinder}
 */
public final class PedestalFinder {

    /** The tightest circle worth calling an orbit. */
    private static final int MIN_ORBIT_PX = 8;

    private PedestalFinder() {
    }

    /** The clear spot for a circle of radius {@code r} nearest the game's own ({@code gx}, {@code gy}), or null. */
    private static int[] nearest(LandmarkValidator.Solid grid, RoomAddress room, CreatureData.Box box,
                                 CreatureData.Box amulet, int r, int gx, int gy) {
        int best = Integer.MAX_VALUE;
        int[] at = null;
        for (int y = r; y < CollisionMask.ROWS * 8 - r; y += 2) {
            for (int x = r; x < CollisionMask.COLS * 8 - r; x += 2) {
                int d = (x - gx) * (x - gx) + (y - gy) * (y - gy);
                if (d < best && !LandmarkValidator.blocked(grid, room, amulet, x, y)
                        && LandmarkValidator.orbitClear(grid, room, box, x, y, r)) {
                    best = d;
                    at = new int[] {x, y};
                }
            }
        }
        return at;
    }

    public static void main(String[] args) {
        Content c = Content.load(Path.of("data"));
        WorldGrid world = new WorldGrid(c.rooms());
        LandmarkValidator.Solid grid = world::isSolid;
        GuardianData g = c.guardians();
        CreatureData.Box box = g.guardians().get(0).collisionBox();
        CreatureData.Box amulet = c.landmarks().amulet().collisionBox();
        int full = g.orbit().radiusPx();
        Set<String> rooms = new LinkedHashSet<>();
        for (java.util.List<String> set : c.landmarks().amuletSets().sets()) {
            rooms.addAll(set);
        }
        for (String key : rooms) {
            RoomAddress room = LandmarksData.room(key);
            LandmarksData.Point game = c.treasures().slotsByRoomType().get(c.map().roomType(room)).get(0);
            int gx = game.x() + c.treasures().fromScreen().dx();
            int gy = game.y() + c.treasures().fromScreen().dy();
            for (int r = full; r >= MIN_ORBIT_PX; r -= 2) {
                int[] at = nearest(grid, room, box, amulet, r, gx, gy);
                if (at != null) {
                    System.out.println("      \"" + key + "\": { \"x\": " + at[0] + ", \"y\": " + at[1]
                            + (r < full ? ", \"orbitPx\": " + r : "") + " },");
                    break;
                }
                if (r - 2 < MIN_ORBIT_PX) {
                    System.out.println(key + ": NONE");
                }
            }
        }
    }
}
