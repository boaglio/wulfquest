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
        List<String> caveMouths,
        Hint hint,
        StillWater stillWater) {

    public LandmarksData {
        lairs = List.copyOf(lairs);
        caveMouths = List.copyOf(caveMouths);
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
    public record StillWater(String name, List<String> rooms) {
        public StillWater {
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
            List.of(), new Hint(1, true), new StillWater("none", List.of()));

    public boolean isCaveMouth(RoomAddress address) {
        for (String key : caveMouths) {
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
