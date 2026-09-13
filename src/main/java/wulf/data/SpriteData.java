package wulf.data;

import java.util.List;
import java.util.Map;

/**
 * Bound view of one {@code data/art/sprites/<name>.sprite.json} (AGENTS.md
 * §10.1): pixel rows of legend characters, decoded here into palette indices.
 *
 * <p>Decoding lives in the data layer, not the renderer, so that
 * {@code wulf.world} can derive collision masks from the art without touching
 * AWT (§4, §22.4).
 */
public record SpriteData(
        int schemaVersion,
        String name,
        Size size,
        Origin origin,
        Map<String, Integer> legend,
        List<Frame> frames,
        Map<String, Mirror> mirror) {

    public SpriteData {
        legend = Map.copyOf(legend);
        frames = List.copyOf(frames);
        mirror = mirror == null ? Map.of() : Map.copyOf(mirror);
    }

    public record Size(int w, int h) {
    }

    public record Origin(int x, int y) {
    }

    public record Frame(String id, List<String> rows, Map<String, Point> anchors) {
        public Frame {
            rows = List.copyOf(rows);
            anchors = anchors == null ? Map.of() : Map.copyOf(anchors);
        }

        public Frame(String id, List<String> rows) {
            this(id, rows, Map.of());
        }
    }

    /** A named point inside a frame, in sprite pixels — e.g. the hand that holds the blade. */
    public record Point(int x, int y) {
    }

    public record Mirror(String from, boolean flipX) {
    }

    /**
     * Decodes and validates every frame, including mirrored ones.
     *
     * @return frame id -> row-major palette indices, -1 transparent
     * @throws DataException naming the file and the offending row
     */
    public Map<String, byte[]> decode(String source) {
        Map<String, byte[]> out = new java.util.LinkedHashMap<>();
        for (int f = 0; f < frames.size(); f++) {
            Frame frame = frames.get(f);
            String at = "/frames/" + f;
            if (out.containsKey(frame.id())) {
                throw new DataException(source, at + "/id", "duplicate frame id '" + frame.id() + "'");
            }
            if (frame.rows().size() != size.h()) {
                throw new DataException(source, at + "/rows",
                        "has " + frame.rows().size() + " rows but size.h is " + size.h());
            }
            for (Map.Entry<String, Point> a : frame.anchors().entrySet()) {
                Point pt = a.getValue();
                if (pt.x() < 0 || pt.y() < 0 || pt.x() >= size.w() || pt.y() >= size.h()) {
                    throw new DataException(source, at + "/anchors/" + a.getKey(),
                            "is at " + pt.x() + "," + pt.y() + ", outside the " + size.w() + "x" + size.h() + " sprite");
                }
            }
            byte[] px = new byte[size.w() * size.h()];
            for (int y = 0; y < size.h(); y++) {
                String row = frame.rows().get(y);
                if (row.length() != size.w()) {
                    throw new DataException(source, at + "/rows/" + y,
                            "is " + row.length() + " characters but size.w is " + size.w());
                }
                for (int x = 0; x < size.w(); x++) {
                    Integer index = legend.get(String.valueOf(row.charAt(x)));
                    if (index == null) {
                        throw new DataException(source, at + "/rows/" + y,
                                "uses '" + row.charAt(x) + "' at column " + x + ", which is not in the legend");
                    }
                    px[y * size.w() + x] = index.byteValue();
                }
            }
            out.put(frame.id(), px);
        }
        for (Map.Entry<String, Mirror> e : mirror.entrySet()) {
            byte[] from = out.get(e.getValue().from());
            if (from == null) {
                throw new DataException(source, "/mirror/" + e.getKey() + "/from",
                        "names frame '" + e.getValue().from() + "', which does not exist");
            }
            byte[] px = from.clone();
            if (e.getValue().flipX()) {
                for (int y = 0; y < size.h(); y++) {
                    for (int x = 0; x < size.w(); x++) {
                        px[y * size.w() + x] = from[y * size.w() + (size.w() - 1 - x)];
                    }
                }
            }
            out.put(e.getKey(), px);
        }
        return out;
    }

    /**
     * Every frame's anchors, mirrored frames included with x flipped.
     *
     * @return frame id -> anchor name -> point
     */
    public Map<String, Map<String, Point>> anchors() {
        Map<String, Frame> byId = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Point>> out = new java.util.LinkedHashMap<>();
        for (Frame f : frames) {
            byId.put(f.id(), f);
            out.put(f.id(), f.anchors());
        }
        for (Map.Entry<String, Mirror> e : mirror.entrySet()) {
            Frame from = byId.get(e.getValue().from());
            if (from == null) {
                continue;   // decode() reports the dangling mirror with a pointer
            }
            Map<String, Point> flipped = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Point> a : from.anchors().entrySet()) {
                Point pt = a.getValue();
                flipped.put(a.getKey(), e.getValue().flipX() ? new Point(size.w() - 1 - pt.x(), pt.y()) : pt);
            }
            out.put(e.getKey(), Map.copyOf(flipped));
        }
        return Map.copyOf(out);
    }
}
