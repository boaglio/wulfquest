package wulf.world;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import wulf.data.OriginalMapRepository;
import wulf.data.Placement;
import wulf.data.RoomEntitiesData;

/**
 * Which biome each room belongs to, derived from its scenery (AGENTS.md §12.6):
 *
 * <pre>
 * contains the hut marker                    -> hut
 * contains the water marker                  -> water
 * over half its footprint cells rocky        -> bonefields
 * over half its footprint cells mountainous  -> mountain
 * contains the reed marker and no mountains  -> swamp
 * otherwise                                  -> jungle
 * </pre>
 *
 * Resolved once for all 256 rooms at construction.
 */
public final class BiomeResolver {

    public static final String JUNGLE = "jungle";
    public static final String SWAMP = "swamp";
    public static final String MOUNTAIN = "mountain";
    public static final String BONEFIELDS = "bonefields";
    public static final String HUT = "hut";
    public static final String WATER = "water";

    private final String[] byRoom = new String[RoomAddress.GRID_W * RoomAddress.GRID_H];

    public BiomeResolver(OriginalMapRepository map, SceneryCatalog catalog, RoomEntitiesData.BiomeMarkers markers) {
        for (int row = 0; row < RoomAddress.GRID_H; row++) {
            for (int col = 0; col < RoomAddress.GRID_W; col++) {
                RoomAddress room = new RoomAddress(col, row);
                byRoom[room.index()] = resolve(map.placements(room), catalog, markers);
            }
        }
    }

    static String resolve(List<Placement> placements, SceneryCatalog catalog, RoomEntitiesData.BiomeMarkers markers) {
        boolean hut = false;
        boolean water = false;
        boolean reeds = false;
        int total = 0;
        int rock = 0;
        int mountain = 0;
        for (Placement p : placements) {
            hut |= p.graphic().equals(markers.hut());
            water |= p.graphic().equals(markers.water());
            reeds |= p.graphic().equals(markers.swamp());
            SceneryCatalog.Piece piece = catalog.piece(p.graphic());
            int cells = piece.w() * piece.h();
            total += cells;
            if (piece.biomeHint().equals(BONEFIELDS)) {
                rock += cells;
            } else if (piece.biomeHint().equals(MOUNTAIN)) {
                mountain += cells;
            }
        }
        if (hut) {
            return HUT;
        }
        if (water) {
            return WATER;
        }
        if (rock * 2 > total) {
            return BONEFIELDS;
        }
        if (mountain * 2 > total) {
            return MOUNTAIN;
        }
        if (reeds && mountain == 0) {
            return SWAMP;
        }
        return JUNGLE;
    }

    public String biomeOf(RoomAddress room) {
        return byRoom[room.index()];
    }

    /** The biomes that at least one room resolves to, in first-seen order. */
    public Set<String> inUse() {
        Set<String> used = new LinkedHashSet<>();
        for (String b : byRoom) {
            used.add(b);
        }
        return used;
    }
}
