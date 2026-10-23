package wulf.world;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import wulf.data.DataException;
import wulf.data.SceneryData;
import wulf.data.SpriteData;

/**
 * The 41 placeable maze pieces, with their collision resolved (AGENTS.md §9, §7.3).
 *
 * <p>Performs the {@code SceneryFootprintValidator} checks of §20.6 at
 * construction: every object's sprite exists and is exactly its footprint in
 * pixels, and every collision override has the footprint's shape.
 */
public final class SceneryCatalog {

    public static final int CELL = 8;
    private static final String SOURCE = "data/world/scenery.json";

    private final Map<String, Piece> pieces;

    public SceneryCatalog(SceneryData data, Map<String, SpriteData> spritesByName) {
        Map<String, Piece> built = new TreeMap<>();
        for (Map.Entry<String, SceneryData.SceneryObject> e : data.objects().entrySet()) {
            String id = e.getKey();
            SceneryData.SceneryObject o = e.getValue();
            String at = "/objects/" + id;
            int w = o.cells().w();
            int h = o.cells().h();

            SpriteData sprite = spritesByName.get(o.sprite());
            if (sprite == null) {
                throw new DataException(SOURCE, at + "/sprite",
                        "names sprite '" + o.sprite() + "', which is not in art/sprites/index.json");
            }
            if (sprite.size().w() != w * CELL || sprite.size().h() != h * CELL) {
                throw new DataException(SOURCE, at + "/cells",
                        "is " + w + "x" + h + " cells (" + w * CELL + "x" + h * CELL + " px) but sprite '"
                                + o.sprite() + "' is " + sprite.size().w() + "x" + sprite.size().h() + " px");
            }

            boolean[] solid;
            boolean derived;
            if (o.collisionCells() != null) {
                solid = parseOverride(o.collisionCells(), w, h, at + "/collisionCells");
                derived = false;
            } else {
                String spriteSource = "data/art/sprites/" + o.sprite() + ".sprite.json";
                byte[] px = sprite.decode(spriteSource).get(sprite.frames().get(0).id());
                solid = derive(px, w, h, data.solidCoveragePercent());
                derived = true;
            }
            built.put(id, new Piece(id, o.sprite(), w, h, solid, derived, o.biomeHint()));
        }
        this.pieces = Collections.unmodifiableMap(built);
    }

    /**
     * §7.3: a cell is solid when the art paints at least {@code coveragePercent}
     * of its 64 pixels. Integer arithmetic only.
     */
    static boolean[] derive(byte[] px, int cellsW, int cellsH, int coveragePercent) {
        boolean[] solid = new boolean[cellsW * cellsH];
        int pixelW = cellsW * CELL;
        for (int cy = 0; cy < cellsH; cy++) {
            for (int cx = 0; cx < cellsW; cx++) {
                int painted = 0;
                for (int y = 0; y < CELL; y++) {
                    int row = (cy * CELL + y) * pixelW;
                    for (int x = 0; x < CELL; x++) {
                        if (px[row + cx * CELL + x] >= 0) {
                            painted++;
                        }
                    }
                }
                solid[cy * cellsW + cx] = painted * 100 >= CELL * CELL * coveragePercent;
            }
        }
        return solid;
    }

    private static boolean[] parseOverride(List<String> rows, int w, int h, String at) {
        if (rows.size() != h) {
            throw new DataException(SOURCE, at, "has " + rows.size() + " rows but the object is " + h + " cells tall");
        }
        boolean[] solid = new boolean[w * h];
        for (int y = 0; y < h; y++) {
            String row = rows.get(y);
            if (row.length() != w) {
                throw new DataException(SOURCE, at + "/" + y,
                        "is " + row.length() + " characters but the object is " + w + " cells wide");
            }
            for (int x = 0; x < w; x++) {
                solid[y * w + x] = row.charAt(x) == '#';
            }
        }
        return solid;
    }

    public boolean has(String id) {
        return pieces.containsKey(id);
    }

    public Piece piece(String id) {
        Piece p = pieces.get(id);
        if (p == null) {
            throw new IllegalStateException("no scenery object '" + id + "' in data/world/scenery.json");
        }
        return p;
    }

    public Set<String> ids() {
        return pieces.keySet();
    }

    /** One maze piece: its footprint in cells and which of those cells block movement. */
    public static final class Piece {
        private final String id;
        private final String sprite;
        private final int w;
        private final int h;
        private final boolean[] solid;
        private final boolean derived;
        private final String biomeHint;

        Piece(String id, String sprite, int w, int h, boolean[] solid, boolean derived, String biomeHint) {
            this.id = id;
            this.sprite = sprite;
            this.w = w;
            this.h = h;
            this.solid = solid;
            this.derived = derived;
            this.biomeHint = biomeHint;
        }

        public String id() {
            return id;
        }

        public String sprite() {
            return sprite;
        }

        /** Footprint width in cells. */
        public int w() {
            return w;
        }

        /** Footprint height in cells. */
        public int h() {
            return h;
        }

        /** True when collision came from the art; false when authored as an override. */
        public boolean derived() {
            return derived;
        }

        public String biomeHint() {
            return biomeHint;
        }

        public boolean solidAt(int cx, int cy) {
            return cx >= 0 && cy >= 0 && cx < w && cy < h && solid[cy * w + cx];
        }

        public int solidCells() {
            int n = 0;
            for (boolean s : solid) {
                if (s) {
                    n++;
                }
            }
            return n;
        }
    }
}
