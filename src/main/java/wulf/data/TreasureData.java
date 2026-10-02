package wulf.data;

import java.util.List;
import java.util.Map;
import java.util.Set;
import wulf.world.RoomAddress;

/**
 * Bound view of {@code data/world/treasures.json} (AGENTS.md §16.4, {@code [CANON]}): the
 * odds and ends the original scatters through the jungle at the start of every game —
 * crates, rings, fruit, caps, shields, money bags, swords, and now and then a life.
 *
 * <p>Each room has two places a treasure may lie, chosen from its room type's four
 * {@code slotsByRoomType} spots, which are the original's screen coordinates: the
 * sprite's left edge and bottom row, the banner included. {@code fromScreen} turns
 * one into a room-local feet point. Each place holds a treasure {@code fillPercent} of
 * the time, of a kind picked evenly from {@code kinds}.
 */
public record TreasureData(
        int schemaVersion,
        String fidelity,
        String source,
        int fillPercent,
        String fillPercentFidelity,
        int score,
        CreatureData.Box pickupBox,
        String sprite,
        Offset fromScreen,
        List<Kind> kinds,
        List<List<LandmarksData.Point>> slotsByRoomType) {

    private static final String FILE = "data/world/treasures.json";
    /** The places per room the original keeps ({@code $DDEC}: two bytes a room). */
    public static final int PLACES_PER_ROOM = 2;
    /** The spots per room type the original keeps ({@code $DC6C}: eight bytes a type). */
    public static final int SPOTS_PER_TYPE = 4;
    /** The sprite box a treasure is drawn in, for checking a spot is clear: 16x16, origin bottom-centre. */
    private static final CreatureData.Box SPRITE_BOX = new CreatureData.Box(-8, -16, 16, 16);

    public TreasureData {
        kinds = List.copyOf(kinds);
        slotsByRoomType = slotsByRoomType.stream().map(List::copyOf).toList();
    }

    public record Offset(int dx, int dy) {
    }

    /** @param effect {@code score}, or {@code extraLife}: a life as well, up to the most Vale may have */
    public record Kind(String frame, String ink, String effect) {
        public boolean extraLife() {
            return "extraLife".equals(effect);
        }
    }

    /** What the schema cannot say: one slot list per room type, sprites and colours that exist. */
    public void check(OriginalMapRepository map, Palette palette, Map<String, Set<String>> framesBySprite) {
        if (slotsByRoomType.size() != map.templateCount()) {
            throw new DataException(FILE, "/slotsByRoomType",
                    "has " + slotsByRoomType.size() + " room types; the map has " + map.templateCount());
        }
        Set<String> frames = framesBySprite.get(sprite);
        if (frames == null) {
            throw new DataException(FILE, "/sprite", "names sprite '" + sprite + "', which is not in data/art/sprites/index.json");
        }
        for (int i = 0; i < kinds.size(); i++) {
            Kind k = kinds.get(i);
            if (!frames.contains(k.frame())) {
                throw new DataException(FILE, "/kinds/" + i + "/frame", "is '" + k.frame() + "', which sprite '" + sprite
                        + "' has no frame for");
            }
            if (palette.entries().stream().noneMatch(e -> e.name().equals(k.ink()))) {
                throw new DataException(FILE, "/kinds/" + i + "/ink", "is '" + k.ink() + "', which is not a palette colour");
            }
        }
    }

    /**
     * Where each room's places lie, room-local feet, by {@code room.index() * 2 + place}:
     * {@code x[i] == Integer.MIN_VALUE} where there is none — no spot for that room type,
     * a spot inside the scenery, or a room in {@code keepClear}.
     *
     * <p>The original fills its object table with a counter running down from 512, and the
     * counter's low two bits pick the spot ({@code $A2DC}): place 0 of an even room takes
     * spot 0 and place 1 spot 3; an odd room's take 2 and 1. Two treasures never share one.
     */
    public int[][] places(OriginalMapRepository map, LandmarkValidator.Solid solid, Set<RoomAddress> keepClear) {
        int rooms = RoomAddress.GRID_W * RoomAddress.GRID_H;
        int[] xs = new int[rooms * PLACES_PER_ROOM];
        int[] ys = new int[rooms * PLACES_PER_ROOM];
        java.util.Arrays.fill(xs, Integer.MIN_VALUE);
        for (int r = 0; r < rooms; r++) {
            RoomAddress room = new RoomAddress(r % RoomAddress.GRID_W, r / RoomAddress.GRID_W);
            if (keepClear.contains(room)) {
                continue;
            }
            List<LandmarksData.Point> spots = slotsByRoomType.get(map.roomType(room));
            for (int place = 0; place < PLACES_PER_ROOM; place++) {
                int counter = rooms * PLACES_PER_ROOM - (r * PLACES_PER_ROOM + place);
                int spot = counter & (SPOTS_PER_TYPE - 1);
                if (spot >= spots.size()) {
                    continue;
                }
                int x = spots.get(spot).x() + fromScreen.dx();
                int y = spots.get(spot).y() + fromScreen.dy();
                if (LandmarkValidator.blocked(solid, room, SPRITE_BOX, x, y)) {
                    continue;
                }
                xs[r * PLACES_PER_ROOM + place] = x;
                ys[r * PLACES_PER_ROOM + place] = y;
            }
        }
        return new int[][] {xs, ys};
    }
}
