package wulf.tools;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import wulf.Content;
import wulf.data.CreatureData;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.engine.ReplayRecorder;
import wulf.input.InputState;
import wulf.sim.Creature;
import wulf.sim.Direction8;
import wulf.sim.Player;
import wulf.sim.Simulation;
import wulf.sim.Wulf;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/**
 * Dev tool, not a test: finds and records {@code replays/wulf_escape.json} — a Wulf
 * chase survived across four rooms (AGENTS.md §22.6, M5).
 *
 * <p>A bot walks a planned route from the start room towards a far corner. It swings
 * only when the blade will land: for each facing it projects the sabre's live ticks
 * against where the Wulf, or a creature, will be on its current heading — the parry
 * a practised player times by eye. With the Wulf close and not stunned it stands its
 * ground, so the timing holds; while it is stunned, it runs. Run seeds are tried until one run
 * has a natural appearance whose chase spans four rooms and ends in an escape, with
 * the player never dying. The route planner is CornerWalkTest's, steering included.
 *
 * <pre>java -cp target/test-classes:target/classes:&lt;deps&gt; wulf.tools.WulfEscapeForge [out.json]</pre>
 */
public final class WulfEscapeForge {

    private static final int ROOMS_TO_SURVIVE = 4;
    private static final int TAIL_TICKS = 100;
    private static final int STAND_GROUND_PX = 56;

    private record Attempt(boolean escaped, ReplayRecorder recorder, String story) {
    }

    static int longestChase;
    static long swings;
    static long parries;
    static long kills;

    private WulfEscapeForge() {
    }

    public static void main(String[] args) {
        Path out = Path.of(args.length > 0 ? args[0] : "replays/wulf_escape.json");
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        RoomAddress start = c.map().startRoom();
        RoomAddress[] targets = {new RoomAddress(14, 1), new RoomAddress(1, 1), new RoomAddress(14, 14), new RoomAddress(1, 14),
            new RoomAddress(8, 1), new RoomAddress(1, 8), new RoomAddress(14, 8), new RoomAddress(8, 14)};
        List<List<int[]>> routes = new java.util.ArrayList<>();
        for (RoomAddress target : targets) {
            Simulation probe = Simulation.startingIn(c.player(), grid, 0, start);
            routes.add(route(grid, c.player().collisionBox(), probe, target));
        }
        java.util.Map<String, Integer> outcomes = new java.util.TreeMap<>();
        for (long seed = 1; seed <= 5000; seed++) {
            for (int r = 0; r < targets.length; r++) {
                Attempt a = attempt(c, grid, start, routes.get(r), seed);
                outcomes.merge(a.escaped() ? "escaped" : a.story(), 1, Integer::sum);
                if (a.escaped()) {
                    String note = "Forged by WulfEscapeForge: " + a.story() + " Replayed by ReplayTest.";
                    a.recorder().note(note).write(out);
                    System.out.println("wulf escape: seed " + seed + " towards " + targets[r] + " — " + a.story());
                    System.out.println("wrote " + out + " (" + a.recorder().ticks() + " ticks)");
                    return;
                }
            }
        }
        System.err.println("no escape found; outcomes: " + outcomes);
        System.err.println("swings " + swings + ", parries " + parries + ", kills " + kills + ", longest chase " + longestChase + " rooms");
        System.exit(1);
    }

    private static Attempt attempt(Content c, WorldGrid grid, RoomAddress start, List<int[]> route, long seed) {
        Simulation sim = Simulation.startingIn(c.player(), grid, c.game().transition().freezeTicks(), start, c.ecosystem(), seed);
        ReplayRecorder rec = new ReplayRecorder("wulf_escape", c.db().contentHash(), seed, start, false);
        Wulf w = sim.wulf();
        int block = 0;
        int sinceProgress = 0;
        boolean chasing = false;
        int chaseRooms = 0;
        int evasionsBefore = 0;
        int parriesBefore = 0;
        RoomAddress chaseRoom = sim.room();
        RoomAddress origin = sim.room();
        int doneAt = -1;
        int appearedAt = 0;
        String story = "";
        for (int t = 0; t < 30_000; t++) {
            InputState in = null;
            if (!sim.player().swinging() && sim.player().cooldown() == 0) {
                Direction8 d = swingThatLands(sim);
                if (d != null) {
                    in = InputState.of(d.dx(), d.dy(), true, true);
                    swings++;
                }
            }
            if (in == null && w.state() == Wulf.State.PURSUE && w.body().hurtTicks() == 0 && gap(sim) <= STAND_GROUND_PX) {
                in = InputState.NONE;   // let it come to the blade
            }
            if (in == null) {
                while (block < route.size() && reached(sim, route.get(block))) {
                    block++;
                    sinceProgress = 0;
                }
                if (block >= route.size()) {
                    return new Attempt(false, rec, "route finished" + (story.isEmpty() ? "" : " after: " + story));
                }
                int ddx = Fixed.fp(route.get(block)[0] * 8 + 8) - sim.player().xFp();
                int ddy = Fixed.fp(route.get(block)[1] * 8 + 13) - sim.player().yFp();
                int dx = Math.abs(ddx) > Fixed.ONE ? Integer.signum(ddx) : 0;
                int dy = Math.abs(ddy) > Fixed.ONE ? Integer.signum(ddy) : 0;
                in = touches(sim, dx, dy) ? InputState.NONE : InputState.of(dx, dy, false, false);
                if (++sinceProgress > 600) {
                    return new Attempt(false, rec, "stuck" + (chasing ? " in a chase" : ""));
                }
            }
            Wulf.State before = w.state();
            RoomAddress roomBefore = sim.room();
            sim.play(in, false);
            rec.record(in, sim);
            if (sim.player().mode() != Player.Mode.ALIVE) {
                parries += w.parries();
                kills += sim.kills();
                PlayerData.Box pb = sim.rules().collisionBox();
                int px = Fixed.px(sim.player().xFp()) + pb.x();
                int py = Fixed.px(sim.player().yFp()) + pb.y();
                CreatureData.Box wb = w.body().species().collisionBox();
                int wx = Fixed.px(w.body().xFp()) + wb.x();
                int wy = Fixed.px(w.body().yFp()) + wb.y();
                boolean byWulf = w.touchable() && px < wx + wb.w() && wx < px + pb.w() && py < wy + wb.h() && wy < py + pb.h();
                if (byWulf) {
                    String when = w.followedFlips() > 0 && w.stateTick() <= 2 ? "on arrival"
                            : w.state() == Wulf.State.WARNING ? "while it warned"
                            : "chasing, " + (w.stateTick() < 60 ? "<60" : w.stateTick() < 200 ? "<200" : "200+") + " ticks in";
                    return new Attempt(false, rec, "died to the Wulf " + when + (sim.player().swinging() ? " mid-swing" : "")
                            + (w.body().hurtTicks() > 0 ? " (it was stunned)" : ""));
                }
                String who = "?";
                for (Creature cr : sim.creatures()) {
                    CreatureData.Box b = cr.species().collisionBox();
                    int cx = Fixed.px(cr.xFp()) + b.x();
                    int cy = Fixed.px(cr.yFp()) + b.y();
                    if (cr.alive() && px < cx + b.w() && cx < px + pb.w() && py < cy + b.h() && cy < py + pb.h()) {
                        who = cr.species().id();
                    }
                }
                if (who.equals("?") && !sim.spears().isEmpty()) {
                    who = "spear";
                }
                return new Attempt(false, rec, "died to " + who + (chasing ? " in a chase" : "") + (sim.player().swinging() ? " mid-swing" : ""));
            }
            // A chase begins when it appears, and a room counts only if it was after you when you flipped into it.
            // (LEAVING is not a chase: counting from it once credited ordinary wandering to an escape.)
            boolean after = before == Wulf.State.WARNING || before == Wulf.State.PURSUE || before == Wulf.State.ARRIVING;
            if (before == Wulf.State.ABSENT && w.state() == Wulf.State.WARNING && doneAt < 0) {
                chasing = true;
                chaseRooms = 1;
                chaseRoom = sim.room();
                origin = sim.room();
                evasionsBefore = w.evasions();
                parriesBefore = w.parries();
                appearedAt = t;
            }
            if (chasing && after && !sim.room().equals(roomBefore)) {
                chaseRooms++;
                chaseRoom = sim.room();
                longestChase = Math.max(longestChase, chaseRooms);
            }
            if (chasing && w.state() == Wulf.State.ABSENT && w.evasions() == evasionsBefore) {
                chasing = false;
            }
            if (chasing && w.evasions() > evasionsBefore) {
                chasing = false;
                if (chaseRooms < ROOMS_TO_SURVIVE) {
                    story = "escaped a chase of " + chaseRooms + " rooms";
                }
                if (chaseRooms >= ROOMS_TO_SURVIVE) {
                    doneAt = t;
                    story = "the Wulf appeared in " + origin + " at tick " + appearedAt + " and chased across "
                            + chaseRooms + " rooms to " + sim.room() + ", parried " + (w.parries() - parriesBefore) + " times, "
                            + (w.state() == Wulf.State.LEAVING ? "until it gave up" : "until it was outrun") + "; no lives lost.";
                }
            }
            if (doneAt >= 0 && t >= doneAt + TAIL_TICKS) {
                return new Attempt(true, rec, story);
            }
        }
        return new Attempt(false, rec, "out of time");
    }

    /** A facing whose swing lands on the Wulf, or failing that a creature, before either touches the player; or null. */
    private static Direction8 swingThatLands(Simulation sim) {
        Wulf w = sim.wulf();
        if (w.touchable()) {
            Direction8 d = swingThatLands(sim, w.body());
            if (d != null) {
                return d;
            }
        }
        for (Creature cr : sim.creatures()) {
            if (cr.alive()) {
                Direction8 d = swingThatLands(sim, cr);
                if (d != null) {
                    return d;
                }
            }
        }
        return null;
    }

    private static Direction8 swingThatLands(Simulation sim, Creature target) {
        PlayerData rules = sim.rules();
        PlayerData.Box pb = rules.collisionBox();
        PlayerData.Sabre sabre = rules.sabre();
        CreatureData.Box tb = target.species().collisionBox();
        CreatureData.Speed speed = target.species().speed();
        boolean diagonalTarget = target.dirX() != 0 && target.dirY() != 0;
        int scale = diagonalTarget ? sim.ecosystem().creatures().diagonalScaleFp() : Fixed.ONE;
        int vx = target.dirX() * Fixed.mul(speed.xFp(), scale);
        int vy = target.dirY() * Fixed.mul(speed.yFp(), scale);
        Direction8 toward = Direction8.toward(target.centreXPx() - Fixed.px(sim.player().xFp()),
                target.centreYPx() - (Fixed.px(sim.player().yFp()) + pb.y() + pb.h() / 2));
        for (int turn = 0; turn < 8; turn++) {
            Direction8 d = toward.rotate(turn % 2 == 0 ? turn / 2 : -(turn + 1) / 2);
            // Pressing a direction to face it also steps that way, once.
            int step = d.diagonal() ? Fixed.mul(Fixed.ONE, rules.speed().diagonalScaleFp()) : Fixed.ONE;
            int pxFp = sim.player().xFp() + d.dx() * Fixed.mul(rules.speed().xFp(), step);
            int pyFp = sim.player().yFp() + d.dy() * Fixed.mul(rules.speed().yFp(), step);
            int cx = Fixed.px(pxFp) + pb.x() + pb.w() / 2;
            int cy = Fixed.px(pyFp) + pb.y() + pb.h() / 2;
            for (int k = 1; k <= sabre.windupTicks() + sabre.activeTicks() - 1; k++) {
                int tx = Fixed.px(target.xFp() + k * vx) + tb.x();
                int ty = Fixed.px(target.yFp() + k * vy) + tb.y();
                if (k >= sabre.windupTicks()) {
                    int[] r = blade(d, cx, cy, sabre);
                    // A pixel of margin each side: the heading is a guess a tick or two out.
                    if (r[0] < tx + tb.w() - 1 && tx + 1 < r[0] + r[2] && r[1] < ty + tb.h() - 1 && ty + 1 < r[1] + r[3]) {
                        return d;
                    }
                }
                int bx = Fixed.px(pxFp) + pb.x();
                int by = Fixed.px(pyFp) + pb.y();
                if (bx < tx + tb.w() + 1 && tx - 1 < bx + pb.w() && by < ty + tb.h() + 1 && ty - 1 < by + pb.h()) {
                    break;   // it touches first
                }
            }
        }
        return null;
    }

    private static int gap(Simulation sim) {
        PlayerData.Box pb = sim.rules().collisionBox();
        Creature b = sim.wulf().body();
        return Math.max(Math.abs(b.centreXPx() - (Fixed.px(sim.player().xFp()) + pb.x() + pb.w() / 2)),
                Math.abs(b.centreYPx() - (Fixed.px(sim.player().yFp()) + pb.y() + pb.h() / 2)));
    }

    /** Whether stepping this way next tick would bring the player within 2 px of the Wulf or a live creature. */
    private static boolean touches(Simulation sim, int dx, int dy) {
        PlayerData rules = sim.rules();
        PlayerData.Box pb = rules.collisionBox();
        int bx = Fixed.px(sim.player().xFp() + dx * rules.speed().xFp()) + pb.x();
        int by = Fixed.px(sim.player().yFp() + dy * rules.speed().yFp()) + pb.y();
        List<Creature> near = new java.util.ArrayList<>(sim.creatures());
        if (sim.wulf().touchable()) {
            near.add(sim.wulf().body());
        }
        for (Creature cr : near) {
            if (!cr.alive()) {
                continue;
            }
            CreatureData.Box b = cr.species().collisionBox();
            int cx = Fixed.px(cr.xFp()) + b.x() - 2;
            int cy = Fixed.px(cr.yFp()) + b.y() - 2;
            if (bx < cx + b.w() + 4 && cx < bx + pb.w() && by < cy + b.h() + 4 && cy < by + pb.h()) {
                return true;
            }
        }
        return false;
    }

    /** Simulation.computeSabre's rectangle, {x, y, w, h}, for a swing facing {@code d} from a feet-box centre. */
    private static int[] blade(Direction8 d, int cx, int cy, PlayerData.Sabre s) {
        if (d.dx() != 0) {
            int x = d.dx() > 0 ? cx : cx - s.reachPx();
            int y = cy - s.thicknessPx() / 2 + (d.diagonal() ? s.diagonalOffsetPx() * d.dy() : 0);
            return new int[] {x, y, s.reachPx(), s.thicknessPx()};
        }
        int y = d.dy() > 0 ? cy : cy - s.reachPx();
        return new int[] {cx - s.thicknessPx() / 2, y, s.thicknessPx(), s.reachPx()};
    }

    private static boolean reached(Simulation sim, int[] block) {
        int tx = Fixed.fp(block[0] * 8 + 8);
        int ty = Fixed.fp(block[1] * 8 + 13);
        return Math.abs(tx - sim.player().xFp()) <= Fixed.ONE && Math.abs(ty - sim.player().yFp()) <= Fixed.ONE;
    }

    /** Breadth-first over 2x2-cell blocks — CornerWalkTest's planner. */
    private static List<int[]> route(WorldGrid grid, PlayerData.Box box, Simulation sim, RoomAddress target) {
        int cols = WorldGrid.COLS;
        int startX = Math.floorDiv(Fixed.px(sim.player().xFp()) + box.x(), Simulation.CELL_PX);
        int startY = Math.floorDiv(Fixed.px(sim.player().yFp()) + box.y(), Simulation.CELL_PX);
        int[] parent = new int[cols * WorldGrid.ROWS];
        Arrays.fill(parent, -2);
        int[] queue = new int[parent.length];
        int head = 0;
        int tail = 0;
        int start = startY * cols + startX;
        parent[start] = -1;
        queue[tail++] = start;
        int goal = -1;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int i = queue[head++];
            int x = i % cols;
            int y = i / cols;
            int centreX = x * 8 + 8 - target.col() * Simulation.ROOM_W_PX;
            int centreY = y * 8 + 8 - target.row() * Simulation.ROOM_H_PX;
            if (centreX >= 8 && centreX < Simulation.ROOM_W_PX - 8 && centreY >= 8 && centreY < Simulation.ROOM_H_PX - 8) {
                goal = i;
                break;
            }
            for (int[] s : steps) {
                int nx = x + s[0];
                int ny = y + s[1];
                if (nx < 0 || ny < 0 || nx >= cols || ny >= WorldGrid.ROWS) {
                    continue;
                }
                int j = ny * cols + nx;
                if (parent[j] != -2 || !grid.blockPassable(nx, ny)) {
                    continue;
                }
                parent[j] = i;
                queue[tail++] = j;
            }
        }
        LinkedList<int[]> path = new LinkedList<>();
        for (int i = goal; i >= 0; i = parent[i]) {
            path.addFirst(new int[] {i % cols, i / cols});
        }
        return path;
    }
}
