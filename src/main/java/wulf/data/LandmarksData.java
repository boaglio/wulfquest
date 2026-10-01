package wulf.data;

import java.util.ArrayList;
import java.util.List;
import wulf.world.RoomAddress;

/**
 * Bound view of {@code data/world/landmarks.json} (AGENTS.md §14): where the four
 * lairs and their pieces are, where the way out is, and which rooms hold a shrine.
 *
 * <p>The start room itself is not here — it is extracted canon, in
 * {@code original_map.json} (§8) — only the direction Vale faces at the start.
 */
public record LandmarksData(
        int schemaVersion,
        String fidelity,
        String startFacing,
        Exit exit,
        List<Lair> lairs,
        Amulet amulet,
        Shrines shrines,
        Items items,
        Hint hint,
        Temple temple) {

    public LandmarksData {
        lairs = List.copyOf(lairs);
    }

    /**
     * What can be found besides the amulet (§14.8, {@code [NEW]}): the map scroll, which
     * shows every room on the map screen, and the eyes, each of which marks the lairs it
     * names there. Each lies at {@code spot}, room-local feet, and is taken by touching
     * {@code pickupBox} around it. An eye lying in a room glances about, a look every
     * {@code glanceTicks} (drawing only).
     */
    public record Items(CreatureData.Box pickupBox, int glanceTicks, Item map, List<Eye> eyes) {
        public Items {
            eyes = List.copyOf(eyes);
        }

        public static final Items NONE = new Items(new CreatureData.Box(0, 0, 1, 1), 1, null, List.of());
    }

    public record Item(String room, Point spot, String sprite) {
    }

    /** @param reveals lair ids this eye marks on the map (§14.8) */
    public record Eye(String room, Point spot, String sprite, List<String> reveals) {
        public Eye {
            reveals = List.copyOf(reveals);
        }
    }

    /**
     * Where a player can ask the way (§14.5, {@code [NEW]}): stand in {@code zone}, room-local,
     * in front of {@code object} — the same spot in every room of the list, since those rooms
     * share the object's template — and the panel names the way to the nearest quarter.
     */
    public record Shrines(String object, Rect zone, List<String> rooms) {
        public Shrines {
            rooms = List.copyOf(rooms);
        }
    }

    /**
     * @param arch           the scenery id of the stone arch that is the way out
     * @param zone           the room-local rectangle in front of the arch: stand here with the
     *                       whole amulet and the game is won (the arch's own cells are solid)
     * @param keeper         where the Keeper stands, room-local, feet
     * @param requiresPieces  quarters needed before it steps aside
     * @param escapeWalkTicks how long Vale takes to walk into the arch once the game is won (§14.7)
     */
    public record Exit(String room, String arch, Rect zone, Point keeper, int requiresPieces, int escapeWalkTicks) {
    }

    public record Lair(String id, String room, String guardian, Piece piece, Point pedestal) {
    }

    /**
     * @param frame the amulet sprite's frame for this quarter
     * @param slot  its place in the panel's 2x2 assembly, 0..3 reading across
     */
    public record Piece(String id, String frame, int slot) {
    }

    public record Amulet(String sprite, CreatureData.Size size, CreatureData.Box collisionBox, int pickupFlashTicks,
                         int flyToPanelTicks) {
    }

    public record Hint(int messageTicks, boolean oncePerLife) {
    }

    /** The central lake the map is built around (§8.2). Flavour: nothing reads it yet. */
    public record Temple(String name, List<String> rooms) {
        public Temple {
            rooms = List.copyOf(rooms);
        }
    }

    public record Rect(int x, int y, int w, int h) {
    }

    public record Point(int x, int y) {
    }

    /** Parses a {@code "col,row"} room key. */
    public static RoomAddress room(String key) {
        int comma = key.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("a room key is \"col,row\", got \"" + key + "\"");
        }
        return new RoomAddress(Integer.parseInt(key.substring(0, comma).trim()),
                Integer.parseInt(key.substring(comma + 1).trim()));
    }

    public RoomAddress exitRoom() {
        return room(exit.room());
    }

    public List<RoomAddress> lairRooms() {
        List<RoomAddress> rooms = new ArrayList<>(lairs.size());
        for (Lair lair : lairs) {
            rooms.add(room(lair.room()));
        }
        return rooms;
    }

    /** The lair in this room, or null when it holds no lair. */
    public Lair lairIn(RoomAddress address) {
        for (Lair lair : lairs) {
            if (room(lair.room()).equals(address)) {
                return lair;
            }
        }
        return null;
    }

    /** The index into {@link #lairs()} of the lair in this room, or -1. */
    public int lairIndexIn(RoomAddress address) {
        for (int i = 0; i < lairs.size(); i++) {
            if (room(lairs.get(i).room()).equals(address)) {
                return i;
            }
        }
        return -1;
    }

    /** No quest: an inert set of landmarks for simulations without one. */
    public static final LandmarksData NONE = new LandmarksData(1, "none", "N",
            new Exit("0,0", "0000", new Rect(0, 0, 1, 1), new Point(0, 0), 4, 1), List.of(),
            new Amulet("amulet_piece", new CreatureData.Size(1, 1), new CreatureData.Box(0, 0, 1, 1), 0, 1),
            new Shrines("0000", new Rect(0, 0, 1, 1), List.of()), Items.NONE, new Hint(1, true),
            new Temple("none", List.of()));

    public boolean isShrine(RoomAddress address) {
        for (String key : shrines.rooms()) {
            if (room(key).equals(address)) {
                return true;
            }
        }
        return false;
    }

    public Direction8Name startFacingName() {
        return Direction8Name.valueOf(startFacing);
    }

    /** The compass names {@code startFacing} may use, mirroring the simulation's own directions. */
    public enum Direction8Name { N, NE, E, SE, S, SW, W, NW }
}
