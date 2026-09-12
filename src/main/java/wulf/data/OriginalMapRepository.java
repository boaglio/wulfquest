package wulf.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import wulf.world.RoomAddress;

/**
 * Domain view over the extracted map (AGENTS.md §8.3). Everything downstream
 * asks questions of this repository rather than reading the raw JSON shape.
 *
 * <p>All indexes are built once here; nothing is computed per tick.
 */
public final class OriginalMapRepository {

    private final int gridW;
    private final int gridH;
    private final RoomAddress startRoom;
    private final int[] roomTypeByIndex;
    private final Map<Integer, List<Placement>> placementsByType;

    public OriginalMapRepository(OriginalMap map) {
        this.gridW = map.gridW();
        this.gridH = map.gridH();
        this.startRoom = new RoomAddress(map.startCol(), map.startRow());

        this.roomTypeByIndex = new int[gridW * gridH];
        for (int row = 0; row < gridH; row++) {
            List<Integer> line = map.roomTypeGrid().get(row);
            for (int col = 0; col < gridW; col++) {
                roomTypeByIndex[row * gridW + col] = line.get(col);
            }
        }

        Map<Integer, List<Placement>> byType = new LinkedHashMap<>();
        for (Map.Entry<String, List<Placement>> e : map.templates().entrySet()) {
            byType.put(Integer.valueOf(e.getKey()), List.copyOf(e.getValue()));
        }
        this.placementsByType = Collections.unmodifiableMap(byType);
    }

    public int gridW() {
        return gridW;
    }

    public int gridH() {
        return gridH;
    }

    public RoomAddress startRoom() {
        return startRoom;
    }

    public int roomCount() {
        return gridW * gridH;
    }

    public int templateCount() {
        return placementsByType.size();
    }

    public int roomType(int col, int row) {
        return roomTypeByIndex[row * gridW + col];
    }

    public int roomType(RoomAddress room) {
        return roomTypeByIndex[room.index()];
    }

    /** The scenery of a room, in the order the original data lists it. */
    public List<Placement> placements(int roomType) {
        List<Placement> p = placementsByType.get(roomType);
        if (p == null) {
            throw new IllegalStateException("no template for room type " + roomType);
        }
        return p;
    }

    public List<Placement> placements(RoomAddress room) {
        return placements(roomType(room));
    }

    /** Template ids that exist in the data, ascending. */
    public Set<Integer> templateIds() {
        return new TreeSet<>(placementsByType.keySet());
    }

    /** Template ids actually used by at least one room, ascending. */
    public Set<Integer> usedTemplateIds() {
        Set<Integer> used = new TreeSet<>();
        for (int t : roomTypeByIndex) {
            used.add(t);
        }
        return used;
    }

    /** Distinct scenery object ids across every template, ascending. */
    public Set<String> sceneryIds() {
        Set<String> ids = new TreeSet<>();
        for (List<Placement> ps : placementsByType.values()) {
            for (Placement p : ps) {
                ids.add(p.graphic());
            }
        }
        return ids;
    }

    /** Every room that uses a given template. */
    public List<RoomAddress> roomsOfType(int roomType) {
        List<RoomAddress> rooms = new ArrayList<>();
        for (int row = 0; row < gridH; row++) {
            for (int col = 0; col < gridW; col++) {
                if (roomType(col, row) == roomType) {
                    rooms.add(new RoomAddress(col, row));
                }
            }
        }
        return rooms;
    }

    /** Total placements summed over all 256 rooms (not over templates). */
    public int totalRoomPlacements() {
        int total = 0;
        for (int t : roomTypeByIndex) {
            total += placements(t).size();
        }
        return total;
    }

    /** Total placements summed over the templates themselves. */
    public int totalTemplatePlacements() {
        int total = 0;
        for (List<Placement> ps : placementsByType.values()) {
            total += ps.size();
        }
        return total;
    }
}
