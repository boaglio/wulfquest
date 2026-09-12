package wulf.data;

import java.util.List;

/**
 * The 16-colour palette (AGENTS.md §5.2). Index {@code -1} is transparent and
 * must never reach the screen.
 */
public record Palette(int schemaVersion, int transparent, List<Entry> entries) {

    public static final int SIZE = 16;

    public Palette {
        entries = List.copyOf(entries);
    }

    public record Entry(int i, String name, String rgb) {
    }

    /** Packed 0xFFRRGGBB for each index, ready for the scaler. */
    public int[] toArgb() {
        int[] argb = new int[SIZE];
        for (Entry e : entries) {
            argb[e.i()] = 0xFF00_0000 | Integer.parseInt(e.rgb().substring(1), 16);
        }
        return argb;
    }

    /** Index of a named colour, for data files that refer to colours by name. */
    public int indexOf(String name) {
        for (Entry e : entries) {
            if (e.name().equals(name)) {
                return e.i();
            }
        }
        throw new IllegalStateException("no palette colour named '" + name + "'");
    }
}
