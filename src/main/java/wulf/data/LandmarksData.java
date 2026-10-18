package wulf.data;

import java.util.ArrayList;
import java.util.List;
import wulf.world.RoomAddress;

/**
 * Bound view of {@code data/world/landmarks.json} (AGENTS.md §14): the four quarters
 * and their guardians, the rooms the original may hide them in, where the way out is,
 * and which rooms hold a shrine.
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
        AmuletSets amuletSets,
        Amulet amulet,
        Shrines shrines,
        Items items,
        Hint hint,
        Temple temple) {

    public LandmarksData {
        lairs = List.copyOf(lairs);
    }

    /**
     * Where the original hides the amulet (§14.2, {@code [CANON]}): {@code Place Amulet Pieces}
     * ({@code $A255}) picks one of eight preset {@code sets} of four rooms ({@code $A29D}) at the
     * start of every game. Entry {@code j} of a set holds the quarter of lair {@code quarters[j]};
     * each room's guardian circles its {@code pedestals} spot.
     *
     * @param sets      each four {@code "col,row"} rooms, in the original's byte order
     * @param quarters  the lair id whose quarter each entry of a set holds
     * @param pedestals by room: where the quarter rests and the guardian circles, room-local feet
     */
    public record AmuletSets(String source, List<String> quarters, List<List<String>> sets,
                             java.util.Map<String, Pedestal> pedestals) {
        public AmuletSets {
            quarters = List.copyOf(quarters);
            sets = sets.stream().map(List::copyOf).toList();
            pedestals = java.util.Map.copyOf(pedestals);
        }

        public static final AmuletSets NONE = new AmuletSets("none", List.of(), List.of(), java.util.Map.of());
    }

    /**
     * Where a quarter rests, room-local feet, and its guardian circles round it.
     *
     * @param orbitPx the circle's radius where the room is too tight for {@code guardians.json → orbit.radiusPx};
     *                null for that one
     */
    public record Pedestal(int x, int y, Integer orbitPx) {
        public Point point() {
            return new Point(x, y);
        }
    }

    /**
     * The landmarks with the amulet laid out as set {@code set} lays it: every lair given its
     * room and pedestal. The game picks the set (§14.2); tests and tools may name one.
     */
    public LandmarksData withAmuletSet(int set) {
        List<String> rooms = amuletSets.sets().get(set);
        List<Lair> placed = new ArrayList<>(lairs.size());
        for (Lair lair : lairs) {
            int entry = amuletSets.quarters().indexOf(lair.id());
            String room = rooms.get(entry);
            Pedestal p = amuletSets.pedestals().get(room);
            placed.add(new Lair(lair.id(), room, lair.guardian(), lair.piece(), p.point(), p.orbitPx()));
        }
        return new LandmarksData(schemaVersion, fidelity, startFacing, exit, placed, amuletSets, amulet, shrines,
                items, hint, temple);
    }

    /**
     * §14.9: only {@code kept} of the lairs, in their order, the {@code j}-th holding a piece that
     * fills {@code masks[j]}'s slots. The rest hold nothing this game, and no guardian waits there.
     */
    public LandmarksData keeping(int[] kept, int[] masks) {
        List<Lair> some = new ArrayList<>(kept.length);
        for (int j = 0; j < kept.length; j++) {
            some.add(lairs.get(kept[j]).holding(masks[j]));
        }
        return new LandmarksData(schemaVersion, fidelity, startFacing, exit, some, amuletSets, amulet, shrines,
                items, hint, temple);
    }

    /** Whether every lair has its room: false for the data as loaded, before a set is picked. */
    public boolean placed() {
        return lairs.stream().allMatch(l -> l.room() != null);
    }

    /** The set the lairs were laid out from, or -1 before one is picked. */
    public int amuletSet() {
        if (lairs.isEmpty() || !placed()) {
            return -1;
        }
        // By room alone: a later job (§14.9) keeps only some of the set's lairs.
        for (int k = 0; k < amuletSets.sets().size(); k++) {
            List<String> rooms = amuletSets.sets().get(k);
            boolean all = true;
            for (Lair lair : lairs) {
                all &= rooms.get(amuletSets.quarters().indexOf(lair.id())).equals(lair.room());
            }
            if (all) {
                return k;
            }
        }
        return -1;
    }

    /**
     * What can be found besides the amulet (§14.8, {@code [NEW]}): the map scroll, which
     * shows every room on the map screen, and the eyes, each of which marks the lairs it
     * names there. Each lies at {@code spot}, room-local feet, and is taken by touching
     * {@code pickupBox} around it. An eye lying in a room glances about, a look every
     * {@code glanceTicks} (drawing only).
     */
    public record Items(CreatureData.Box pickupBox, int glanceTicks, Item map, List<Eye> eyes, Glass glass) {
        public Items {
            eyes = List.copyOf(eyes);
            glass = glass == null ? Glass.NONE : glass;
        }

        public Items(CreatureData.Box pickupBox, int glanceTicks, Item map, List<Eye> eyes) {
            this(pickupBox, glanceTicks, map, eyes, Glass.NONE);
        }

        public static final Items NONE = new Items(new CreatureData.Box(0, 0, 1, 1), 1, null, List.of());
    }

    /**
     * The looking glasses (§14.10, {@code [NEW]}): each lies at one of {@code places}, once a
     * game, and taken shows the way to the nearest quarter still out — or, the amulet whole, to
     * the arch — for {@code showWayTicks}, as an arrow at the room's way out and a trail on the
     * map. For its last {@code warnTicks} the arrow blinks faster. {@code wholePlaces} are more
     * glasses that lie only once the amulet is whole, to lead the way out.
     */
    public record Glass(String sprite, int showWayTicks, int warnTicks, Arrow arrow, List<Place> places,
                        List<Place> wholePlaces) {
        public Glass {
            places = List.copyOf(places);
            wholePlaces = wholePlaces == null ? List.of() : List.copyOf(wholePlaces);
        }

        public Glass(String sprite, int showWayTicks, int warnTicks, Arrow arrow, List<Place> places) {
            this(sprite, showWayTicks, warnTicks, arrow, places, List.of());
        }

        public static final Glass NONE = new Glass("", 1, 0, new Arrow("", 1, 1, 0), List.of());

        /** Every glass, those of {@code places} first: glass {@code i} of the quest is the {@code i}th. */
        public List<Place> all() {
            List<Place> out = new java.util.ArrayList<>(places);
            out.addAll(wholePlaces);
            return out;
        }

        /** Whether glass {@code i} of {@link #all()} lies in the jungle: always, or once the amulet is whole. */
        public boolean lies(int i, boolean whole) {
            return i < places.size() || whole;
        }
    }

    /**
     * The glass's arrow (§14.10): {@code sprite} has a frame for each way out, {@code n},
     * {@code e}, {@code s}, {@code w}, drawn {@code insetPx} inside the playfield's edge, on for
     * {@code blinkTicks} and off as long — {@code warnBlinkTicks} when time is short.
     */
    public record Arrow(String sprite, int blinkTicks, int warnBlinkTicks, int insetPx) {
    }

    /** Where a thing lies: a room and a spot on its ground, room-local feet. */
    public record Place(String room, Point spot) {
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

    /**
     * One quarter of the amulet and its guardian. {@code room}, {@code pedestal} and {@code orbitPx}
     * are not in the file: they come from the set the game picks ({@link #withAmuletSet(int)}), null before.
     *
     * @param orbitPx the guardian's circle here, when the room is too tight for its usual one; else null
     * @param slots   the panel slots its piece fills, as a mask, when a later job (§14.9) breaks the amulet
     *                into fewer pieces; null for its own quarter's one
     */
    public record Lair(String id, String room, String guardian, Piece piece, Point pedestal, Integer orbitPx,
                       Integer slots) {

        public Lair(String id, String room, String guardian, Piece piece, Point pedestal, Integer orbitPx) {
            this(id, room, guardian, piece, pedestal, orbitPx, null);
        }

        /** Bit {@code slot} set for every slot of the panel this lair's piece fills. */
        public int slotMask() {
            return slots == null ? 1 << piece.slot() : slots;
        }

        /** The same lair holding a piece that fills {@code mask}'s slots (§14.9). */
        public Lair holding(int mask) {
            return new Lair(id, room, guardian, piece, pedestal, orbitPx, mask);
        }
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
            new Exit("0,0", "0000", new Rect(0, 0, 1, 1), new Point(0, 0), 4, 1), List.of(), AmuletSets.NONE,
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
