package wulf.world;

/**
 * A room on the 16x16 map (AGENTS.md §7.1). {@code col} runs left to right,
 * {@code row} runs north to south, so row 0 is the northern boundary.
 */
public record RoomAddress(int col, int row) {

    public static final int GRID_W = 16;
    public static final int GRID_H = 16;

    public RoomAddress {
        if (col < 0 || col >= GRID_W || row < 0 || row >= GRID_H) {
            throw new IllegalArgumentException("room out of range: " + col + "," + row);
        }
    }

    /** Parses the canonical {@code "c,r"} form used as a key in data files. */
    public static RoomAddress parse(String id) {
        int comma = id.indexOf(',');
        if (comma < 0) {
            throw new IllegalArgumentException("not a room id: '" + id + "' (expected \"col,row\")");
        }
        return new RoomAddress(
                Integer.parseInt(id.substring(0, comma).trim()),
                Integer.parseInt(id.substring(comma + 1).trim()));
    }

    public int index() {
        return row * GRID_W + col;
    }

    public boolean isBoundary() {
        return col == 0 || row == 0 || col == GRID_W - 1 || row == GRID_H - 1;
    }

    @Override
    public String toString() {
        return col + "," + row;
    }
}
