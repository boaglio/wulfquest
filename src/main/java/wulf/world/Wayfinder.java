package wulf.world;

/**
 * The way through the jungle (AGENTS.md §14.10, {@code [NEW]}): a breadth-first flood over
 * the stitched world, one step a cell, through the cells the player's feet fit in
 * ({@link WorldGrid#blockPassable}), so the way it finds is one he can walk, room edges and
 * all.
 *
 * <p>Cells are packed as {@code gy * WorldGrid.COLS + gx}. The flood runs from the targets
 * outwards, once for a set of them; any number of starting points can then follow it down.
 * Drawing only: the simulation never asks.
 */
public final class Wayfinder {

    /** Neighbours in a fixed order, so the same field always gives the same way. */
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DY = {0, 0, 1, -1};

    private final WorldGrid grid;

    public Wayfinder(WorldGrid grid) {
        this.grid = grid;
    }

    public static int cell(int gx, int gy) {
        return gy * WorldGrid.COLS + gx;
    }

    public static int gx(int cell) {
        return cell % WorldGrid.COLS;
    }

    public static int gy(int cell) {
        return cell / WorldGrid.COLS;
    }

    /**
     * Steps from every cell to the nearest of {@code targets}; -1 where none can be reached.
     * A target the feet do not fit in is left out.
     */
    public int[] distances(int[] targets) {
        int[] dist = new int[WorldGrid.COLS * WorldGrid.ROWS];
        java.util.Arrays.fill(dist, -1);
        int[] queue = new int[dist.length];
        int tail = 0;
        for (int t : targets) {
            if (t >= 0 && dist[t] < 0 && grid.blockPassable(gx(t), gy(t))) {
                dist[t] = 0;
                queue[tail++] = t;
            }
        }
        for (int head = 0; head < tail; head++) {
            int c = queue[head];
            int x = gx(c);
            int y = gy(c);
            for (int k = 0; k < DX.length; k++) {
                int nx = x + DX[k];
                int ny = y + DY[k];
                if (nx < 0 || ny < 0 || nx >= WorldGrid.COLS || ny >= WorldGrid.ROWS) {
                    continue;
                }
                int n = cell(nx, ny);
                if (dist[n] < 0 && grid.blockPassable(nx, ny)) {
                    dist[n] = dist[c] + 1;
                    queue[tail++] = n;
                }
            }
        }
        return dist;
    }

    /**
     * The cell nearest ({@code gx}, {@code gy}) within {@code radius} cells, by steps on both
     * axes, that the feet fit in and — given a field — that it reaches; -1 if none.
     */
    public int nearest(int[] dist, int gx, int gy, int radius) {
        int best = -1;
        int bestD = Integer.MAX_VALUE;
        for (int y = gy - radius; y <= gy + radius; y++) {
            for (int x = gx - radius; x <= gx + radius; x++) {
                if (x < 0 || y < 0 || x >= WorldGrid.COLS || y >= WorldGrid.ROWS || !grid.blockPassable(x, y)) {
                    continue;
                }
                if (dist != null && dist[cell(x, y)] < 0) {
                    continue;
                }
                int d = Math.abs(x - gx) + Math.abs(y - gy);
                if (d < bestD) {
                    bestD = d;
                    best = cell(x, y);
                }
            }
        }
        return best;
    }

    /** The cells from {@code from} down the field to a target, both ends included; empty if it is not reached. */
    public int[] path(int[] dist, int from) {
        if (from < 0 || dist[from] < 0) {
            return new int[0];
        }
        int[] way = new int[dist[from] + 1];
        int c = from;
        for (int i = 0; i < way.length; i++) {
            way[i] = c;
            int x = gx(c);
            int y = gy(c);
            for (int k = 0; k < DX.length && dist[c] > 0; k++) {
                int nx = x + DX[k];
                int ny = y + DY[k];
                if (nx >= 0 && ny >= 0 && nx < WorldGrid.COLS && ny < WorldGrid.ROWS && dist[cell(nx, ny)] == dist[c] - 1) {
                    c = cell(nx, ny);
                    break;
                }
            }
        }
        return way;
    }
}
