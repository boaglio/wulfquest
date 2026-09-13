package wulf.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import wulf.data.DataException;
import wulf.data.OriginalMapRepository;
import wulf.data.Placement;

/**
 * Turns the extracted map plus the scenery catalogue into rooms with
 * collision masks (AGENTS.md §7.2).
 *
 * <p>Performs the {@code MapReferenceValidator} and
 * {@code PlacementBoundsValidator} checks of §20.6. Every template is baked
 * eagerly at construction — nothing is resolved lazily at runtime (§20.3) —
 * and rooms that share a template share one mask.
 */
public final class RoomBaker {

    private static final String MAP_SOURCE = "data/world/original_map.json";

    private final OriginalMapRepository map;
    private final SceneryCatalog catalog;
    private final Map<Integer, CollisionMask> maskByType = new LinkedHashMap<>();

    public RoomBaker(OriginalMapRepository map, SceneryCatalog catalog) {
        this.map = map;
        this.catalog = catalog;
        for (int type : map.templateIds()) {
            validate(type);
        }
        for (int type : map.templateIds()) {
            maskByType.put(type, bake(type));
        }
    }

    private void validate(int type) {
        List<Placement> placements = map.placements(type);
        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            String at = "/templates/" + type + "/" + i;
            if (!catalog.has(p.graphic())) {
                throw new DataException(MAP_SOURCE, at + "/graphic",
                        "is '" + p.graphic() + "', which has no entry in data/world/scenery.json");
            }
            SceneryCatalog.Piece piece = catalog.piece(p.graphic());
            if (p.x() + piece.w() > CollisionMask.COLS || p.y() + piece.h() > CollisionMask.ROWS) {
                throw new DataException(MAP_SOURCE, at,
                        "places " + p.graphic() + " (" + piece.w() + "x" + piece.h() + " cells) at " + p.x() + ","
                                + p.y() + ", which overflows the 32x24 playfield");
            }
        }
    }

    private CollisionMask bake(int type) {
        CollisionMask mask = new CollisionMask();
        for (Placement p : map.placements(type)) {
            SceneryCatalog.Piece piece = catalog.piece(p.graphic());
            for (int cy = 0; cy < piece.h(); cy++) {
                for (int cx = 0; cx < piece.w(); cx++) {
                    if (piece.solidAt(cx, cy)) {
                        mask.setSolid(p.x() + cx, p.y() + cy);
                    }
                }
            }
        }
        return mask;
    }

    /** The collision mask of a template, whether or not any room uses it. */
    public CollisionMask mask(int roomType) {
        CollisionMask m = maskByType.get(roomType);
        if (m == null) {
            throw new IllegalStateException("no template for room type " + roomType);
        }
        return m;
    }

    public Room room(RoomAddress address) {
        int type = map.roomType(address);
        return new Room(address, type, map.placements(type), maskByType.get(type));
    }

    public OriginalMapRepository map() {
        return map;
    }

    public SceneryCatalog catalog() {
        return catalog;
    }
}
