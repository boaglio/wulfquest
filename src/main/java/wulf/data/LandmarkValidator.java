package wulf.data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import wulf.world.CollisionMask;
import wulf.world.RoomAddress;

/**
 * What the schema cannot say about {@code landmarks.json} (AGENTS.md §14.2, §20.6):
 * that the rooms exist, that the arch is really in them, and that a guardian, a
 * piece, the Keeper and the player all fit where they are asked to stand.
 *
 * <p>The ±4 balance of the four lairs' distances from the start needs a flood of the
 * whole world, so it lives in {@code MapAudit} (§22.3) and its test.
 */
public final class LandmarkValidator {

    private static final String FILE = "data/world/landmarks.json";

    /** Solidity by world cell: {@code WorldGrid} without the dependency. */
    public interface Solid {
        boolean at(int gx, int gy);
    }

    private LandmarkValidator() {
    }

    public static void check(LandmarksData marks, GuardianData wardens, CreatureData creatures,
                             OriginalMapRepository map, Set<String> sceneryIds, Map<String, Set<String>> framesBySprite,
                             Solid solid) {
        if (!sceneryIds.contains(marks.exit().arch())) {
            throw new DataException(FILE, "/exit/arch",
                    "is '" + marks.exit().arch() + "', which is not a scenery object in data/world/scenery.json");
        }
        RoomAddress exit = room(marks.exit().room(), "/exit/room");
        if (!hasObject(map, exit, marks.exit().arch())) {
            throw new DataException(FILE, "/exit/room",
                    "is " + exit + ", which has no '" + marks.exit().arch() + "' arch in it");
        }
        if (exit.equals(map.startRoom())) {
            throw new DataException(FILE, "/exit/room", "is the start room; the way out must be found, not stood on");
        }
        checkFrames(framesBySprite, "/amulet/sprite", marks.amulet().sprite(),
                pieceFrames(marks));

        // The exit zone: inside the room, and somewhere in it the player can actually stand.
        LandmarksData.Rect zone = marks.exit().zone();
        if (zone.x() + zone.w() > CollisionMask.COLS * 8 || zone.y() + zone.h() > CollisionMask.ROWS * 8) {
            throw new DataException(FILE, "/exit/zone", "runs off the room");
        }
        if (!anyStandingSpot(solid, exit, zone)) {
            throw new DataException(FILE, "/exit/zone", "is solid everywhere: the way out could never be reached");
        }
        CreatureData.Box keeperBox = wardens.keeper().collisionBox();
        LandmarksData.Point keeper = marks.exit().keeper();
        if (blocked(solid, exit, keeperBox, keeper.x(), keeper.y())) {
            throw new DataException(FILE, "/exit/keeper",
                    "is (" + keeper.x() + "," + keeper.y() + "), where the Keeper does not fit");
        }
        for (int asideX : new int[] {wardens.keeper().asideMinPx(), wardens.keeper().asideMaxPx()}) {
            if (blocked(solid, exit, keeperBox, asideX, keeper.y())) {
                throw new DataException(FILE, "/exit/keeper", "leaves the Keeper nowhere to step aside to: ("
                        + asideX + "," + keeper.y() + ") is solid (guardians.json /keeper/asideMinPx, asideMaxPx)");
            }
        }

        Set<String> lairIds = new HashSet<>();
        Set<String> pieceIds = new HashSet<>();
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < marks.lairs().size(); i++) {
            LandmarksData.Lair lair = marks.lairs().get(i);
            String at = "/lairs/" + i;
            if (!lairIds.add(lair.id())) {
                throw new DataException(FILE, at + "/id", "repeats '" + lair.id() + "'");
            }
            if (!pieceIds.add(lair.piece().id())) {
                throw new DataException(FILE, at + "/piece/id", "repeats '" + lair.piece().id() + "'");
            }
            if (!slots.add(lair.piece().slot())) {
                throw new DataException(FILE, at + "/piece/slot",
                        "repeats slot " + lair.piece().slot() + "; the four quarters fill four slots");
            }
            guardian(wardens, lair.guardian(), at + "/guardian");
        }
        Set<RoomAddress> amuletRooms = checkAmuletSets(marks, wardens, map, exit, lairIds, solid);
        if (creatures.creatures().isEmpty()) {
            throw new DataException("data/entities/creatures.json", "/creatures", "is empty");
        }
        LandmarksData.Shrines shrines = marks.shrines();
        LandmarksData.Rect shrineZone = shrines.zone();
        if (shrineZone.x() + shrineZone.w() > CollisionMask.COLS * 8 || shrineZone.y() + shrineZone.h() > CollisionMask.ROWS * 8) {
            throw new DataException(FILE, "/shrines/zone", "runs off the room");
        }
        Set<RoomAddress> taken = new HashSet<>();
        taken.add(exit);
        taken.add(map.startRoom());
        for (int i = 0; i < shrines.rooms().size(); i++) {
            RoomAddress shrine = room(shrines.rooms().get(i), "/shrines/rooms/" + i);
            if (!hasObject(map, shrine, shrines.object())) {
                throw new DataException(FILE, "/shrines/rooms/" + i,
                        "is " + shrine + ", which has no '" + shrines.object() + "' in it");
            }
            if (taken.contains(shrine)) {
                throw new DataException(FILE, "/shrines/rooms/" + i,
                        "is " + shrine + ", already the start or the way out");
            }
            if (!anyStandingSpot(solid, shrine, shrineZone)) {
                throw new DataException(FILE, "/shrines/zone", "is solid everywhere in " + shrine);
            }
        }
        // A shrine may be where a game hides a quarter: it is quiet in that game (§14.5). Not an item.
        taken.addAll(amuletRooms);
        checkItems(marks, framesBySprite, solid, taken);
        Set<RoomAddress> busy = new HashSet<>(taken);
        for (String shrine : shrines.rooms()) {
            busy.add(LandmarksData.room(shrine));
        }
        checkGlass(marks, framesBySprite, solid, busy);
        for (int i = 0; i < marks.temple().rooms().size(); i++) {
            room(marks.temple().rooms().get(i), "/temple/rooms/" + i);
        }
        marks.startFacingName();
    }

    /**
     * §14.2: every set is four real rooms — none the start or the way out — each with a pedestal
     * where the quarter rests and every guardian fits and circles clear of the scenery; every
     * pedestal belongs to a set's room; and the quarters name each lair once.
     *
     * @return every room some set hides a quarter in
     */
    private static Set<RoomAddress> checkAmuletSets(LandmarksData marks, GuardianData wardens,
                                                    OriginalMapRepository map, RoomAddress exit, Set<String> lairIds,
                                                    Solid solid) {
        LandmarksData.AmuletSets sets = marks.amuletSets();
        if (!new HashSet<>(sets.quarters()).equals(lairIds)) {
            throw new DataException(FILE, "/amuletSets/quarters", "is " + sets.quarters() + ", not each lair once " + lairIds);
        }
        Set<RoomAddress> all = new java.util.LinkedHashSet<>();
        Set<String> used = new HashSet<>();
        for (int k = 0; k < sets.sets().size(); k++) {
            for (int j = 0; j < sets.sets().get(k).size(); j++) {
                String key = sets.sets().get(k).get(j);
                String at = "/amuletSets/sets/" + k + "/" + j;
                RoomAddress room = room(key, at);
                if (room.equals(map.startRoom()) || room.equals(exit)) {
                    throw new DataException(FILE, at, "is " + room + ", which is already the start or the way out");
                }
                LandmarksData.Pedestal pedestal = sets.pedestals().get(key);
                if (pedestal == null) {
                    throw new DataException(FILE, "/amuletSets/pedestals", "has no pedestal for room " + key);
                }
                used.add(key);
                if (!all.add(room)) {
                    continue;
                }
                String where = "/amuletSets/pedestals/" + key;
                int reach = pedestal.orbitPx() != null ? pedestal.orbitPx() : wardens.orbit().radiusPx();
                if (pedestal.orbitPx() != null && pedestal.orbitPx() >= wardens.orbit().radiusPx()) {
                    throw new DataException(FILE, where + "/orbitPx", "is no tighter than guardians.json's "
                            + wardens.orbit().radiusPx() + " px: leave it out");
                }
                if (pedestal.x() - reach < 0 || pedestal.x() + reach >= CollisionMask.COLS * 8
                        || pedestal.y() - reach < 0 || pedestal.y() + reach >= CollisionMask.ROWS * 8) {
                    throw new DataException(FILE, where, "leaves the guardian's " + reach + " px orbit off the room");
                }
                if (blocked(solid, room, marks.amulet().collisionBox(), pedestal.x(), pedestal.y())) {
                    throw new DataException(FILE, where, "is solid where the amulet quarter would rest");
                }
                for (GuardianData.Guardian g : wardens.guardians()) {
                    if (!orbitClear(solid, room, g.collisionBox(), pedestal.x(), pedestal.y(), reach)) {
                        throw new DataException(FILE, where, "is (" + pedestal.x() + "," + pedestal.y() + "), where "
                                + g.id() + " cannot circle clear of the scenery");
                    }
                }
            }
        }
        for (String key : sets.pedestals().keySet()) {
            if (!used.contains(key)) {
                throw new DataException(FILE, "/amuletSets/pedestals/" + key, "is a room no set hides a quarter in");
            }
        }
        return all;
    }

    /** Points tried round an orbit by {@link #orbitClear}. */
    private static final int ORBIT_POINTS = 32;

    /** Whether {@code box} fits at ({@code px}, {@code py}), room-local, and at every point of a circle of radius {@code r} round it. */
    public static boolean orbitClear(Solid solid, RoomAddress room, CreatureData.Box box, int px, int py, int r) {
        if (blocked(solid, room, box, px, py)) {
            return false;
        }
        for (int a = 0; a < ORBIT_POINTS; a++) {
            double t = 2 * Math.PI * a / ORBIT_POINTS;
            if (blocked(solid, room, box, px + (int) Math.round(r * Math.cos(t)), py + (int) Math.round(r * Math.sin(t)))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> pieceFrames(LandmarksData marks) {
        List<String> frames = new ArrayList<>();
        for (LandmarksData.Lair lair : marks.lairs()) {
            frames.add(lair.piece().frame());
        }
        return frames;
    }

    private static GuardianData.Guardian guardian(GuardianData wardens, String id, String at) {
        for (GuardianData.Guardian g : wardens.guardians()) {
            if (g.id().equals(id)) {
                return g;
            }
        }
        throw new DataException(FILE, at, "is '" + id + "', which is not in data/entities/guardians.json");
    }

    private static boolean hasObject(OriginalMapRepository map, RoomAddress room, String object) {
        for (Placement p : map.placements(room)) {
            if (p.graphic().equals(object)) {
                return true;
            }
        }
        return false;
    }

    /** §14.8: the map scroll and the eyes are in real rooms, on clear ground, and the eyes name real lairs. */
    private static void checkItems(LandmarksData marks, Map<String, Set<String>> framesBySprite, Solid solid,
                                   Set<RoomAddress> taken) {
        LandmarksData.Items items = marks.items();
        if (items.map() == null) {
            return;
        }
        checkItem(framesBySprite, solid, taken, items.pickupBox(), items.map().room(), items.map().spot(),
                items.map().sprite(), "/items/map");
        Set<String> lairIds = new HashSet<>();
        for (LandmarksData.Lair lair : marks.lairs()) {
            lairIds.add(lair.id());
        }
        Set<String> revealed = new HashSet<>();
        for (int i = 0; i < items.eyes().size(); i++) {
            LandmarksData.Eye eye = items.eyes().get(i);
            String at = "/items/eyes/" + i;
            checkItem(framesBySprite, solid, taken, items.pickupBox(), eye.room(), eye.spot(), eye.sprite(), at);
            for (int k = 0; k < eye.reveals().size(); k++) {
                String id = eye.reveals().get(k);
                if (!lairIds.contains(id)) {
                    throw new DataException(FILE, at + "/reveals/" + k, "is '" + id + "', which is not a lair id");
                }
                if (!revealed.add(id)) {
                    throw new DataException(FILE, at + "/reveals/" + k, "is '" + id + "', which another eye already shows");
                }
            }
        }
        if (!items.eyes().isEmpty() && !revealed.equals(lairIds)) {
            throw new DataException(FILE, "/items/eyes", "between them show " + revealed + ", not every lair " + lairIds);
        }
    }

    /** The way arrow's frames, one for each way out of a room (§14.10). */
    public static final List<String> ARROW_FRAMES = List.of("n", "e", "s", "w");
    /** The glass glints through these, as the eye glances (§14.10). */
    public static final List<String> GLASS_FRAMES = List.of("f0", "f1", "f2");

    /**
     * §14.10: the looking glasses lie on clear ground in rooms of their own — not the start, the
     * way out, a room any set hides a quarter in, a shrine, the scroll's or an eye's, nor each
     * other's — and their sprites have the frames drawn for them.
     */
    private static void checkGlass(LandmarksData marks, Map<String, Set<String>> framesBySprite, Solid solid,
                                   Set<RoomAddress> busy) {
        LandmarksData.Glass glass = marks.items().glass();
        if (glass.places().isEmpty()) {
            return;
        }
        checkFrames(framesBySprite, "/items/glass/sprite", glass.sprite(), GLASS_FRAMES);
        checkFrames(framesBySprite, "/items/glass/arrow/sprite", glass.arrow().sprite(), ARROW_FRAMES);
        if (glass.warnTicks() >= glass.showWayTicks()) {
            throw new DataException(FILE, "/items/glass/warnTicks", "is not shorter than showWayTicks");
        }
        Set<RoomAddress> used = new HashSet<>(busy);
        LandmarksData.Items items = marks.items();
        if (items.map() != null) {
            used.add(LandmarksData.room(items.map().room()));
        }
        for (LandmarksData.Eye eye : items.eyes()) {
            used.add(LandmarksData.room(eye.room()));
        }
        for (int i = 0; i < glass.places().size(); i++) {
            LandmarksData.Place place = glass.places().get(i);
            String at = "/items/glass/places/" + i;
            RoomAddress room = room(place.room(), at + "/room");
            if (!used.add(room)) {
                throw new DataException(FILE, at + "/room",
                        "is " + room + ", already the start, the way out, a lair, a shrine or another item's room");
            }
            if (blocked(solid, room, items.pickupBox(), place.spot().x(), place.spot().y())) {
                throw new DataException(FILE, at + "/spot",
                        "is (" + place.spot().x() + "," + place.spot().y() + "), inside the scenery");
            }
        }
    }

    private static void checkItem(Map<String, Set<String>> framesBySprite, Solid solid, Set<RoomAddress> taken,
                                  CreatureData.Box box, String roomKey, LandmarksData.Point spot, String sprite,
                                  String at) {
        RoomAddress room = room(roomKey, at + "/room");
        if (taken.contains(room)) {
            throw new DataException(FILE, at + "/room", "is " + room + ", already the start, the way out or a lair");
        }
        if (!framesBySprite.containsKey(sprite)) {
            throw new DataException(FILE, at + "/sprite", "names sprite '" + sprite + "', which is not in data/art/sprites/index.json");
        }
        if (blocked(solid, room, box, spot.x(), spot.y())) {
            throw new DataException(FILE, at + "/spot", "is (" + spot.x() + "," + spot.y() + "), inside the scenery");
        }
    }

    private static boolean anyStandingSpot(Solid solid, RoomAddress room, LandmarksData.Rect zone) {
        for (int y = zone.y(); y < zone.y() + zone.h(); y += 4) {
            for (int x = zone.x(); x < zone.x() + zone.w(); x += 4) {
                if (!solid.at(room.col() * CollisionMask.COLS + x / 8, room.row() * CollisionMask.ROWS + y / 8)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean blocked(Solid solid, RoomAddress room, CreatureData.Box box, int px, int py) {
        int x0 = room.col() * CollisionMask.COLS * 8 + px + box.x();
        int y0 = room.row() * CollisionMask.ROWS * 8 + py + box.y();
        for (int y = y0; y < y0 + box.h(); y++) {
            for (int x = x0; x < x0 + box.w(); x++) {
                if (solid.at(Math.floorDiv(x, 8), Math.floorDiv(y, 8))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void checkFrames(Map<String, Set<String>> framesBySprite, String at, String sprite,
                                    List<String> needed) {
        Set<String> have = framesBySprite.get(sprite);
        if (have == null) {
            throw new DataException(FILE, at, "names sprite '" + sprite + "', which is not in data/art/sprites/index.json");
        }
        for (String frame : needed) {
            if (!have.contains(frame)) {
                throw new DataException(FILE, at, "names sprite '" + sprite + "', which has no frame '" + frame + "'");
            }
        }
    }

    private static RoomAddress room(String key, String at) {
        try {
            return LandmarksData.room(key);
        } catch (RuntimeException e) {
            throw new DataException(FILE, at, "is '" + key + "', which is not a \"col,row\" room");
        }
    }
}
