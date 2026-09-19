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
        if (!hasArch(map, exit, marks.exit().arch())) {
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
        int asideX = keeper.x() + wardens.keeper().stepAsidePx();
        if (blocked(solid, exit, keeperBox, asideX, keeper.y())) {
            throw new DataException(FILE, "/exit/keeper", "leaves the Keeper nowhere to step aside to: ("
                    + asideX + "," + keeper.y() + ") is solid (guardians.json /keeper/stepAsidePx)");
        }

        Set<String> lairIds = new HashSet<>();
        Set<String> pieceIds = new HashSet<>();
        Set<Integer> slots = new HashSet<>();
        Set<String> quadrants = new HashSet<>();
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
            RoomAddress room = room(lair.room(), at + "/room");
            if (room.isBoundary()) {
                throw new DataException(FILE, at + "/room", "is " + room + ", a boundary room");
            }
            if (room.equals(map.startRoom()) || room.equals(exit)) {
                throw new DataException(FILE, at + "/room", "is " + room + ", which is already the start or the way out");
            }
            String quadrant = (room.col() < RoomAddress.GRID_W / 2 ? "W" : "E")
                    + (room.row() < RoomAddress.GRID_H / 2 ? "N" : "S");
            if (!quadrants.add(quadrant)) {
                throw new DataException(FILE, at + "/room",
                        "is " + room + ", a second lair in the " + quadrant + " quadrant; §14.1 wants one per quadrant");
            }
            GuardianData.Guardian guardian = guardian(wardens, lair.guardian(), at + "/guardian");
            LandmarksData.Point pedestal = lair.pedestal();
            if (blocked(solid, room, guardian.collisionBox(), pedestal.x(), pedestal.y())) {
                throw new DataException(FILE, at + "/pedestal",
                        "is (" + pedestal.x() + "," + pedestal.y() + "), where " + lair.guardian() + " does not fit");
            }
            if (blocked(solid, room, marks.amulet().collisionBox(), pedestal.x(), pedestal.y())) {
                throw new DataException(FILE, at + "/pedestal", "is solid where the amulet quarter would rest");
            }
            int reach = wardens.orbit().radiusPx();
            if (pedestal.x() - reach < 0 || pedestal.x() + reach >= CollisionMask.COLS * 8
                    || pedestal.y() - reach < 0 || pedestal.y() + reach >= CollisionMask.ROWS * 8) {
                throw new DataException(FILE, at + "/pedestal",
                        "leaves the guardian's " + reach + " px orbit off the room");
            }
        }
        if (creatures.creatures().isEmpty()) {
            throw new DataException("data/entities/creatures.json", "/creatures", "is empty");
        }
        for (int i = 0; i < marks.caveMouths().size(); i++) {
            RoomAddress mouth = room(marks.caveMouths().get(i), "/caveMouths/" + i);
            if (!hasArch(map, mouth, marks.exit().arch())) {
                throw new DataException(FILE, "/caveMouths/" + i, "is " + mouth + ", which has no arch in it");
            }
            if (mouth.equals(exit)) {
                throw new DataException(FILE, "/caveMouths/" + i,
                        "is the way out; its shrine would compete with the Keeper");
            }
        }
        for (int i = 0; i < marks.stillWater().rooms().size(); i++) {
            room(marks.stillWater().rooms().get(i), "/stillWater/rooms/" + i);
        }
        marks.startFacingName();
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

    private static boolean hasArch(OriginalMapRepository map, RoomAddress room, String arch) {
        for (Placement p : map.placements(room)) {
            if (p.graphic().equals(arch)) {
                return true;
            }
        }
        return false;
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

    private static boolean blocked(Solid solid, RoomAddress room, CreatureData.Box box, int px, int py) {
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
