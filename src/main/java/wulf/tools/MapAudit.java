package wulf.tools;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import wulf.Content;
import wulf.data.DataException;
import wulf.data.Placement;
import wulf.world.CollisionMask;
import wulf.world.Room;
import wulf.world.RoomAddress;
import wulf.world.RoomBaker;
import wulf.world.SceneryCatalog;
import wulf.world.WorldGrid;

/**
 * Whole-world navigability audit (AGENTS.md §22.3).
 *
 * <p>Floods the stitched world from the start room using the player's 2x2-cell
 * feet clearance, then reports unreachable and sealed interior rooms and the
 * walkable share of every room. Each room is also measured under the
 * <em>footprint baseline</em> — every object's full rectangle solid — which is
 * the extracted map's own maze: art-derived collision can never be less open
 * than that, and should not be wildly more open either.
 *
 * <p>{@code mvn -q exec:java -Dexec.mainClass=wulf.tools.MapAudit}
 */
public final class MapAudit {

    /** Below this walkable share a room is reported as cramped. */
    public static final int CRAMPED_BELOW = 18;
    /** Above this walkable share a room is reported as an open field. */
    public static final int OPEN_ABOVE = 70;
    /** Below this walkable share the audit fails. */
    public static final int FAIL_BELOW = 12;

    private MapAudit() {
    }

    public record RoomStat(RoomAddress room, int type, int walkablePercent, int footprintWalkablePercent,
                           int passableBlocks, boolean reached) {
    }

    public record Report(
            List<RoomStat> rooms,
            int interiorRooms,
            int interiorReached,
            List<RoomAddress> unreachable,
            List<RoomAddress> sealed,
            List<RoomAddress> cramped,
            List<RoomAddress> open,
            List<RoomAddress> belowFloor,
            int minWalk,
            int medianWalk,
            int maxWalk,
            int footprintMinWalk,
            int footprintMedianWalk,
            int footprintMaxWalk) {

        public boolean passes() {
            return unreachable.isEmpty() && sealed.isEmpty() && belowFloor.isEmpty();
        }
    }

    public static Report run(RoomBaker baker) {
        WorldGrid grid = new WorldGrid(baker);
        boolean[] reached = new boolean[WorldGrid.COLS * WorldGrid.ROWS];
        int seed = nearestPassable(grid, baker.map().startRoom());
        if (seed >= 0) {
            flood(grid, seed, reached);
        }

        List<RoomStat> stats = new ArrayList<>();
        List<RoomAddress> unreachable = new ArrayList<>();
        List<RoomAddress> sealed = new ArrayList<>();
        List<RoomAddress> cramped = new ArrayList<>();
        List<RoomAddress> open = new ArrayList<>();
        List<RoomAddress> belowFloor = new ArrayList<>();
        List<Integer> walks = new ArrayList<>();
        List<Integer> footprintWalks = new ArrayList<>();
        int interior = 0;
        int interiorReached = 0;

        for (int row = 0; row < RoomAddress.GRID_H; row++) {
            for (int col = 0; col < RoomAddress.GRID_W; col++) {
                RoomAddress address = new RoomAddress(col, row);
                Room room = baker.room(address);
                int blocks = 0;
                boolean hit = false;
                for (int cy = 0; cy < CollisionMask.ROWS; cy++) {
                    for (int cx = 0; cx < CollisionMask.COLS; cx++) {
                        int gx = col * CollisionMask.COLS + cx;
                        int gy = row * CollisionMask.ROWS + cy;
                        if (grid.blockPassable(gx, gy)) {
                            blocks++;
                            hit |= reached[gy * WorldGrid.COLS + gx];
                        }
                    }
                }
                int walk = room.mask().walkablePercent();
                int footprint = footprintWalkablePercent(baker.catalog(), room);
                stats.add(new RoomStat(address, room.type(), walk, footprint, blocks, hit));

                if (address.isBoundary()) {
                    continue;
                }
                interior++;
                walks.add(walk);
                footprintWalks.add(footprint);
                if (hit) {
                    interiorReached++;
                } else {
                    unreachable.add(address);
                }
                if (blocks == 0) {
                    sealed.add(address);
                }
                if (walk < FAIL_BELOW) {
                    belowFloor.add(address);
                }
                if (walk < CRAMPED_BELOW) {
                    cramped.add(address);
                }
                if (walk > OPEN_ABOVE) {
                    open.add(address);
                }
            }
        }
        walks.sort(null);
        footprintWalks.sort(null);
        return new Report(List.copyOf(stats), interior, interiorReached, List.copyOf(unreachable),
                List.copyOf(sealed), List.copyOf(cramped), List.copyOf(open), List.copyOf(belowFloor),
                walks.get(0), walks.get(walks.size() / 2), walks.get(walks.size() - 1),
                footprintWalks.get(0), footprintWalks.get(footprintWalks.size() / 2),
                footprintWalks.get(footprintWalks.size() - 1));
    }

    /** The room as the extracted map drew it: every object's whole rectangle solid. */
    static int footprintWalkablePercent(SceneryCatalog catalog, Room room) {
        boolean[] solid = new boolean[CollisionMask.COLS * CollisionMask.ROWS];
        for (Placement p : room.placements()) {
            SceneryCatalog.Piece piece = catalog.piece(p.graphic());
            for (int y = p.y(); y < Math.min(CollisionMask.ROWS, p.y() + piece.h()); y++) {
                for (int x = p.x(); x < Math.min(CollisionMask.COLS, p.x() + piece.w()); x++) {
                    solid[y * CollisionMask.COLS + x] = true;
                }
            }
        }
        int walkable = 0;
        for (boolean s : solid) {
            if (!s) {
                walkable++;
            }
        }
        return walkable * 100 / solid.length;
    }

    private static int nearestPassable(WorldGrid grid, RoomAddress start) {
        int x0 = start.col() * CollisionMask.COLS;
        int y0 = start.row() * CollisionMask.ROWS;
        int cx = x0 + CollisionMask.COLS / 2;
        int cy = y0 + CollisionMask.ROWS / 2;
        int best = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = y0; y < y0 + CollisionMask.ROWS; y++) {
            for (int x = x0; x < x0 + CollisionMask.COLS; x++) {
                int d = Math.abs(x - cx) + Math.abs(y - cy);
                if (d < bestDistance && grid.blockPassable(x, y)) {
                    bestDistance = d;
                    best = y * WorldGrid.COLS + x;
                }
            }
        }
        return best;
    }

    private static void flood(WorldGrid grid, int seed, boolean[] reached) {
        int[] queue = new int[reached.length];
        int head = 0;
        int tail = 0;
        reached[seed] = true;
        queue[tail++] = seed;
        while (head < tail) {
            int i = queue[head++];
            int x = i % WorldGrid.COLS;
            int y = i / WorldGrid.COLS;
            tail = visit(grid, x + 1, y, reached, queue, tail);
            tail = visit(grid, x - 1, y, reached, queue, tail);
            tail = visit(grid, x, y + 1, reached, queue, tail);
            tail = visit(grid, x, y - 1, reached, queue, tail);
        }
    }

    private static int visit(WorldGrid grid, int x, int y, boolean[] reached, int[] queue, int tail) {
        if (x < 0 || y < 0 || x >= WorldGrid.COLS || y >= WorldGrid.ROWS) {
            return tail;
        }
        int i = y * WorldGrid.COLS + x;
        if (reached[i] || !grid.blockPassable(x, y)) {
            return tail;
        }
        reached[i] = true;
        queue[tail] = i;
        return tail + 1;
    }

    /** A human-readable dump: the whole world as '#' and '.', then one line per room. */
    public static String render(RoomBaker baker, Report report) {
        StringBuilder sb = new StringBuilder(1 << 20);
        sb.append("Wulf Quest map audit — AGENTS.md §22.3\n\n");
        summary(sb, report);
        sb.append("\nWorld (# solid, . walkable; rooms separated by | and -)\n\n");
        String rule = ("-".repeat(CollisionMask.COLS) + "+").repeat(RoomAddress.GRID_W) + "\n";
        for (int row = 0; row < RoomAddress.GRID_H; row++) {
            sb.append(rule);
            String[] masks = new String[RoomAddress.GRID_W];
            for (int col = 0; col < RoomAddress.GRID_W; col++) {
                masks[col] = baker.room(new RoomAddress(col, row)).mask().toAscii();
            }
            for (int cy = 0; cy < CollisionMask.ROWS; cy++) {
                for (int col = 0; col < RoomAddress.GRID_W; col++) {
                    int start = cy * (CollisionMask.COLS + 1);
                    sb.append(masks[col], start, start + CollisionMask.COLS).append('|');
                }
                sb.append('\n');
            }
        }
        sb.append(rule).append("\nroom   type  walk%  footprint%  passable-blocks  reached\n");
        for (RoomStat s : report.rooms()) {
            sb.append(String.format("%-6s %4d  %5d  %10d  %15d  %s%n", s.room(), s.type(), s.walkablePercent(),
                    s.footprintWalkablePercent(), s.passableBlocks(), s.reached() ? "yes" : "NO"));
        }
        return sb.toString();
    }

    static void summary(StringBuilder sb, Report r) {
        sb.append("  interior rooms reached from the start : ")
                .append(r.interiorReached()).append(" / ").append(r.interiorRooms()).append('\n');
        sb.append("  unreachable                            : ").append(list(r.unreachable())).append('\n');
        sb.append("  sealed (no 2x2 clearance at all)       : ").append(list(r.sealed())).append('\n');
        sb.append("  walkable %, interior, art collision    : min ").append(r.minWalk())
                .append("  median ").append(r.medianWalk()).append("  max ").append(r.maxWalk()).append('\n');
        sb.append("  walkable %, interior, footprint base   : min ").append(r.footprintMinWalk())
                .append("  median ").append(r.footprintMedianWalk()).append("  max ").append(r.footprintMaxWalk())
                .append('\n');
        sb.append("  cramped (< ").append(CRAMPED_BELOW).append("%)                        : ")
                .append(list(r.cramped())).append('\n');
        sb.append("  open field (> ").append(OPEN_ABOVE).append("%)                     : ")
                .append(list(r.open())).append('\n');
        sb.append("  below the ").append(FAIL_BELOW).append("% floor (fails)             : ")
                .append(list(r.belowFloor())).append('\n');
        sb.append("  result                                 : ").append(r.passes() ? "PASS" : "FAIL").append('\n');
    }

    private static String list(List<RoomAddress> rooms) {
        if (rooms.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder().append(rooms.size()).append(" — ");
        for (int i = 0; i < Math.min(rooms.size(), 12); i++) {
            sb.append(i == 0 ? "" : " ").append(rooms.get(i));
        }
        return rooms.size() > 12 ? sb.append(" ...").toString() : sb.toString();
    }

    public static void main(String[] args) {
        Path data = Path.of(args.length > 0 ? args[0] : "data");
        Content content;
        try {
            content = Content.load(data);
        } catch (DataException e) {
            System.err.println("DATA ERROR\n  file    : " + e.file() + "\n  pointer : " + e.pointer()
                    + "\n  problem : " + e.detail());
            System.exit(2);
            return;
        }
        Report report = run(content.rooms());
        StringBuilder sb = new StringBuilder("map audit\n");
        summary(sb, report);
        System.out.print(sb);
        Path out = Path.of("target", "map-audit.txt");
        try {
            Files.createDirectories(out.getParent());
            Files.writeString(out, render(content.rooms(), report), StandardCharsets.UTF_8);
            System.out.println("  full report                            : " + out);
        } catch (IOException e) {
            System.err.println("could not write " + out + ": " + e.getMessage());
        }
        System.exit(report.passes() ? 0 : 1);
    }

    /**
     * Rooms crossed on the shortest walk from {@code start} to every room, at the player's
     * 2x2-cell clearance (§14.2): moving within a room costs nothing, crossing into the
     * next costs one. {@link Integer#MAX_VALUE} where a room cannot be reached.
     */
    public static int[] roomDistances(WorldGrid grid, RoomAddress start) {
        int cols = WorldGrid.COLS;
        int[] best = new int[cols * WorldGrid.ROWS];
        java.util.Arrays.fill(best, Integer.MAX_VALUE);
        int[] rooms = new int[RoomAddress.GRID_W * RoomAddress.GRID_H];
        java.util.Arrays.fill(rooms, Integer.MAX_VALUE);
        int seed = nearestPassable(grid, start);
        if (seed < 0) {
            return rooms;
        }
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        best[seed] = 0;
        queue.add(new int[] {seed % cols, seed / cols, 0});
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            int[] at = queue.poll();
            int x = at[0];
            int y = at[1];
            int d = at[2];
            if (d > best[y * cols + x]) {
                continue;
            }
            int room = (y / CollisionMask.ROWS) * RoomAddress.GRID_W + x / CollisionMask.COLS;
            rooms[room] = Math.min(rooms[room], d);
            for (int[] s : steps) {
                int nx = x + s[0];
                int ny = y + s[1];
                if (nx < 0 || ny < 0 || nx >= cols || ny >= WorldGrid.ROWS || !grid.blockPassable(nx, ny)) {
                    continue;
                }
                boolean crossing = nx / CollisionMask.COLS != x / CollisionMask.COLS
                        || ny / CollisionMask.ROWS != y / CollisionMask.ROWS;
                int nd = d + (crossing ? 1 : 0);
                if (nd < best[ny * cols + nx]) {
                    best[ny * cols + nx] = nd;
                    // 0-1 breadth-first: free steps to the front, crossings to the back.
                    if (crossing) {
                        queue.addLast(new int[] {nx, ny, nd});
                    } else {
                        queue.addFirst(new int[] {nx, ny, nd});
                    }
                }
            }
        }
        return rooms;
    }
}
