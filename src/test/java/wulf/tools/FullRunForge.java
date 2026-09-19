package wulf.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import wulf.Content;
import wulf.data.CreatureData;
import wulf.data.LandmarksData;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.engine.ReplayRecorder;
import wulf.input.InputState;
import wulf.sim.Creature;
import wulf.sim.Direction8;
import wulf.sim.Player;
import wulf.sim.Quest;
import wulf.sim.Simulation;
import wulf.sim.Wulf;
import wulf.sim.ai.GuardOrbit;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/**
 * Dev tool, not a test: finds and records {@code replays/full_run.json} — the whole
 * game won, all four quarters and out through the arch (AGENTS.md §22.6, M6).
 *
 * <p>A bot plays the real game from the start room. It visits the lairs nearest first,
 * then the way out. It swings only when the blade will land; it stands its ground when
 * the Wulf closes, a guardian charges or a creature comes at it, so the blow lands; and
 * it routes around every guardian's ground but the one it is fetching from.
 *
 * <p>The bot is no better than a nervous beginner, so the forge searches: the game is
 * deterministic, so when the bot dies or stalls, the forge rewinds — replays the input
 * so far, minus a stretch, into a fresh simulation — and tries again from there with a
 * random pause that changes every timing after it. Rewinds reach further back when
 * failures repeat. What it records is an ordinary input stream through the real game,
 * with no death in it: proof the game can be won, not a demonstration of good play.
 *
 * <pre>java -cp target/test-classes:target/classes:&lt;deps&gt; wulf.tools.FullRunForge [out.json]</pre>
 */
public final class FullRunForge {

    /** Lair visiting orders to try, by index into landmarks.lairs(): nearest first, two ways round. */
    private static final int[][] ORDERS = {{3, 2, 0, 1}, {3, 1, 0, 2}};
    private static int[] LAIR_ORDER = ORDERS[0];
    private static final int GIVE_WAY_TICKS = 40;
    private static final int CHASER_STAND_PX = 48;
    /** Behaviours that come for the player: better met on the blade than walked away from. */
    private static final java.util.Set<String> CHASERS = java.util.Set.of("CHASE_AXIS", "CHASE_DIRECT", "AMBUSH_BURST");
    static final Map<String, Integer> KILLERS = new TreeMap<>();
    private static final boolean TRACE = Boolean.getBoolean("forge.trace");
    /** Standing ground for longer than this means it cannot reach us: stop waiting for it for a while. */
    private static final int STAND_LIMIT_TICKS = 60;
    private static final int IGNORE_TICKS = 90;
    /** Routes keep this far from every pedestal but the one being fetched: a guardian's ground (ring + body). */
    private static final int GUARDIAN_GROUND_PX = 56;

    /** Ticks spent standing ground, and ticks left of ignoring a guardian that cannot come. Bot state, not game state. */
    private static int standing;
    private static int ignoring;
    private static final int STAND_GROUND_PX = 56;
    private static final int GIVE_UP_WAITING_TICKS = 1500;
    private static final int MAX_TICKS = 120_000;

    private record Attempt(boolean won, ReplayRecorder recorder, String story) {
    }

    private FullRunForge() {
    }

    public static void main(String[] args) {
        Path out = Path.of(args.length > 0 ? args[0] : "replays/full_run.json");
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        Map<String, Integer> outcomes = new TreeMap<>();
        int bestGoal = 0;
        long first = Long.getLong("forge.seed", 1L);
        long last = Long.getLong("forge.lastSeed", first + 39);
        for (long seed = first; seed <= last; seed++) {
            LAIR_ORDER = ORDERS[Integer.getInteger("forge.order", (int) (seed % ORDERS.length))];
            Attempt a = attempt(c, grid, seed);
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("at goal (\\d)").matcher(a.story());
            if (m.find()) {
                bestGoal = Math.max(bestGoal, Integer.parseInt(m.group(1)));
            }
            String kind = a.won() ? "won" : a.story().replaceAll(" at tick.*", "");
            outcomes.merge(kind, 1, Integer::sum);
            System.err.println("  seed " + seed + ": " + a.story());
            if (a.won()) {
                a.recorder().note("Forged by FullRunForge: " + a.story() + " Replayed by ReplayTest.").write(out);
                System.out.println("full run: seed " + seed + " — " + a.story());
                System.out.println("wrote " + out + " (" + a.recorder().ticks() + " ticks)");
                return;
            }
        }
        System.err.println("killers: " + KILLERS);
        System.err.println("no win found; furthest goal reached " + bestGoal + "; outcomes: " + outcomes);
        System.exit(1);
    }

    private static final int MAX_REWINDS = Integer.getInteger("forge.rewinds", 4000);

    private static Simulation fresh(Content c, WorldGrid grid, long seed) {
        return Simulation.startingIn(c.player(), grid, c.game().transition().freezeTicks(), c.map().startRoom(),
                c.ecosystem(), seed);
    }

    private static Attempt attempt(Content c, WorldGrid grid, long seed) {
        LandmarksData marks = c.landmarks();
        PlayerData.Box box = c.player().collisionBox();
        wulf.engine.Rng search = new wulf.engine.Rng(seed * 7919 + 17);
        List<InputState> inputs = new ArrayList<>();
        Simulation sim = fresh(c, grid, seed);
        int rewinds = 0;
        int furthest = 0;
        int anchor = 0;
        int piecesSeen = 0;
        int failsHere = 0;
        int pause = 0;
        int goal = 0;
        List<int[]> route = List.of();
        int block = 0;
        int sinceProgress = 0;
        boolean replan = true;
        int waiting = 0;
        standing = 0;
        ignoring = 0;
        dodgePer10k = 0;
        while (inputs.size() < MAX_TICKS) {
            Player.Mode mode = sim.player().mode();
            if (mode == Player.Mode.WON) {
                ReplayRecorder rec = new ReplayRecorder("full_run", c.db().contentHash(), seed, c.map().startRoom(), false);
                Simulation check = fresh(c, grid, seed);
                for (InputState in : inputs) {
                    check.play(in, false);
                    rec.record(in, check);
                }
                return new Attempt(true, rec, "all four quarters (lairs in order " + Arrays.toString(LAIR_ORDER)
                        + ") and out through the arch in " + inputs.size() + " ticks without a death, score "
                        + check.score() + "; found after " + rewinds + " rewinds.");
            }
            boolean failed = mode != Player.Mode.ALIVE && mode != Player.Mode.ESCAPING || sinceProgress > 2000
                    || waiting > GIVE_UP_WAITING_TICKS;
            if (failed) {
                String why = mode == Player.Mode.DYING ? "killed by " + killer(sim)
                        : sinceProgress > 2000 ? "stalled" : "waited";
                if (mode == Player.Mode.DYING) {
                    KILLERS.merge(killer(sim), 1, Integer::sum);
                }
                if (TRACE && (sim.quest().piecesHeld() == 4 ? rewinds % 20 == 0 : rewinds % 500 == 0)) {
                    System.err.printf("    rewind %d: tick %d, %s in %s at %d,%d, pieces %d, block %d/%d, back up to %d%n",
                            rewinds, inputs.size(), why, sim.room(),
                            Fixed.px(sim.player().xFp()) - sim.room().col() * 256, Fixed.px(sim.player().yFp()) - sim.room().row() * 192,
                            sim.quest().piecesHeld(), block, route.size(), furthest);
                }
                if (++rewinds > MAX_REWINDS) {
                    return new Attempt(false, null, "gave up at goal " + goal + " after " + rewinds + " rewinds, furthest tick "
                            + furthest);
                }
                failsHere = inputs.size() > furthest ? 0 : failsHere + 1;
                furthest = Math.max(furthest, inputs.size());
                int back = 30 + search.nextInt(120 + 60 * Math.min(failsHere, 40));
                if (failsHere > 120) {
                    anchor = 0;   // this whole approach is doomed: let it re-plan from further back
                    failsHere = 0;
                }
                // Never undo a quarter already won unless that has stopped working.
                int keep = Math.max(anchor, inputs.size() - back);
                inputs = new ArrayList<>(inputs.subList(0, keep));
                sim = fresh(c, grid, seed);
                for (InputState in : inputs) {
                    sim.play(in, false);
                }
                piecesSeen = sim.quest().inLair() ? Math.min(piecesSeen, sim.quest().piecesHeld()) : sim.quest().piecesHeld();
                pause = search.nextInt(90);
                dice = new wulf.engine.Rng(search.nextLong());
                dodgePer10k = 500 + search.nextInt(2500);
                goal = 0;
                replan = true;
                waiting = 0;
                sinceProgress = 0;
                standing = 0;
                ignoring = 0;
                continue;
            }
            while (goal < LAIR_ORDER.length && sim.quest().taken(LAIR_ORDER[goal])) {
                goal++;
                replan = true;
                waiting = 0;
            }
            InputState in = InputState.NONE;
            if (mode == Player.Mode.ALIVE && sim.freezeRemaining() == 0) {
                if (pause > 0) {
                    pause--;
                } else {
                    if (replan) {
                        int[] target = goal < LAIR_ORDER.length ? pedestalBlock(marks, LAIR_ORDER[goal]) : exitBlock(marks);
                        List<int[]> planned = route(grid, box, sim, target, marks, goal < LAIR_ORDER.length ? LAIR_ORDER[goal] : -1);
                        if (planned.isEmpty() && route.isEmpty()) {
                            sinceProgress = Integer.MAX_VALUE / 2;   // nowhere to go from here: rewind
                            continue;
                        }
                        block = planned.isEmpty() ? nearestBlock(sim, route) : 0;
                        if (!planned.isEmpty()) {
                            route = planned;
                        }
                        sinceProgress = 0;
                        replan = false;
                    }
                    InputState chosen = decide(sim, marks, goal, route, block, waiting);
                    if (chosen == null) {
                        waiting++;
                    } else {
                        waiting = 0;
                        in = chosen;
                    }
                    while (block < route.size() && reached(sim, route.get(block))) {
                        block++;
                        sinceProgress = 0;
                    }
                    sinceProgress++;
                    if (block >= route.size() && goal < LAIR_ORDER.length && !sim.quest().taken(LAIR_ORDER[goal])) {
                        replan = true;
                    }
                }
            }
            sim.play(in, false);
            inputs.add(in);
            if (sim.quest().piecesHeld() > piecesSeen && !sim.quest().inLair()) {
                // A quarter counts as won once it is out of the lair: anchoring on the pickup itself
                // pinned the search inside a guardian's reach.
                piecesSeen = sim.quest().piecesHeld();
                anchor = inputs.size();
            }
        }
        return new Attempt(false, null, "out of time at goal " + goal);
    }

    /** After a rewind, the search's own dice: how often to dodge instead of following the plan near a threat. */
    private static wulf.engine.Rng dice = new wulf.engine.Rng(1);
    private static int dodgePer10k;

    /** One tick's choice. Null means "hold still and wait" (counted towards giving up). */
    private static InputState decide(Simulation sim, LandmarksData marks, int goal, List<int[]> route, int block, int waiting) {
        // marks and goal are kept for strategies that need them; this one plays every lair the same way.
        Player p = sim.player();
        if (!p.swinging() && p.cooldown() == 0) {
            Direction8 d = swingThatLands(sim);
            if (d != null) {
                return InputState.of(d.dx(), d.dy(), true, true);
            }
        }
        Creature nearest = null;
        int nearestGap = Integer.MAX_VALUE;
        for (Creature cr : threats(sim)) {
            int g = gap(sim, cr);
            if (g < nearestGap) {
                nearestGap = g;
                nearest = cr;
            }
        }
        // Fleeing whatever cannot be killed looked sensible and was not: the bot ran from the Wulf
        // for ever and never finished a run. Only give ground to something already struck.
        if (nearest != null && nearestGap <= 40 && (p.swinging() || p.cooldown() > 0)
                && (nearest.hurtTicks() > 0 || nearest.hp() > 1)) {
            // Struck it and it is still coming: give ground until the blade is ready again.
            PlayerData.Box pb = sim.rules().collisionBox();
            Direction8 away = Direction8.toward(Fixed.px(p.xFp()) + pb.x() + pb.w() / 2 - nearest.centreXPx(),
                    Fixed.px(p.yFp()) + pb.y() + pb.h() / 2 - nearest.centreYPx());
            return InputState.of(away.dx(), away.dy(), false, false);
        }
        if (nearest != null && nearestGap <= 64 && dodgePer10k > 0 && dice.chance(dodgePer10k)) {
            Direction8 d = Direction8.N.rotate(dice.nextInt(8));
            return InputState.of(d.dx(), d.dy(), false, false);
        }
        // Something coming at us, close: stand and let it meet the blade rather than walk into it.
        // Not for ever — a thing that cannot reach us is walked past after STAND_LIMIT_TICKS.
        if (ignoring > 0) {
            ignoring--;
        } else if (comingForUs(sim)) {
            if (++standing <= STAND_LIMIT_TICKS) {
                return InputState.NONE;
            }
            ignoring = IGNORE_TICKS;
        }
        if (ignoring == 0 && !comingForUs(sim)) {
            standing = 0;
        }
        if (block >= route.size()) {
            return InputState.NONE;
        }
        InputState step = steer(sim, route.get(block));
        // Give way to what is in the path — but not for ever: a creature parked on the only way is walked past.
        return touches(sim, step.dx(), step.dy()) && waiting < GIVE_WAY_TICKS ? null : step;
    }

    private static int nearestBlock(Simulation sim, List<int[]> route) {
        int best = 0;
        long bestD = Long.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            long dx = Fixed.fp(route.get(i)[0] * 8 + 8) - (long) sim.player().xFp();
            long dy = Fixed.fp(route.get(i)[1] * 8 + 13) - (long) sim.player().yFp();
            if (dx * dx + dy * dy < bestD) {
                bestD = dx * dx + dy * dy;
                best = i;
            }
        }
        return best;
    }

    private static boolean comingForUs(Simulation sim) {
        Wulf w = sim.wulf();
        if (w.state() == Wulf.State.PURSUE && w.body().hurtTicks() == 0 && gap(sim, w.body()) <= STAND_GROUND_PX) {
            return true;
        }
        Quest q = sim.quest();
        if (q.inLair()) {
            Creature g = q.guardian();
            if (g.phase() == GuardOrbit.LUNGING && g.hurtTicks() == 0 && gap(sim, g) <= STAND_GROUND_PX) {
                return true;
            }
        }
        PlayerData.Box pb = sim.rules().collisionBox();
        int px = Fixed.px(sim.player().xFp()) + pb.x() + pb.w() / 2;
        int py = Fixed.px(sim.player().yFp()) + pb.y() + pb.h() / 2;
        for (Creature cr : sim.creatures()) {
            if (!cr.alive() || cr.moveTicks() == 0) {
                continue;
            }
            int d = gap(sim, cr);
            boolean chaser = CHASERS.contains(cr.species().behaviour().kind());
            boolean heading = cr.dirX() * (px - cr.centreXPx()) + cr.dirY() * (py - cr.centreYPx()) > 0;
            if ((chaser && d <= CHASER_STAND_PX) || (heading && d <= 40)) {
                return true;
            }
        }
        return false;
    }

    /** What was touching the player as they fell. */
    private static String killer(Simulation sim) {
        PlayerData.Box pb = sim.rules().collisionBox();
        int px = Fixed.px(sim.player().xFp()) + pb.x();
        int py = Fixed.px(sim.player().yFp()) + pb.y();
        List<Creature> all = threats(sim);
        if (sim.quest().keeperHere()) {
            all.add(sim.quest().keeper());
        }
        for (Creature cr : all) {
            CreatureData.Box b = cr.species().collisionBox();
            int cx = Fixed.px(cr.xFp()) + b.x();
            int cy = Fixed.px(cr.yFp()) + b.y();
            if (px < cx + b.w() + 1 && cx - 1 < px + pb.w() && py < cy + b.h() + 1 && cy - 1 < py + pb.h()) {
                return cr.species().id();
            }
        }
        return sim.spears().isEmpty() ? "?" : "spear";
    }

    private static String where(Simulation sim, int t, int deaths) {
        return " at tick " + t + ", local " + (Fixed.px(sim.player().xFp()) - sim.room().col() * 256) + ","
                + (Fixed.px(sim.player().yFp()) - sim.room().row() * 192) + ", " + deaths + " deaths, "
                + sim.creatures().size() + " creatures, Wulf " + sim.wulf().state();
    }

    private static InputState steer(Simulation sim, int[] target) {
        int ddx = Fixed.fp(target[0] * 8 + 8) - sim.player().xFp();
        int ddy = Fixed.fp(target[1] * 8 + 13) - sim.player().yFp();
        int dx = Math.abs(ddx) > Fixed.ONE ? Integer.signum(ddx) : 0;
        int dy = Math.abs(ddy) > Fixed.ONE ? Integer.signum(ddy) : 0;
        return InputState.of(dx, dy, false, false);
    }

    /** The route block whose steering point puts the feet on the quarter (§14.6). */
    private static int[] pedestalBlock(LandmarksData marks, int lair) {
        RoomAddress room = LandmarksData.room(marks.lairs().get(lair).room());
        LandmarksData.Point p = marks.lairs().get(lair).pedestal();
        return new int[] {Math.floorDiv(room.col() * 256 + p.x() - 8, 8), Math.floorDiv(room.row() * 192 + p.y() - 13, 8)};
    }

    /** The route block in the bottom of the exit zone: the arch's own cells above it are solid. */
    private static int[] exitBlock(LandmarksData marks) {
        RoomAddress room = marks.exitRoom();
        LandmarksData.Rect z = marks.exit().zone();
        int feetY = z.y() + z.h() - 11;
        return new int[] {Math.floorDiv(room.col() * 256 + z.x() + z.w() / 2 - 8, 8), Math.floorDiv(room.row() * 192 + feetY - 13, 8)};
    }

    private static boolean reached(Simulation sim, int[] block) {
        return Math.abs(Fixed.fp(block[0] * 8 + 8) - sim.player().xFp()) <= Fixed.ONE
                && Math.abs(Fixed.fp(block[1] * 8 + 13) - sim.player().yFp()) <= Fixed.ONE;
    }

    private static int gap(Simulation sim, Creature body) {
        PlayerData.Box pb = sim.rules().collisionBox();
        return Math.max(Math.abs(body.centreXPx() - (Fixed.px(sim.player().xFp()) + pb.x() + pb.w() / 2)),
                Math.abs(body.centreYPx() - (Fixed.px(sim.player().yFp()) + pb.y() + pb.h() / 2)));
    }

    /** Anything lethal the swing could meet: the Wulf, the guardian, then the creatures. */
    private static List<Creature> threats(Simulation sim) {
        List<Creature> out = new ArrayList<>();
        if (sim.wulf().touchable()) {
            out.add(sim.wulf().body());
        }
        if (sim.quest().inLair()) {
            out.add(sim.quest().guardian());
        }
        for (Creature cr : sim.creatures()) {
            if (cr.alive()) {
                out.add(cr);
            }
        }
        return out;
    }

    private static Direction8 swingThatLands(Simulation sim) {
        for (Creature target : threats(sim)) {
            Direction8 d = swingThatLands(sim, target);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    /** WulfEscapeForge's prediction: the blade's live ticks against the target's heading. */
    private static Direction8 swingThatLands(Simulation sim, Creature target) {
        PlayerData rules = sim.rules();
        PlayerData.Box pb = rules.collisionBox();
        PlayerData.Sabre sabre = rules.sabre();
        CreatureData.Box tb = target.species().collisionBox();
        CreatureData.Speed speed = target.species().speed();
        boolean diagonal = target.dirX() != 0 && target.dirY() != 0;
        int scale = diagonal ? sim.ecosystem().creatures().diagonalScaleFp() : Fixed.ONE;
        int vx = target.dirX() * Fixed.mul(speed.xFp(), scale);
        int vy = target.dirY() * Fixed.mul(speed.yFp(), scale);
        String kind = target.species().behaviour().kind();
        boolean homing = kind.equals("CHASE_DIRECT") || kind.equals("CHASE_AXIS")
                || (kind.equals("GUARD_ORBIT") && target.phase() == GuardOrbit.LUNGING);
        Direction8 toward = Direction8.toward(target.centreXPx() - Fixed.px(sim.player().xFp()),
                target.centreYPx() - (Fixed.px(sim.player().yFp()) + pb.y() + pb.h() / 2));
        for (int turn = 0; turn < 8; turn++) {
            Direction8 d = toward.rotate(turn % 2 == 0 ? turn / 2 : -(turn + 1) / 2);
            int step = d.diagonal() ? rules.speed().diagonalScaleFp() : Fixed.ONE;
            int pxFp = sim.player().xFp() + d.dx() * Fixed.mul(rules.speed().xFp(), step);
            int pyFp = sim.player().yFp() + d.dy() * Fixed.mul(rules.speed().yFp(), step);
            int cx = Fixed.px(pxFp) + pb.x() + pb.w() / 2;
            int cy = Fixed.px(pyFp) + pb.y() + pb.h() / 2;
            int hx = target.xFp();
            int hy = target.yFp();
            for (int k = 1; k <= sabre.windupTicks() + sabre.activeTicks() - 1; k++) {
                if (homing && target.aux() <= k) {
                    // It turns towards us as it comes: step it along that line rather than its old heading.
                    int ddx = Fixed.fp(cx) - (hx + Fixed.fp(tb.x() + tb.w() / 2));
                    int ddy = Fixed.fp(cy) - (hy + Fixed.fp(tb.y() + tb.h() / 2));
                    Direction8 to = Direction8.toward(ddx, ddy);
                    int sc = to.diagonal() ? sim.ecosystem().creatures().diagonalScaleFp() : Fixed.ONE;
                    int ax = Fixed.mul(speed.xFp(), sc);
                    int ay = Fixed.mul(speed.yFp(), sc);
                    if (kind.equals("CHASE_AXIS")) {
                        boolean alongX = Math.abs(ddx) >= Math.abs(ddy);
                        ax = alongX ? speed.xFp() : speed.xFp() / 2;
                        ay = alongX ? speed.yFp() / 2 : speed.yFp();
                    }
                    hx += Integer.signum(ddx) * Math.min(Math.abs(ddx), ax);
                    hy += Integer.signum(ddy) * Math.min(Math.abs(ddy), ay);
                } else if (!homing) {
                    hx += vx;
                    hy += vy;
                }
                int tx = Fixed.px(hx) + tb.x();
                int ty = Fixed.px(hy) + tb.y();
                if (k >= sabre.windupTicks()) {
                    int[] r = blade(d, cx, cy, sabre);
                    if (r[0] < tx + tb.w() - 1 && tx + 1 < r[0] + r[2] && r[1] < ty + tb.h() - 1 && ty + 1 < r[1] + r[3]) {
                        return d;
                    }
                }
                int bx = Fixed.px(pxFp) + pb.x();
                int by = Fixed.px(pyFp) + pb.y();
                if (bx < tx + tb.w() + 1 && tx - 1 < bx + pb.w() && by < ty + tb.h() + 1 && ty - 1 < by + pb.h()) {
                    break;
                }
            }
        }
        return null;
    }

    private static int[] blade(Direction8 d, int cx, int cy, PlayerData.Sabre s) {
        if (d.dx() != 0) {
            int x = d.dx() > 0 ? cx : cx - s.reachPx();
            int y = cy - s.thicknessPx() / 2 + (d.diagonal() ? s.diagonalOffsetPx() * d.dy() : 0);
            return new int[] {x, y, s.reachPx(), s.thicknessPx()};
        }
        int y = d.dy() > 0 ? cy : cy - s.reachPx();
        return new int[] {cx - s.thicknessPx() / 2, y, s.thicknessPx(), s.reachPx()};
    }

    /** Whether stepping this way would bring the player within 2 px of anything lethal, the Keeper included. */
    private static boolean touches(Simulation sim, int dx, int dy) {
        PlayerData rules = sim.rules();
        PlayerData.Box pb = rules.collisionBox();
        int bx = Fixed.px(sim.player().xFp() + dx * rules.speed().xFp()) + pb.x();
        int by = Fixed.px(sim.player().yFp() + dy * rules.speed().yFp()) + pb.y();
        List<Creature> near = threats(sim);
        if (sim.quest().keeperHere() && sim.quest().keeperState() != Quest.Keeper.ASIDE) {
            near.add(sim.quest().keeper());
        }
        for (Creature cr : near) {
            CreatureData.Box b = cr.species().collisionBox();
            int cx = Fixed.px(cr.xFp()) + b.x() - 2;
            int cy = Fixed.px(cr.yFp()) + b.y() - 2;
            if (bx < cx + b.w() + 4 && cx < bx + pb.w() && by < cy + b.h() + 4 && cy < by + pb.h()) {
                return true;
            }
        }
        return false;
    }

    /** Breadth-first over 2x2-cell blocks from the player to one block — CornerWalkTest's planner, aimed at a spot. */
    private static List<int[]> route(WorldGrid grid, PlayerData.Box box, Simulation sim, int[] target, LandmarksData marks,
                                     int fetching) {
        int[][] grounds = new int[marks.lairs().size()][];
        for (int l = 0; l < grounds.length; l++) {
            RoomAddress room = LandmarksData.room(marks.lairs().get(l).room());
            LandmarksData.Point p = marks.lairs().get(l).pedestal();
            grounds[l] = new int[] {room.col() * 256 + p.x(), room.row() * 192 + p.y()};
        }
        int cols = WorldGrid.COLS;
        int startX = Math.floorDiv(Fixed.px(sim.player().xFp()) + box.x(), Simulation.CELL_PX);
        int startY = Math.floorDiv(Fixed.px(sim.player().yFp()) + box.y(), Simulation.CELL_PX);
        int[] parent = new int[cols * WorldGrid.ROWS];
        Arrays.fill(parent, -2);
        int[] queue = new int[parent.length];
        int head = 0;
        int tail = 0;
        int goalIndex = target[1] * cols + target[0];
        // Every clear block round the feet: a player straddling a wall's edge is in no single one.
        for (int y = startY - 1; y <= startY + 1; y++) {
            for (int x = startX - 1; x <= startX + 1; x++) {
                if (x >= 0 && y >= 0 && x < cols && y < WorldGrid.ROWS && grid.blockPassable(x, y) && parent[y * cols + x] == -2) {
                    parent[y * cols + x] = -1;
                    queue[tail++] = y * cols + x;
                }
            }
        }
        boolean found = false;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (head < tail) {
            int i = queue[head++];
            if (i == goalIndex) {
                found = true;
                break;
            }
            int x = i % cols;
            int y = i / cols;
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
                int feetX = nx * 8 + 8;
                int feetY = ny * 8 + 13;
                boolean guarded = false;
                for (int l = 0; l < grounds.length && !guarded; l++) {
                    int next = Math.max(Math.abs(feetX - grounds[l][0]), Math.abs(feetY - grounds[l][1]));
                    int here = Math.max(Math.abs(x * 8 + 8 - grounds[l][0]), Math.abs(y * 8 + 13 - grounds[l][1]));
                    // Inside a guardian's ground only ever step outward: out of a lair, never through one.
                    guarded = l != fetching && next <= GUARDIAN_GROUND_PX && next <= here;
                }
                if (guarded) {
                    continue;
                }
                parent[j] = i;
                queue[tail++] = j;
            }
        }
        LinkedList<int[]> path = new LinkedList<>();
        if (!found) {
            return path;
        }
        for (int i = goalIndex; i >= 0; i = parent[i]) {
            path.addFirst(new int[] {i % cols, i / cols});
        }
        return path;
    }
}
