package wulf.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import wulf.data.DataException;
import wulf.data.FontData;

/**
 * A loaded bitmap font (AGENTS.md §10.4). Glyphs are stored as boolean masks
 * so the painter can draw the same glyph in any palette colour.
 */
public final class FontSet {

    private final String name;
    private final int glyphW;
    private final int glyphH;
    private final int advance;
    private final int lineHeight;
    private final char fallback;
    private final Map<Character, boolean[]> masks;

    public FontSet(String source, String key, FontData.Set set) {
        this.name = set.name();
        this.glyphW = set.size().w();
        this.glyphH = set.size().h();
        this.advance = set.advance();
        this.lineHeight = set.lineHeight();
        this.fallback = set.fallback().charAt(0);

        Map<Character, boolean[]> built = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : set.glyphs().entrySet()) {
            String ch = e.getKey();
            if (ch.length() != 1) {
                throw new DataException(source, "/sets/" + key + "/glyphs/" + ch,
                        "glyph key must be exactly one character");
            }
            List<String> rows = e.getValue();
            if (rows.size() != glyphH) {
                throw new DataException(source, "/sets/" + key + "/glyphs/" + ch,
                        "has " + rows.size() + " rows but size.h is " + glyphH);
            }
            boolean[] mask = new boolean[glyphW * glyphH];
            for (int y = 0; y < glyphH; y++) {
                String row = rows.get(y);
                if (row.length() != glyphW) {
                    throw new DataException(source, "/sets/" + key + "/glyphs/" + ch + "/" + y,
                            "is " + row.length() + " characters but size.w is " + glyphW);
                }
                for (int x = 0; x < glyphW; x++) {
                    char c = row.charAt(x);
                    Integer index = set.legend().get(String.valueOf(c));
                    if (index == null) {
                        throw new DataException(source, "/sets/" + key + "/glyphs/" + ch + "/" + y,
                                "uses '" + c + "', which is not in the set's legend");
                    }
                    mask[y * glyphW + x] = index >= 0;
                }
            }
            built.put(ch.charAt(0), mask);
        }
        if (!built.containsKey(fallback)) {
            throw new DataException(source, "/sets/" + key + "/fallback",
                    "'" + fallback + "' is not one of the set's glyphs");
        }
        this.masks = Map.copyOf(built);
    }

    public String name() {
        return name;
    }

    public int glyphW() {
        return glyphW;
    }

    public int glyphH() {
        return glyphH;
    }

    public int advance() {
        return advance;
    }

    public int lineHeight() {
        return lineHeight;
    }

    public int widthOf(String text) {
        return text.isEmpty() ? 0 : text.length() * advance;
    }

    /** Draws {@code text} with its top-left at (x, y). Unknown chars use the fallback. */
    public void draw(Framebuffer fb, String text, int x, int y, int paletteIndex) {
        int penX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            boolean[] mask = masks.get(c);
            if (mask == null) {
                mask = masks.get(fallback);
            }
            for (int gy = 0; gy < glyphH; gy++) {
                for (int gx = 0; gx < glyphW; gx++) {
                    if (mask[gy * glyphW + gx]) {
                        fb.set(penX + gx, y + gy, paletteIndex);
                    }
                }
            }
            penX += advance;
        }
    }

    /** Draws text centred horizontally within {@code [x, x + w)}. */
    public void drawCentred(Framebuffer fb, String text, int x, int w, int y, int paletteIndex) {
        draw(fb, text, x + (w - widthOf(text)) / 2, y, paletteIndex);
    }
}
