package wulf.sim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import wulf.data.CreatureData;
import wulf.data.LandmarksData;
import wulf.data.PlayerData;
import wulf.data.WulfData;
import wulf.engine.Fixed;
import wulf.engine.Rng;
import wulf.input.InputState;
import wulf.sim.ai.Behaviour;
import wulf.sim.ai.BehaviourCatalog;
import wulf.sim.ai.SimContext;
import wulf.world.CollisionMask;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;
import wulf.data.OrchidData;
import wulf.sim.effects.EffectState;

/**
 * The game's single source of truth, advanced one fixed tick at a time
 * (AGENTS.md §6). Pure and deterministic: integer arithmetic only, no wall
 * clock, no AWT, and one seeded random stream. Given the same data, run seed and
 * input it produces the same {@link #stateHash()} on every tick, everywhere.
 *
 * <p>M3: Ranger Vale — movement, collision, flip-screen rooms, the sabre, death
 * and respawn. M4: the creatures — room population, behaviours, spears, sabre
 * kills, contact deaths, score and extra lives. M5: the Wulf — appearance, warning,
 * pursuit across rooms, parry, and giving up. M6: the quest — guardians, the amulet,
 * the Keeper of the Arch, the shrines' hints, and the escape. M7: the orchids —
 * their growth, and what a bloom does to you.
 */
public final class Simulation {

    public static final int CELL_PX = 8;
    public static final int ROOM_W_PX = 256;
    public static final int ROOM_H_PX = 192;
    /** How close to reachable feet a ground creature must start (§12.6): one cell. */
    static final int REACH_SLACK_PX = 8;
    /** Counters stop here instead of wrapping: "a very long time ago" is all they need to say. */
    private static final int SATURATED = 1 << 30;

    /** What the border shows this tick (§5.4, §11.7, §13.3, §13.5). */
    public enum BorderFlash { NONE, ALARM, PARRY, PICKUP }

    /** Ledger names for the two deaths no species owns (§21.5). */
    private static final String SPEAR_CAUSE = "spear";
    private static final String DEV_CAUSE = "dev";

    /** The names in {@code sfx.json} the simulation announces (§18.2). */
    private static final String SFX_FOOTSTEP = "footstep";
    private static final String SFX_SABRE_SWING = "sabre_swing";
    private static final String SFX_CREATURE_DIE = "creature_die";
    private static final String SFX_SPEAR_THROW = "spear_throw";
    private static final String SFX_ORCHID_PICK = "orchid_pick";
    private static final String SFX_AMULET_PIECE = "amulet_piece";
    private static final String SFX_WULF_HOWL = "wulf_howl";
    private static final String SFX_WULF_GROWL = "wulf_growl";
    private static final String SFX_WULF_PARRY = "wulf_parry";
    private static final String SFX_PLAYER_DIE = "player_die";
    private static final String SFX_EXTRA_LIFE = "extra_life";
    private static final String SFX_ROOM_FLIP = "room_flip";
    private static final String SFX_KEEPER_MOVES = "keeper_moves";

    private final PlayerData rules;
    private final CollisionWorld world;
    private final int transitionFreezeTicks;
    private final Ecosystem eco;
    private final long runSeed;
    private final Rng rng;
    private final Behaviour[] behaviours;
    private final Player player = new Player();
    private final ArrayList<Creature> creatures = new ArrayList<>();
    private final ArrayList<Spear> spears = new ArrayList<>();
    private final List<Creature> creaturesView = Collections.unmodifiableList(creatures);
    private final List<Spear> spearsView = Collections.unmodifiableList(spears);
    private final int[] visits = new int[RoomAddress.GRID_W * RoomAddress.GRID_H];
    private final Context context = new Context();
    private final Spawns spawns = new Spawns();
    private final Wulf wulf;
    private final Behaviour chase = BehaviourCatalog.of("CHASE_DIRECT");
    private final Quest quest;
    private final Behaviour orbit = BehaviourCatalog.of("GUARD_ORBIT");
    private final OrchidField orchids;
    private final EffectState effect = new EffectState();
    /** When each anchor's cycle began, by room and anchor; {@link Long#MIN_VALUE} until first seen. */
    private final long[] orchidCycleStart;
    private int[] roomOrchids = new int[0];
    private int orchidBase;
    private long orchidStagesRead;

    private RoomAddress room;
    private CollisionWorld roomSolid;
    private CollisionWorld roomEdges;
    private Reach reach;
    private int freeze;
    private long tick;
    private int roomsEntered;
    private PixelRect sabre = PixelRect.NONE;
    private int swingSerial;
    private int nextEntityId;
    private long score;
    private int kills;
    /**
     * Bookkeeping for the lifetime ledger (§21.5), not game state: what died on the
     * blade, what killed Vale, and which flowers were picked. Never mixed into
     * {@link #stateHash()} — counting is not simulating, and a replay must not care.
     */
    private final Map<String, Integer> killsBySpecies = new LinkedHashMap<>();
    private final Map<String, Integer> deathsByCause = new LinkedHashMap<>();
    private final Map<String, Integer> orchidsByColour = new LinkedHashMap<>();
    private int deaths;
    /**
     * Where noises are announced (§18.1). Silent unless the game hands one in, so
     * every test and every replay runs without sound and is unaffected by it.
     */
    private SoundSink sounds = SoundSink.NONE;
    private int footstepTicks;
    private int footstepEveryTicks = Integer.MAX_VALUE;
    private int extraLivesAwarded;

    private Simulation(PlayerData rules, CollisionWorld world, int transitionFreezeTicks, Ecosystem eco, long runSeed,
                       int xFp, int yFp) {
        this.rules = rules;
        this.world = world;
        this.transitionFreezeTicks = transitionFreezeTicks;
        this.eco = eco;
        this.runSeed = runSeed;
        this.rng = new Rng(runSeed);
        List<CreatureData.Species> roster = eco.creatures().creatures();
        this.behaviours = new Behaviour[roster.size()];
        for (int i = 0; i < roster.size(); i++) {
            behaviours[i] = BehaviourCatalog.of(roster.get(i).behaviour().kind());
        }
        player.xFp = xFp;
        player.yFp = yFp;
        player.entryXFp = xFp;
        player.entryYFp = yFp;
        player.lives = rules.lives().start();
        WulfData.Appearance appearance = eco.wulf().data().appearance();
        this.wulf = new Wulf(new Creature(Wulf.ID, eco.wulf().species(), -1, xFp, yFp, Herd.NONE));
        wulf.ticksSinceGone = appearance.minTicksBetweenAppearances();
        wulf.ticksSinceRespawn = appearance.graceTicksAfterPlayerDeath();
        Creature keeper = new Creature(Quest.KEEPER_ID, eco.quest().keeperSpecies(), -1, xFp, yFp, Herd.NONE);
        this.quest = new Quest(keeper, keeper);   // the guardian is seated on entering a lair
        this.orchids = new OrchidField(eco.orchids());
        this.orchidCycleStart = new long[RoomAddress.GRID_W * RoomAddress.GRID_H
                * Math.max(1, eco.orchids().anchors().perRoom())];
        java.util.Arrays.fill(orchidCycleStart, Long.MIN_VALUE);
        this.room = new RoomAddress(roomColOf(rules.collisionBox(), xFp), roomRowOf(rules.collisionBox(), yFp));
        enterRoom(false);
    }

    /** A new game without creatures: the player on the free spot nearest the centre of {@code start}. */
    public static Simulation startingIn(PlayerData rules, CollisionWorld world, int transitionFreezeTicks,
                                        RoomAddress start) {
        return startingIn(rules, world, transitionFreezeTicks, start, Ecosystem.NONE, 0L);
    }

    /** A new game in a living jungle. */
    public static Simulation startingIn(PlayerData rules, CollisionWorld world, int transitionFreezeTicks,
                                        RoomAddress start, Ecosystem eco, long runSeed) {
        int cx = Fixed.fp(start.col() * ROOM_W_PX + ROOM_W_PX / 2);
        int cy = Fixed.fp(start.row() * ROOM_H_PX + ROOM_H_PX / 2);
        int[] p = SpawnFinder.nearestFree(world, rules.collisionBox(), rules.spawn().insideRoomPx(), start, cx, cy);
        return new Simulation(rules, world, transitionFreezeTicks, eco, runSeed, p[0], p[1]);
    }

    /** A simulation with the player at an exact world position and no creatures — for tests and tools. */
    public static Simulation at(PlayerData rules, CollisionWorld world, int transitionFreezeTicks, int xFp, int yFp) {
        return at(rules, world, transitionFreezeTicks, xFp, yFp, Ecosystem.NONE, 0L);
    }

    public static Simulation at(PlayerData rules, CollisionWorld world, int transitionFreezeTicks, int xFp, int yFp,
                                Ecosystem eco, long runSeed) {
        return new Simulation(rules, world, transitionFreezeTicks, eco, runSeed, xFp, yFp);
    }

    public void tick(InputState in) {
        tick++;
        switch (player.mode) {
            case GAME_OVER -> {
                return;
            }
            case DYING -> {
                tickCreatures();   // §11.7: the jungle carries on around the fallen player
                if (++player.modeTick >= rules.death().animTicks()) {
                    player.mode = Player.Mode.DOWN;
                    player.modeTick = 0;
                }
                return;
            }
            case DOWN -> {
                tickCreatures();
                if (++player.modeTick >= rules.death().freezeTicks()) {
                    respawn();
                }
                return;
            }
            case ESCAPING -> {
                stepEscape();
                return;
            }
            case WON -> {
                return;
            }
            case ALIVE -> {
                // fall through to play
            }
        }
        if (freeze > 0) {
            // §7.5: the flip-screen hitch. Nothing moves; the new room is already current.
            freeze--;
            return;
        }
        if (player.invulnTicks > 0) {
            player.invulnTicks--;
        }
        updateSwing(in);
        move(in);
        if (updateRoom()) {
            sabre = PixelRect.NONE;
            return;   // the new room's creatures start moving after the hitch
        }
        sabre = computeSabre();
        tickCreatures();
        tickWulf();
        tickQuest();
        tickOrchids();
        resolveCombat();
    }

    /**
     * One tick as the game drives it: in dev mode the kill and summon keys act first,
     * then the tick. Replays go through here too, so a recording made with dev keys
     * plays back identically.
     */
    public void play(InputState in, boolean dev) {
        if (dev && in.devKillPressed()) {
            kill();
        }
        if (dev && in.devWulfPressed()) {
            summonWulf();
        }
        tick(in);
    }

    /**
     * Lethal contact (§11.7). Ignored while invulnerable or already down.
     *
     * @return whether the hit landed
     */
    public boolean kill() {
        return kill(DEV_CAUSE);
    }

    /** The same death, with what did it, for the ledger (§21.5). */
    public boolean kill(String cause) {
        if (player.mode != Player.Mode.ALIVE || player.invulnTicks > 0) {
            return false;
        }
        deaths++;
        deathsByCause.merge(cause, 1, Integer::sum);
        sounds.play(SFX_PLAYER_DIE);
        sounds.loop(SFX_WULF_GROWL, false);
        effect.clear();   // §15.2: whatever was in the blood dies with you
        player.lives--;
        player.mode = Player.Mode.DYING;
        player.modeTick = 0;
        player.swingTick = -1;
        player.cooldown = 0;
        player.walkTicks = 0;
        freeze = 0;
        sabre = PixelRect.NONE;
        return true;
    }

    // ---------------------------------------------------------------- the sabre

    private void updateSwing(InputState in) {
        PlayerData.Sabre s = rules.sabre();
        if (player.swingTick >= 0) {
            if (++player.swingTick >= s.totalTicks()) {
                player.swingTick = -1;
                // §11.6: held, the sabre goes again straight away; let go and it needs its cooldown,
                // so tapping is never quicker than keeping the blade working.
                player.cooldown = in.fire() ? s.holdRepeatTicks() : s.cooldownTicks();
            }
        } else if (player.cooldown > 0) {
            player.cooldown--;
        }
        // Held or tapped: the level, not the edge (§11.6).
        if (player.swingTick < 0 && player.cooldown == 0 && in.fire()) {
            player.swingTick = 0;
            swingSerial++;   // each swing may hit each creature once
            sounds.play(SFX_SABRE_SWING);
        }
    }

    private PixelRect computeSabre() {
        PlayerData.Sabre s = rules.sabre();
        int t = player.swingTick;
        if (t < s.windupTicks() || t >= s.windupTicks() + s.activeTicks()) {
            return PixelRect.NONE;
        }
        PlayerData.Box b = rules.collisionBox();
        int cx = Fixed.px(player.xFp) + b.x() + b.w() / 2;
        int cy = Fixed.px(player.yFp) + b.y() + b.h() / 2;
        Direction8 d = player.facing;
        if (d.dx() != 0) {
            // Diagonals project along the horizontal and shift vertically: no rotated rectangles (§11.6).
            int x = d.dx() > 0 ? cx : cx - s.reachPx();
            int y = cy - s.thicknessPx() / 2 + (d.diagonal() ? s.diagonalOffsetPx() * d.dy() : 0);
            return new PixelRect(x, y, s.reachPx(), s.thicknessPx());
        }
        int y = d.dy() > 0 ? cy : cy - s.reachPx();
        return new PixelRect(cx - s.thicknessPx() / 2, y, s.thicknessPx(), s.reachPx());
    }

    // ---------------------------------------------------------------- the player's movement

    private void move(InputState in) {
        // §15.2: the flower has the legs, not the keyboard — steering is changed here, inside the
        // simulation, so a recorded replay still holds the player's own honest input.
        int[] steered = effect.steer(in.dx(), in.dy(), eco.orchids().delirium(), rng);
        int dx = steered[0];
        int dy = steered[1];
        if (dx != 0 || dy != 0) {
            player.facing = Direction8.of(dx, dy);   // facing persists when the keys are released (§11.4)
        }
        PlayerData.Speed speed = rules.speed();
        int speedX = effect.speedFp(speed.xFp());
        int speedY = effect.speedFp(speed.yFp());
        if (player.swingTick >= 0 && rules.sabre().moveSpeedScaleFp() != Fixed.ONE) {
            speedX = Fixed.mul(speedX, rules.sabre().moveSpeedScaleFp());
            speedY = Fixed.mul(speedY, rules.sabre().moveSpeedScaleFp());
        }
        PlayerData.Box box = rules.collisionBox();
        if (dx != 0 && dy != 0) {
            boolean xTouching = Collision.boxBlocked(world, box, player.xFp + dx * Fixed.ONE, player.yFp);
            boolean yTouching = Collision.boxBlocked(world, box, player.xFp, player.yFp + dy * Fixed.ONE);
            if (!xTouching && !yTouching) {
                // Scale the magnitude, then apply the sign, so left and right are exact mirrors.
                speedX = Fixed.mul(speedX, speed.diagonalScaleFp());
                speedY = Fixed.mul(speedY, speed.diagonalScaleFp());
            }
            // Pressed against a wall on one axis: slide along the other at its full rate (§7.4, §22.2).
        }
        int oldX = player.xFp;
        int oldY = player.yFp;
        // No acceleration and no inertia: velocity is a pure function of this tick's input (§11.3).
        // X then Y, each resolved on its own: that ordering is what produces wall-sliding (§7.4).
        player.xFp = Collision.resolve(world, box.x(), box.y(), box.w(), box.h(), player.xFp, player.yFp,
                dx * speedX, true);
        player.yFp = Collision.resolve(world, box.x(), box.y(), box.w(), box.h(), player.xFp, player.yFp,
                dy * speedY, false);
        boolean moved = player.xFp != oldX || player.yFp != oldY;
        player.walkTicks = moved ? player.walkTicks + 1 : 0;
        // A step every few ticks while the feet are actually going somewhere (§18.2).
        if (!moved) {
            footstepTicks = 0;
        } else if (++footstepTicks >= footstepEveryTicks) {
            footstepTicks = 0;
            sounds.play(SFX_FOOTSTEP);
        }
    }

    // ---------------------------------------------------------------- rooms

    /** @return whether the player just entered a different room */
    private boolean updateRoom() {
        int col = roomColOf(rules.collisionBox(), player.xFp);
        int row = roomRowOf(rules.collisionBox(), player.yFp);
        if (col == room.col() && row == room.row()) {
            return false;
        }
        // §7.5: the feet-box centre crossed an edge. Position is untouched — world
        // coordinates carry the cross-edge offset across exactly.
        RoomAddress from = room;
        room = new RoomAddress(col, row);
        freeze = transitionFreezeTicks;
        roomsEntered++;
        sounds.play(SFX_ROOM_FLIP);
        player.entryXFp = player.xFp;
        player.entryYFp = player.yFp;
        enterRoom(true);
        wulfEntersRoom(from);
        return true;
    }

    private void enterRoom(boolean byTransition) {
        if (byTransition && visits[room.index()] == 0) {
            addScore(eco.roomFirstVisitScore());
        }
        int x0 = room.col() * CollisionMask.COLS;
        int y0 = room.row() * CollisionMask.ROWS;
        int x1 = x0 + CollisionMask.COLS;
        int y1 = y0 + CollisionMask.ROWS;
        CollisionWorld scenery = world;
        // Creatures never leave their room (§12.2): its edges are walls to them.
        roomEdges = (gx, gy) -> gx < x0 || gy < y0 || gx >= x1 || gy >= y1;
        roomSolid = (gx, gy) -> gx < x0 || gy < y0 || gx >= x1 || gy >= y1 || scenery.isSolid(gx, gy);
        repopulate();
        questEntersRoom();
        enterOrchidRoom();
    }

    /** The room's creatures, rolled afresh: on every entry and after every respawn (§7.6, §11.7). */
    private void repopulate() {
        creatures.clear();
        spears.clear();
        reach = null;
        int visit = visits[room.index()]++;
        eco.populator().populate(spawns, room, runSeed, visit);
    }

    static int roomColOf(PlayerData.Box box, int xFp) {
        int centre = Fixed.px(xFp) + box.x() + box.w() / 2;
        return Math.max(0, Math.min(RoomAddress.GRID_W - 1, Math.floorDiv(centre, ROOM_W_PX)));
    }

    static int roomRowOf(PlayerData.Box box, int yFp) {
        int centre = Fixed.px(yFp) + box.y() + box.h() / 2;
        return Math.max(0, Math.min(RoomAddress.GRID_H - 1, Math.floorDiv(centre, ROOM_H_PX)));
    }

    private void respawn() {
        if (player.lives <= 0) {
            player.mode = Player.Mode.GAME_OVER;
            player.modeTick = 0;
            return;
        }
        // Same room, at the free spot nearest where the player came in, wholly inside the room (§11.7).
        int[] p = SpawnFinder.nearestFree(world, rules.collisionBox(), rules.spawn().insideRoomPx(), room,
                player.entryXFp, player.entryYFp);
        player.xFp = p[0];
        player.yFp = p[1];
        player.mode = Player.Mode.ALIVE;
        player.modeTick = 0;
        player.invulnTicks = rules.spawn().invulnTicks();
        repopulate();
        if (wulf.state != Wulf.State.ABSENT) {
            gone();   // it caught you; it does not wait at the respawn
        }
        wulf.ticksSinceRespawn = 0;
        wulf.ticksInRoom = 0;
        questEntersRoom();   // the guardian back on its ring
        if (eco.quest().landmarks().hint().oncePerLife()) {
            java.util.Arrays.fill(quest.hintUsed, false);
        }
        quest.hintTicks = 0;
    }

    // ---------------------------------------------------------------- creatures and spears

    private void tickCreatures() {
        for (int i = 0; i < creatures.size(); i++) {
            Creature c = creatures.get(i);
            if (c.mode == Creature.Mode.DYING) {
                c.modeTick++;
                continue;
            }
            if (c.hurtTicks > 0) {
                c.hurtTicks--;
            }
            if (effect.kind().freezesCreatures()) {
                c.moveTicks = 0;   // §15.2: the blue flower stops the room dead
                continue;
            }
            int ox = c.xFp;
            int oy = c.yFp;
            behaviours[c.speciesIndex()].tick(c, context);
            c.moveTicks = c.xFp != ox || c.yFp != oy ? c.moveTicks + 1 : 0;
        }
        if (!spears.isEmpty()) {
            CreatureData.Projectile def = eco.creatures().projectile("spear");
            CreatureData.Box b = def.collisionBox();
            int speed = def.speedFp();
            int diagonalSpeed = Fixed.mul(speed, eco.creatures().diagonalScaleFp());
            for (int i = 0; i < spears.size(); i++) {
                Spear s = spears.get(i);
                int v = s.dirX() != 0 && s.dirY() != 0 ? diagonalSpeed : speed;
                int nx = Collision.resolve(roomSolid, b.x(), b.y(), b.w(), b.h(), s.xFp, s.yFp, s.dirX() * v, true);
                boolean hit = nx != s.xFp + s.dirX() * v;
                s.xFp = nx;
                int ny = Collision.resolve(roomSolid, b.x(), b.y(), b.w(), b.h(), s.xFp, s.yFp, s.dirY() * v, false);
                hit |= ny != s.yFp + s.dirY() * v;
                s.yFp = ny;
                if (hit || ++s.ticks >= def.maxTicks()) {
                    s.alive = false;   // spears die on scenery and on the room's edge
                }
            }
        }
        removeFinished();
    }

    private void removeFinished() {
        int puff = eco.creatures().puffTicks();
        int keep = 0;
        for (int i = 0; i < creatures.size(); i++) {
            Creature c = creatures.get(i);
            if (!(c.mode == Creature.Mode.DYING && c.modeTick >= puff)) {
                creatures.set(keep++, c);
            }
        }
        while (creatures.size() > keep) {
            creatures.remove(creatures.size() - 1);
        }
        keep = 0;
        for (int i = 0; i < spears.size(); i++) {
            Spear s = spears.get(i);
            if (s.alive) {
                spears.set(keep++, s);
            }
        }
        while (spears.size() > keep) {
            spears.remove(spears.size() - 1);
        }
    }

    private void resolveCombat() {
        CreatureData roster = eco.creatures();
        if (!sabre.isEmpty()) {
            for (int i = 0; i < creatures.size(); i++) {
                Creature c = creatures.get(i);
                if (c.mode != Creature.Mode.ALIVE || c.lastHitSwing == swingSerial) {
                    continue;
                }
                CreatureData.Box b = c.species().collisionBox();
                if (overlaps(sabre, Fixed.px(c.xFp) + b.x(), Fixed.px(c.yFp) + b.y(), b.w(), b.h())) {
                    c.lastHitSwing = swingSerial;
                    if (--c.hp <= 0) {
                        c.mode = Creature.Mode.DYING;   // a harmless puff (§12.2)
                        c.modeTick = 0;
                        kills++;
                        killsBySpecies.merge(c.species().id(), 1, Integer::sum);
                        sounds.play(SFX_CREATURE_DIE);
                        addScore(c.species().score());
                    } else {
                        c.hurtTicks = roster.hurtFlashTicks();
                    }
                }
            }
            if (!spears.isEmpty()) {
                CreatureData.Projectile def = roster.projectile("spear");
                CreatureData.Box b = def.collisionBox();
                for (int i = 0; i < spears.size(); i++) {
                    Spear s = spears.get(i);
                    if (s.alive && overlaps(sabre, Fixed.px(s.xFp) + b.x(), Fixed.px(s.yFp) + b.y(), b.w(), b.h())) {
                        s.alive = false;
                        addScore(def.score());
                    }
                }
            }
            parryWulf();
            parryGuardian();
        }
        if (player.mode != Player.Mode.ALIVE || player.invulnTicks > 0) {
            return;
        }
        // One touch, one life (§12.2).
        PlayerData.Box pb = rules.collisionBox();
        int px = Fixed.px(player.xFp) + pb.x();
        int py = Fixed.px(player.yFp) + pb.y();
        // §15.2: the green flower stops contact killing you — the Keeper still bars the arch.
        boolean immune = effect.kind().blocksLethalContact();
        if (quest.inLair() && !immune) {
            Creature g = quest.guardian;
            CreatureData.Box gb = g.species().collisionBox();
            if (overlaps(px, py, pb.w(), pb.h(), Fixed.px(g.xFp) + gb.x(), Fixed.px(g.yFp) + gb.y(), gb.w(), gb.h())) {
                kill(g.species().id());   // unkillable, and lethal to touch (§14.3)
                return;
            }
        }
        if (quest.keeperHere && quest.keeperState != Quest.Keeper.ASIDE) {
            Creature k = quest.keeper;
            CreatureData.Box kb = k.species().collisionBox();
            if (overlaps(px, py, pb.w(), pb.h(), Fixed.px(k.xFp) + kb.x(), Fixed.px(k.yFp) + kb.y(), kb.w(), kb.h())) {
                // §14.4 grace: brushing it from the front, in the nudge zone, pushes you back
                // instead of killing you — so a first-minute stumble into the way out is not a death.
                int ahead = Fixed.px(player.yFp) - Fixed.px(k.yFp);
                if (ahead > 0 && ahead <= eco.quest().nudgeZonePx()) {
                    player.yFp = Collision.resolve(world, pb.x(), pb.y(), pb.w(), pb.h(), player.xFp, player.yFp,
                            Fixed.fp(eco.quest().nudgePx()), false);
                } else {
                    kill(k.species().id());
                    return;
                }
            }
        }
        if (wulf.touchable() && !immune) {
            CreatureData.Box wb = wulf.body.species().collisionBox();
            if (overlaps(px, py, pb.w(), pb.h(), Fixed.px(wulf.body.xFp) + wb.x(), Fixed.px(wulf.body.yFp) + wb.y(),
                    wb.w(), wb.h())) {
                kill(wulf.body.species().id());
                return;
            }
        }
        for (int i = 0; i < creatures.size() && !immune; i++) {
            Creature c = creatures.get(i);
            CreatureData.Box b = c.species().collisionBox();
            if (c.mode == Creature.Mode.ALIVE
                    && overlaps(px, py, pb.w(), pb.h(), Fixed.px(c.xFp) + b.x(), Fixed.px(c.yFp) + b.y(), b.w(), b.h())) {
                kill(c.species().id());
                return;
            }
        }
        if (!spears.isEmpty()) {
            CreatureData.Box b = roster.projectile("spear").collisionBox();
            for (int i = 0; i < spears.size(); i++) {
                Spear s = spears.get(i);
                if (s.alive && overlaps(px, py, pb.w(), pb.h(), Fixed.px(s.xFp) + b.x(), Fixed.px(s.yFp) + b.y(), b.w(), b.h())) {
                    s.alive = false;
                    if (!immune) {
                        kill(SPEAR_CAUSE);
                        return;
                    }
                }
            }
        }
    }

    private static boolean overlaps(PixelRect r, int x, int y, int w, int h) {
        return overlaps(r.x(), r.y(), r.w(), r.h(), x, y, w, h);
    }

    private static boolean overlaps(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && bx < ax + aw && ay < by + bh && by < ay + ah;
    }

    // ---------------------------------------------------------------- the orchids (§15)

    /** The room's anchors, found once on entry; guardian rooms and the way out never flower (§14.3). */
    private void enterOrchidRoom() {
        OrchidData data = eco.orchids();
        if (data.orchids().isEmpty()) {
            roomOrchids = new int[0];
            return;
        }
        QuestRules q = eco.quest();
        boolean sacred = q.enabled()
                && (q.landmarks().lairIndexIn(room) >= 0 || room.equals(q.landmarks().exitRoom()));
        roomOrchids = sacred ? new int[0] : orchids.anchors(room, world, runSeed);
        orchidBase = room.index() * data.anchors().perRoom();
        for (int i = 0; i < roomOrchids.length / 2; i++) {
            if (orchidCycleStart[orchidBase + i] == Long.MIN_VALUE) {
                // Staggered, and counted from before the game began, so a room is mid-cycle when first seen.
                orchidCycleStart[orchidBase + i] = orchids.firstCycleStart(runSeed, room, i);
            }
        }
    }

    private void tickOrchids() {
        // Age first, take second: a bloom picked this tick keeps every tick it promised.
        effect.tick();
        if (roomOrchids.length == 0) {
            return;
        }
        OrchidData data = eco.orchids();
        PlayerData.Box pb = rules.collisionBox();
        int px = Fixed.px(player.xFp) + pb.x();
        int py = Fixed.px(player.yFp) + pb.y();
        int roomX = room.col() * ROOM_W_PX;
        int roomY = room.row() * ROOM_H_PX;
        for (int i = 0; i < roomOrchids.length / 2; i++) {
            orchidStagesRead++;
            long start = orchidCycleStart[orchidBase + i];
            if (orchids.stageAt(orchids.phase(tick, start)) != OrchidField.Stage.BLOOM) {
                continue;
            }
            int bx = roomX + roomOrchids[2 * i] - 8;
            int by = roomY + roomOrchids[2 * i + 1] - 16;
            if (!overlaps(px, py, pb.w(), pb.h(), bx, by, 16, 16)) {
                continue;
            }
            int which = orchids.orchidAt(runSeed, room, i, start);
            effect.take(which, data.orchid(which));
            addScore(data.orchid(which).score());
            orchidsByColour.merge(data.orchid(which).colour(), 1, Integer::sum);
            sounds.play(SFX_ORCHID_PICK);
            orchidCycleStart[orchidBase + i] = tick;   // picked: straight back to seed (§15.1)
        }
    }

    // ---------------------------------------------------------------- the quest (§14)

    /** Seats the lair's guardian or the Keeper as the player arrives, and after a respawn. */
    private void questEntersRoom() {
        QuestRules rules = eco.quest();
        if (!rules.enabled()) {
            return;
        }
        LandmarksData marks = rules.landmarks();
        Quest q = quest;
        q.lair = marks.lairIndexIn(room);
        if (q.lair >= 0) {
            LandmarksData.Point pedestal = marks.lairs().get(q.lair).pedestal();
            int x = Fixed.fp(room.col() * ROOM_W_PX + pedestal.x());
            int y = Fixed.fp(room.row() * ROOM_H_PX + pedestal.y());
            // Allocated per lair entry, not per tick: each lair's beast is a different species.
            q.guardian = new Creature(Quest.GUARDIAN_ID, rules.guardianSpecies().get(q.lair), -1, x, y, Herd.NONE);
            orbit.spawn(q.guardian, context, rng);
        }
        q.keeperHere = room.equals(marks.exitRoom());
        if (q.keeperHere) {
            seatKeeper();
        }
    }

    private void seatKeeper() {
        QuestRules rules = eco.quest();
        LandmarksData.Point at = rules.landmarks().exit().keeper();
        int aside = switch (quest.keeperState) {
            case BLOCKING -> 0;
            case STEPPING_ASIDE -> rules.stepAsidePx() * quest.keeperTick / rules.stepAsideTicks();
            case ASIDE -> rules.stepAsidePx();
        };
        LandmarksData.Point p = at;
        quest.keeper.place(Fixed.fp(room.col() * ROOM_W_PX + p.x() + aside), Fixed.fp(room.row() * ROOM_H_PX + p.y()));
    }

    private void tickQuest() {
        QuestRules rules = eco.quest();
        if (!rules.enabled()) {
            return;
        }
        LandmarksData marks = rules.landmarks();
        Quest q = quest;
        if (q.pickupFlash > 0) {
            q.pickupFlash--;
        }
        if (q.flyTicks > 0) {
            q.flyTicks--;
        }
        if (q.hintTicks > 0) {
            q.hintTicks--;
        }
        if (q.lair >= 0) {
            Creature g = q.guardian;
            if (g.hurtTicks > 0) {
                g.hurtTicks--;
            }
            int ox = g.xFp;
            int oy = g.yFp;
            orbit.tick(g, context);
            g.moveTicks = g.xFp != ox || g.yFp != oy ? g.moveTicks + 1 : 0;
        }
        if (q.keeperState == Quest.Keeper.STEPPING_ASIDE) {
            if (++q.keeperTick >= rules.stepAsideTicks()) {
                q.keeperState = Quest.Keeper.ASIDE;
                sounds.play(SFX_KEEPER_MOVES);
            }
            if (q.keeperHere) {
                seatKeeper();
            }
        }
        PlayerData.Box pb = this.rules.collisionBox();
        int px = Fixed.px(player.xFp) + pb.x();
        int py = Fixed.px(player.yFp) + pb.y();
        int roomX = room.col() * ROOM_W_PX;
        int roomY = room.row() * ROOM_H_PX;

        if (q.lair >= 0 && !q.taken[q.lair]) {
            LandmarksData.Lair lair = marks.lairs().get(q.lair);
            CreatureData.Box ab = marks.amulet().collisionBox();
            int ax = roomX + lair.pedestal().x() + ab.x();
            int ay = roomY + lair.pedestal().y() + ab.y();
            if (overlaps(px, py, pb.w(), pb.h(), ax, ay, ab.w(), ab.h())) {
                takePiece(lair, ax + ab.w() / 2, ay + ab.h() / 2);
            }
        }

        LandmarksData.Rect zone = marks.exit().zone();
        boolean inZone = overlaps(px, py, pb.w(), pb.h(), roomX + zone.x(), roomY + zone.y(), zone.w(), zone.h());
        if (rules.caveHints() && inZone && marks.isCaveMouth(room) && !q.hintUsed[room.index()]) {
            // §14.5: the shrine in front of the arch — the same spot in every arch room — points the way.
            q.hintUsed[room.index()] = true;
            q.hintTicks = marks.hint().messageTicks();
            pointTheWay(marks);
        }
        if (inZone && room.equals(marks.exitRoom()) && q.keeperState == Quest.Keeper.ASIDE
                && q.piecesHeld >= marks.exit().requiresPieces()) {
            player.mode = Player.Mode.ESCAPING;
            player.modeTick = 0;
            player.swingTick = -1;
            player.cooldown = 0;
            sabre = PixelRect.NONE;
            q.escapeFromXFp = player.xFp;
        }
    }

    private void takePiece(LandmarksData.Lair lair, int centreXPx, int centreYPx) {
        QuestRules rules = eco.quest();
        LandmarksData.Amulet amulet = rules.landmarks().amulet();
        Quest q = quest;
        q.taken[q.lair] = true;
        q.piecesHeld++;
        q.slotMask |= 1 << lair.piece().slot();
        q.pickupFlash = amulet.pickupFlashTicks();
        sounds.play(SFX_AMULET_PIECE);
        q.flyTicks = amulet.flyToPanelTicks();
        q.flySlot = lair.piece().slot();
        q.flyFromXPx = centreXPx;
        q.flyFromYPx = centreYPx;
        addScore(rules.pieceScore());
        if (q.piecesHeld >= rules.landmarks().exit().requiresPieces() && q.keeperState == Quest.Keeper.BLOCKING) {
            q.keeperState = Quest.Keeper.STEPPING_ASIDE;   // wherever the player is: it is aside by the time they get there
            q.keeperTick = 0;
        }
    }

    /** The compass way from this room to the nearest quarter still out there — or to the arch once none are. */
    private void pointTheWay(LandmarksData marks) {
        Quest q = quest;
        int bestDx = 0;
        int bestDy = 0;
        int best = Integer.MAX_VALUE;
        for (int i = 0; i < marks.lairs().size(); i++) {
            if (q.taken[i]) {
                continue;
            }
            RoomAddress lair = LandmarksData.room(marks.lairs().get(i).room());
            int dx = lair.col() - room.col();
            int dy = lair.row() - room.row();
            int d = Math.abs(dx) + Math.abs(dy);
            if (d < best) {
                best = d;
                bestDx = dx;
                bestDy = dy;
            }
        }
        q.hintToExit = best == Integer.MAX_VALUE;
        if (q.hintToExit) {
            bestDx = marks.exitRoom().col() - room.col();
            bestDy = marks.exitRoom().row() - room.row();
        }
        q.hintDirection = Direction8.toward(bestDx, bestDy);
    }

    /** §14.7: control locked, Vale walks up into the arch's mouth, then the tally. */
    private void stepEscape() {
        QuestRules rules = eco.quest();
        LandmarksData marks = rules.landmarks();
        player.modeTick++;
        player.facing = Direction8.N;
        player.walkTicks++;
        LandmarksData.Rect zone = marks.exit().zone();
        int targetX = Fixed.fp(room.col() * ROOM_W_PX + zone.x() + zone.w() / 2);
        int stepX = Math.min(Math.abs(targetX - player.xFp), this.rules.speed().xFp());
        player.xFp += Integer.signum(targetX - player.xFp) * stepX;
        player.yFp -= this.rules.speed().yFp();   // no collision: the arch is the way out
        if (player.modeTick >= marks.exit().escapeWalkTicks()) {
            win();
        }
    }

    private void win() {
        QuestRules rules = eco.quest();
        Quest q = quest;
        q.scoreBeforeBonuses = score;
        q.escapeBonus = rules.escapeBonus();
        q.timeBonus = Math.max(0, rules.timeBonusMax() - tick / rules.timeBonusTicksDivisor());
        q.livesBonus = (long) player.lives * rules.lifeRemainingBonus();
        addScore((int) q.escapeBonus);
        addScore((int) q.timeBonus);
        addScore((int) q.livesBonus);
        player.mode = Player.Mode.WON;
        player.modeTick = 0;
    }

    /** A guardian is never hurt: a swing pushes it back and stuns it, once per swing (§12.5, §14.3). */
    private void parryGuardian() {
        Quest q = quest;
        if (q.lair < 0) {
            return;
        }
        Creature g = q.guardian;
        CreatureData.Box b = g.species().collisionBox();
        if (g.lastHitSwing == swingSerial
                || !overlaps(sabre, Fixed.px(g.xFp) + b.x(), Fixed.px(g.yFp) + b.y(), b.w(), b.h())) {
            return;
        }
        g.lastHitSwing = swingSerial;
        wulf.data.GuardianData.Orbit o = eco.quest().orbit();
        Direction8 f = player.facing;
        int push = Fixed.fp(o.repelPx());
        if (f.diagonal()) {
            push = Fixed.mul(push, rules.speed().diagonalScaleFp());
        }
        context.move(g, f.dx() * push, f.dy() * push);
        g.aux(o.stunTicks());
        g.hurtTicks = o.hurtFlashTicks();
    }

    // ---------------------------------------------------------------- the Wulf (§13)

    /**
     * Brings the Wulf in now, warning first, at an edge the player is not closest to —
     * skipping the chance, the cooldown, the grace and the rooms it avoids. The dev key.
     *
     * @return whether it came: not while it is already about, the player is down, or no edge has room
     */
    public boolean summonWulf() {
        if (!eco.wulf().enabled() || wulf.state != Wulf.State.ABSENT || player.mode != Player.Mode.ALIVE) {
            return false;
        }
        return appear();
    }

    private void tickWulf() {
        WulfRules hunt = eco.wulf();
        if (!hunt.enabled()) {
            return;
        }
        WulfData.Pursuit pursuit = hunt.data().pursuit();
        Wulf w = wulf;
        Creature body = w.body;
        if (w.parryFlash > 0) {
            w.parryFlash--;
        }
        if (body.hurtTicks > 0) {
            body.hurtTicks--;
        }
        w.ticksSinceRespawn = saturating(w.ticksSinceRespawn);
        w.ticksInRoom = saturating(w.ticksInRoom);
        switch (w.state) {
            case ABSENT -> {
                w.ticksSinceGone = saturating(w.ticksSinceGone);
                if (w.ticksInRoom % hunt.data().appearance().rollEveryTicks() == 0) {
                    rollForWulf();
                }
            }
            case WARNING -> {
                // §13.3: it stands at the edge for 0.8 s before it moves. Without that beat the
                // Wulf is unfair rather than frightening. Non-negotiable.
                if (++w.stateTick >= hunt.data().appearance().warningTicks()) {
                    w.state = Wulf.State.PURSUE;
                    w.stateTick = 0;
                    w.pursuitTicks = 0;
                }
            }
            case PURSUE -> {
                w.stateTick++;
                if (++w.pursuitTicks >= pursuit.giveUpTicks()) {
                    leave();
                    return;
                }
                int ox = body.xFp;
                int oy = body.yFp;
                chase.tick(body, context);   // a parry's stun is CHASE_DIRECT's stall counter
                body.moveTicks = body.xFp != ox || body.yFp != oy ? body.moveTicks + 1 : 0;
            }
            case ARRIVING -> {
                w.stateTick++;
                if (++w.pursuitTicks >= pursuit.giveUpTicks()) {
                    gone();
                    evaded();
                    return;
                }
                if (w.stateTick >= hunt.arrivalDelayTicks()
                        && seatAtEdge(w.arrivalEdge, w.arrivalAlongPx, playerReach(), pursuit.arrivalClearancePx())) {
                    w.state = Wulf.State.PURSUE;
                    w.stateTick = 0;
                    body.timer(0);
                    body.aux(0);
                    body.moveTicks = 0;
                    faceThePlayer(body);
                }
            }
            case LEAVING -> {
                w.stateTick++;
                stepOut(pursuit);
            }
        }
    }

    /** The appearance roll (§13.3): on entering a room, and every {@code rollEveryTicks} while in one. */
    private void rollForWulf() {
        WulfRules hunt = eco.wulf();
        WulfData.Appearance a = hunt.data().appearance();
        if (wulf.state != Wulf.State.ABSENT || player.mode != Player.Mode.ALIVE
                || wulf.ticksSinceGone < a.minTicksBetweenAppearances()
                || wulf.ticksSinceRespawn < a.graceTicksAfterPlayerDeath()
                || hunt.neverIn().contains(room)) {
            return;
        }
        int pieces = quest.piecesHeld;
        int chance = a.baseChancePer10k() + a.chancePerAmuletPiecePer10k() * pieces
                + a.chancePerQuietRoomPer10k() * Math.min(wulf.quietRooms, a.quietRoomsCap());
        if (rng.chance(chance)) {
            appear();
        }
    }

    /** At an edge the player is not closest to, somewhere the player could reach, warning first. */
    private boolean appear() {
        PlayerData.Box pb = rules.collisionBox();
        int lx = Fixed.px(player.xFp) + pb.x() + pb.w() / 2 - room.col() * ROOM_W_PX;
        int ly = Fixed.px(player.yFp) + pb.y() + pb.h() / 2 - room.row() * ROOM_H_PX;
        Wulf.Edge[] edges = Wulf.Edge.values();
        int[] distance = {lx, ROOM_W_PX - lx, ly, ROOM_H_PX - ly};
        int closest = 0;
        for (int i = 1; i < edges.length; i++) {
            if (distance[i] < distance[closest]) {
                closest = i;
            }
        }
        Wulf.Edge[] others = new Wulf.Edge[edges.length - 1];
        int n = 0;
        for (int i = 0; i < edges.length; i++) {
            if (i != closest) {
                others[n++] = edges[i];
            }
        }
        Reach reachable = playerReach();
        CreatureData.Box b = eco.wulf().species().collisionBox();
        int first = rng.nextInt(others.length);
        for (int k = 0; k < others.length; k++) {
            Wulf.Edge edge = others[(first + k) % others.length];
            int lo = edge.alongY() ? -b.y() : -b.x();
            int hi = edge.alongY() ? ROOM_H_PX - b.y() - b.h() : ROOM_W_PX - b.x() - b.w();
            if (seatAtEdge(edge, lo + rng.nextInt(hi - lo + 1), reachable, eco.wulf().data().appearance().minDistancePx())) {
                Wulf w = wulf;
                w.state = Wulf.State.WARNING;
                w.stateTick = 0;
                w.pursuitTicks = 0;
                w.origin = room;
                w.quietRooms = 0;
                w.followedFlips = 0;
                w.appearances++;
                sounds.play(SFX_WULF_HOWL);         // §13.6: the warning you get
                sounds.loop(SFX_WULF_GROWL, true);  // and the growl that stays until it is gone
                Creature body = w.body;
                body.timer(0);
                body.aux(0);
                body.hurtTicks = 0;
                body.moveTicks = 0;
                body.lastHitSwing = -1;
                faceThePlayer(body);
                return true;
            }
        }
        return false;
    }

    /**
     * Puts the body just inside an edge, as near {@code alongPx} as it fits clear of the
     * scenery, within reach of the player and at least {@code clearancePx} from them,
     * searching outwards in 4 px steps.
     */
    private boolean seatAtEdge(Wulf.Edge edge, int alongPx, Reach reachable, int clearancePx) {
        CreatureData.Box b = eco.wulf().species().collisionBox();
        PlayerData.Box pb = rules.collisionBox();
        int playerX = Fixed.px(player.xFp) + pb.x() + pb.w() / 2 - room.col() * ROOM_W_PX;
        int playerY = Fixed.px(player.yFp) + pb.y() + pb.h() / 2 - room.row() * ROOM_H_PX;
        boolean alongY = edge.alongY();
        int lo = alongY ? -b.y() : -b.x();
        int hi = alongY ? ROOM_H_PX - b.y() - b.h() : ROOM_W_PX - b.x() - b.w();
        int across = switch (edge) {
            case WEST -> -b.x();
            case EAST -> ROOM_W_PX - b.x() - b.w();
            case NORTH -> -b.y();
            case SOUTH -> ROOM_H_PX - b.y() - b.h();
        };
        int start = Math.max(lo, Math.min(hi, alongPx));
        for (int k = 0; k <= hi - lo; k += Reach.STEP_PX) {
            for (int sign = 1; sign >= -1; sign -= 2) {
                int along = start + sign * k;
                if ((k == 0 && sign < 0) || along < lo || along > hi) {
                    continue;
                }
                int x = alongY ? across : along;
                int y = alongY ? along : across;
                int xFp = Fixed.fp(room.col() * ROOM_W_PX + x);
                int yFp = Fixed.fp(room.row() * ROOM_H_PX + y);
                int gap = Math.max(Math.abs(x + b.x() + b.w() / 2 - playerX), Math.abs(y + b.y() + b.h() / 2 - playerY));
                if (gap >= clearancePx && !Collision.blocked(roomSolid, b.x(), b.y(), b.w(), b.h(), xFp, yFp)
                        && reachable.near(x, y, REACH_SLACK_PX)) {
                    wulf.body.xFp = xFp;
                    wulf.body.yFp = yFp;
                    return true;
                }
            }
        }
        return false;
    }

    private Reach playerReach() {
        return Reach.from(roomSolid, rules.collisionBox(), room, player.xFp, player.yFp);
    }

    private void faceThePlayer(Creature body) {
        Direction8 d = Direction8.toward(context.playerCentreXPx() - body.centreXPx(),
                context.playerCentreYPx() - body.centreYPx());
        body.direction(d.dx(), d.dy());
    }

    /** §13.4: it follows through the flip — unless the chase has now gone too far from where it began. */
    private void wulfEntersRoom(RoomAddress from) {
        WulfRules hunt = eco.wulf();
        if (!hunt.enabled()) {
            return;
        }
        Wulf w = wulf;
        w.ticksInRoom = 0;
        switch (w.state) {
            case ABSENT -> {
                w.quietRooms = Math.min(hunt.data().appearance().quietRoomsCap(), w.quietRooms + 1);
                rollForWulf();
            }
            case LEAVING -> gone();
            case WARNING, PURSUE, ARRIVING -> {
                if (hunt.neverIn().contains(room)) {
                    // Found by the full-run forge: it followed players into lairs, which §14.3 keeps a clean
                    // puzzle. A room it never appears in is one it never enters — the chase ends at the door,
                    // unscored, so a sanctuary cannot be farmed for escapes.
                    gone();
                    return;
                }
                int distance = Math.max(Math.abs(room.col() - w.origin.col()), Math.abs(room.row() - w.origin.row()));
                if (distance >= hunt.data().pursuit().giveUpRoomDistance()) {
                    gone();
                    evaded();
                    return;
                }
                // It comes in by the edge the player did, where it was along that edge: a mirrored
                // position, a beat later. Still arriving from an earlier flip, it takes the player's line.
                Wulf.Edge edge = room.col() > from.col() ? Wulf.Edge.WEST
                        : room.col() < from.col() ? Wulf.Edge.EAST
                        : room.row() > from.row() ? Wulf.Edge.NORTH
                        : Wulf.Edge.SOUTH;
                int along;
                if (w.state == Wulf.State.ARRIVING) {
                    along = edge.alongY() ? Fixed.px(player.yFp) - room.row() * ROOM_H_PX
                            : Fixed.px(player.xFp) - room.col() * ROOM_W_PX;
                } else {
                    along = edge.alongY() ? Fixed.px(w.body.yFp) - from.row() * ROOM_H_PX
                            : Fixed.px(w.body.xFp) - from.col() * ROOM_W_PX;
                }
                if (w.state == Wulf.State.WARNING) {
                    w.pursuitTicks = 0;
                }
                w.state = Wulf.State.ARRIVING;
                w.stateTick = 0;
                w.arrivalEdge = edge;
                w.arrivalAlongPx = along;
                w.followedFlips++;
            }
        }
    }

    /** Given up (§13.4): it runs off the nearest edge rather than vanishing where it stands. */
    private void leave() {
        Wulf w = wulf;
        Creature body = w.body;
        int cx = body.centreXPx() - room.col() * ROOM_W_PX;
        int cy = body.centreYPx() - room.row() * ROOM_H_PX;
        int[] distance = {cx, ROOM_W_PX - cx, cy, ROOM_H_PX - cy};
        int nearest = 0;
        for (int i = 1; i < distance.length; i++) {
            if (distance[i] < distance[nearest]) {
                nearest = i;
            }
        }
        Wulf.Edge edge = Wulf.Edge.values()[nearest];
        w.leaveDirX = edge == Wulf.Edge.WEST ? -1 : edge == Wulf.Edge.EAST ? 1 : 0;
        w.leaveDirY = edge == Wulf.Edge.NORTH ? -1 : edge == Wulf.Edge.SOUTH ? 1 : 0;
        body.direction(w.leaveDirX, w.leaveDirY);
        w.state = Wulf.State.LEAVING;
        w.stateTick = 0;
        evaded();
    }

    private void stepOut(WulfData.Pursuit pursuit) {
        Wulf w = wulf;
        Creature body = w.body;
        CreatureData.Box b = body.species().collisionBox();
        CreatureData.Speed speed = body.species().speed();
        int ox = body.xFp;
        int oy = body.yFp;
        // The scenery still stops it; the room's edge does not.
        body.xFp = Collision.resolve(world, b.x(), b.y(), b.w(), b.h(), body.xFp, body.yFp, w.leaveDirX * speed.xFp(), true);
        body.yFp = Collision.resolve(world, b.x(), b.y(), b.w(), b.h(), body.xFp, body.yFp, w.leaveDirY * speed.yFp(), false);
        body.moveTicks = body.xFp != ox || body.yFp != oy ? body.moveTicks + 1 : 0;
        int left = Fixed.px(body.xFp) + b.x() - room.col() * ROOM_W_PX;
        int top = Fixed.px(body.yFp) + b.y() - room.row() * ROOM_H_PX;
        boolean outside = left >= ROOM_W_PX || left + b.w() <= 0 || top >= ROOM_H_PX || top + b.h() <= 0;
        if (outside || w.stateTick >= pursuit.leaveMaxTicks()) {
            gone();
        }
    }

    /** §13.5: never a kill and never points — pushed back along the swing and stunned, once per swing. */
    private void parryWulf() {
        Wulf w = wulf;
        Creature body = w.body;
        if (!w.touchable() || body.lastHitSwing == swingSerial) {
            return;
        }
        CreatureData.Box b = body.species().collisionBox();
        if (!overlaps(sabre, Fixed.px(body.xFp) + b.x(), Fixed.px(body.yFp) + b.y(), b.w(), b.h())) {
            return;
        }
        body.lastHitSwing = swingSerial;
        sounds.play(SFX_WULF_PARRY);
        PlayerData.Sabre s = rules.sabre();
        Direction8 f = player.facing;
        int push = Fixed.fp(s.repelWulfPx());
        if (f.diagonal()) {
            push = Fixed.mul(push, rules.speed().diagonalScaleFp());
        }
        context.move(body, f.dx() * push, f.dy() * push);
        body.aux(s.repelWulfStunTicks());
        body.hurtTicks = s.repelWulfStunTicks();
        w.parryFlash = eco.wulf().data().parry().borderFlashTicks();
        w.parries++;
    }

    private void gone() {
        wulf.state = Wulf.State.ABSENT;
        wulf.stateTick = 0;
        wulf.ticksSinceGone = 0;
        sounds.loop(SFX_WULF_GROWL, false);
    }

    private void evaded() {
        wulf.evasions++;
        addScore(eco.wulf().evadedScore());
    }

    private static int saturating(int ticks) {
        return ticks < SATURATED ? ticks + 1 : ticks;
    }

    /** Score, and the extra lives its thresholds award, capped at the maximum (§16.3). */
    private void addScore(int points) {
        score += points;
        List<Integer> thresholds = rules.lives().extraAt();
        while (extraLivesAwarded < thresholds.size() && score >= thresholds.get(extraLivesAwarded)) {
            extraLivesAwarded++;
            if (player.lives < rules.lives().max()) {
                player.lives++;
                sounds.play(SFX_EXTRA_LIFE);
            }
        }
    }

    /** What a behaviour may see and do (§12.5). */
    private final class Context implements SimContext {

        @Override
        public Rng rng() {
            return rng;
        }

        @Override
        public long tick() {
            return tick;
        }

        @Override
        public boolean playerAlive() {
            return player.mode == Player.Mode.ALIVE;
        }

        @Override
        public int playerCentreXPx() {
            PlayerData.Box b = rules.collisionBox();
            return Fixed.px(player.xFp) + b.x() + b.w() / 2;
        }

        @Override
        public int playerCentreYPx() {
            PlayerData.Box b = rules.collisionBox();
            return Fixed.px(player.yFp) + b.y() + b.h() / 2;
        }

        @Override
        public int roomLeftPx() {
            return room.col() * ROOM_W_PX;
        }

        @Override
        public int roomTopPx() {
            return room.row() * ROOM_H_PX;
        }

        @Override
        public int move(Creature c, int dxFp, int dyFp) {
            CreatureData.Box b = c.species().collisionBox();
            CollisionWorld w = c.species().ignoresScenery() ? roomEdges : roomSolid;
            int bits = 0;
            if (dxFp != 0) {
                int nx = Collision.resolve(w, b.x(), b.y(), b.w(), b.h(), c.xFp, c.yFp, dxFp, true);
                if (nx != c.xFp + dxFp) {
                    bits |= BLOCKED_X;
                }
                c.xFp = nx;
            }
            if (dyFp != 0) {
                int ny = Collision.resolve(w, b.x(), b.y(), b.w(), b.h(), c.xFp, c.yFp, dyFp, false);
                if (ny != c.yFp + dyFp) {
                    bits |= BLOCKED_Y;
                }
                c.yFp = ny;
            }
            return bits;
        }

        @Override
        public int step(Creature c, int dx, int dy, int speedXFp, int speedYFp) {
            int vx = speedXFp;
            int vy = speedYFp;
            if (dx != 0 && dy != 0) {
                vx = Fixed.mul(vx, eco.creatures().diagonalScaleFp());
                vy = Fixed.mul(vy, eco.creatures().diagonalScaleFp());
            }
            return move(c, dx * vx, dy * vy);
        }

        @Override
        public boolean probe(Creature c, int dxPx, int dyPx) {
            CreatureData.Box b = c.species().collisionBox();
            CollisionWorld w = c.species().ignoresScenery() ? roomEdges : roomSolid;
            return Collision.blocked(w, b.x(), b.y(), b.w(), b.h(), c.xFp + Fixed.fp(dxPx), c.yFp + Fixed.fp(dyPx));
        }

        @Override
        public boolean lineClear(int x0Px, int y0Px, int x1Px, int y1Px) {
            int dx = x1Px - x0Px;
            int dy = y1Px - y0Px;
            int n = Math.max(Math.abs(dx), Math.abs(dy)) / 4 + 1;
            for (int i = 0; i <= n; i++) {
                int x = x0Px + dx * i / n;
                int y = y0Px + dy * i / n;
                if (roomSolid.isSolid(Math.floorDiv(x, CELL_PX), Math.floorDiv(y, CELL_PX))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void throwSpear(Creature from, int dx, int dy) {
            if (eco.creatures().projectiles().isEmpty() || (dx == 0 && dy == 0)) {
                return;
            }
            spears.add(new Spear(nextEntityId++, Fixed.fp(from.centreXPx()), Fixed.fp(from.centreYPx()), dx, dy));
            sounds.play(SFX_SPEAR_THROW);
        }
    }

    /** How the populator puts creatures into the current room. */
    private final class Spawns implements Spawner {

        @Override
        public boolean spawn(String speciesId, int localXPx, int localYPx, Herd herd, Rng spawnRng) {
            CreatureData roster = eco.creatures();
            int index = roster.indexOf(speciesId);
            CreatureData.Species s = roster.creatures().get(index);
            int xFp = Fixed.fp(room.col() * ROOM_W_PX + localXPx);
            int yFp = Fixed.fp(room.row() * ROOM_H_PX + localYPx);
            CreatureData.Box b = s.collisionBox();
            if (Collision.blocked(s.ignoresScenery() ? roomEdges : roomSolid, b.x(), b.y(), b.w(), b.h(), xFp, yFp)) {
                return false;
            }
            Creature c = new Creature(nextEntityId++, s, index, xFp, yFp, herd);
            creatures.add(c);
            behaviours[index].spawn(c, context, spawnRng);
            return true;
        }

        @Override
        public Herd newHerd() {
            return new Herd();
        }

        @Override
        public int entryLocalXPx() {
            return Fixed.px(player.entryXFp) - room.col() * ROOM_W_PX;
        }

        @Override
        public int entryLocalYPx() {
            return Fixed.px(player.entryYFp) - room.row() * ROOM_H_PX;
        }

        @Override
        public boolean reachable(String speciesId, int localXPx, int localYPx) {
            if (eco.creatures().species(speciesId).ignoresScenery()) {
                return true;
            }
            if (reach == null) {
                // From where the player stands on arrival — pulled clear of the edge they crossed.
                int[] start = SpawnFinder.nearestFree(world, rules.collisionBox(), rules.spawn().insideRoomPx(), room,
                        player.entryXFp, player.entryYFp);
                reach = Reach.from(roomSolid, rules.collisionBox(), room, start[0], start[1]);
            }
            return reach.near(localXPx, localYPx, REACH_SLACK_PX);
        }

        @Override
        public int amuletPieces() {
            return quest.piecesHeld;
        }

        @Override
        public int preferredLocalYPx(String speciesId, int proposedLocalYPx) {
            int index = eco.creatures().indexOf(speciesId);
            return behaviours[index].preferredLocalYPx(eco.creatures().creatures().get(index), proposedLocalYPx);
        }
    }

    // ---------------------------------------------------------------- reads

    public Player player() {
        return player;
    }

    public RoomAddress room() {
        return room;
    }

    public long tick() {
        return tick;
    }

    /** Ticks left in the current flip-screen hitch. */
    public int freezeRemaining() {
        return freeze;
    }

    public int roomsEntered() {
        return roomsEntered;
    }

    /** The live sabre hitbox in world pixels, or {@link PixelRect#NONE}. */
    public PixelRect sabre() {
        return sabre;
    }

    /** The current room's creatures, in update order. Read-only. */
    public List<Creature> creatures() {
        return creaturesView;
    }

    public List<Spear> spears() {
        return spearsView;
    }

    public long score() {
        return score;
    }

    public int kills() {
        return kills;
    }

    /** How many times Vale has gone down this run (§21.5). */
    public int deaths() {
        return deaths;
    }

    /** What died on the blade, by species id. Ledger bookkeeping, outside the state hash. */
    public Map<String, Integer> killsBySpecies() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(killsBySpecies));
    }

    /** What killed Vale, by species id, or {@code spear} / {@code dev}. */
    public Map<String, Integer> deathsByCause() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(deathsByCause));
    }

    /** Which flowers were picked, by colour name. */
    public Map<String, Integer> orchidsByColour() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(orchidsByColour));
    }

    public int visits(RoomAddress address) {
        return visits[address.index()];
    }

    public Ecosystem ecosystem() {
        return eco;
    }

    public long runSeed() {
        return runSeed;
    }

    /**
     * The Ultimate-style border: a red strobe while dying — two ticks on, two off, four
     * times (§11.7) — and while the Wulf warns (§13.3); white for a parry (§13.5).
     */
    public BorderFlash borderFlash() {
        if (player.mode == Player.Mode.DYING && player.modeTick < 16 && player.modeTick % 4 < 2) {
            return BorderFlash.ALARM;
        }
        if (wulf.parryFlash > 0) {
            return BorderFlash.PARRY;
        }
        if (quest.pickupFlash > 0) {
            return BorderFlash.PICKUP;
        }
        WulfData.Appearance a = eco.wulf().data().appearance();
        if (wulf.state == Wulf.State.WARNING && wulf.stateTick % a.warningFlashPeriodTicks() < a.warningFlashOnTicks()) {
            return BorderFlash.ALARM;
        }
        return BorderFlash.NONE;
    }

    public boolean borderAlarm() {
        return borderFlash() == BorderFlash.ALARM;
    }

    public Wulf wulf() {
        return wulf;
    }

    public Quest quest() {
        return quest;
    }

    /** Whether the player's sprite is flashing white this tick: immunity (§15.2). */
    public boolean effectFlash() {
        return effect.kind().flashesThePlayer()
                && (tick / eco.orchids().immunity().flashPeriodTicks()) % 2 == 0;
    }

    /** The one effect the player is under (§15.2). */
    public EffectState effect() {
        return effect;
    }

    /** The current room's orchid anchors, room-local feet positions as {@code x, y} pairs. */
    public int[] orchidAnchors() {
        return roomOrchids.clone();
    }

    public OrchidField orchidField() {
        return orchids;
    }

    /** Where anchor {@code i} is in its cycle this tick (§15.1). */
    public int orchidPhase(int anchor) {
        return orchids.phase(tick, orchidCycleStart[orchidBase + anchor]);
    }

    /** Which orchid anchor {@code i} is growing this time round. */
    public int orchidAt(int anchor) {
        return orchids.orchidAt(runSeed, room, anchor, orchidCycleStart[orchidBase + anchor]);
    }

    /**
     * How many orchid stages have been worked out since the game began. The jungle goes on
     * flowering everywhere, but only the room the player is in costs anything (§15.1) — this
     * is what the test measures.
     */
    public long orchidStagesRead() {
        return orchidStagesRead;
    }

    /**
     * Hands the simulation somewhere to announce noises (§18.1). Sound never
     * changes what happens: the sink is write-only, and the default is silence.
     *
     * @param everyTicks how often walking makes a footstep
     */
    public void sounds(SoundSink sink, int everyTicks) {
        this.sounds = sink == null ? SoundSink.NONE : sink;
        this.footstepEveryTicks = Math.max(1, everyTicks);
    }

    /** Puts the player under an effect outright: {@code --debug-effect}, and the tests. */
    public boolean giveEffect(String effectName) {
        int which = eco.orchids().indexOfEffect(effectName);
        if (which < 0 || player.mode != Player.Mode.ALIVE) {
            return false;
        }
        effect.take(which, eco.orchids().orchid(which));
        return true;
    }

    public PlayerData rules() {
        return rules;
    }

    /** FNV-1a over every field of simulation state: the determinism contract (§6.4). */
    public long stateHash() {
        long h = 0xcbf29ce484222325L;
        h = mix(h, tick);
        h = mix(h, room.col());
        h = mix(h, room.row());
        h = mix(h, freeze);
        h = mix(h, roomsEntered);
        h = mix(h, player.xFp);
        h = mix(h, player.yFp);
        h = mix(h, player.facing.ordinal());
        h = mix(h, player.walkTicks);
        h = mix(h, player.swingTick);
        h = mix(h, player.cooldown);
        h = mix(h, player.lives);
        h = mix(h, player.invulnTicks);
        h = mix(h, player.mode.ordinal());
        h = mix(h, player.modeTick);
        h = mix(h, player.entryXFp);
        h = mix(h, player.entryYFp);
        h = mix(h, rng.stateHash());
        h = mix(h, score);
        h = mix(h, kills);
        h = mix(h, swingSerial);
        h = mix(h, nextEntityId);
        h = mix(h, extraLivesAwarded);
        for (int i = 0; i < visits.length; i++) {
            if (visits[i] != 0) {
                h = mix(h, ((long) i << 32) | visits[i]);
            }
        }
        for (int i = 0; i < creatures.size(); i++) {
            Creature c = creatures.get(i);
            h = mix(h, c.id());
            h = mix(h, c.speciesIndex());
            h = mix(h, c.xFp);
            h = mix(h, c.yFp);
            h = mix(h, ((long) c.dirX << 32) ^ (c.dirY & 0xFFFFFFFFL));
            h = mix(h, c.faceX);
            h = mix(h, c.hp);
            h = mix(h, c.mode.ordinal());
            h = mix(h, c.modeTick);
            h = mix(h, c.hurtTicks);
            h = mix(h, c.lastHitSwing);
            h = mix(h, c.timer());
            h = mix(h, c.phase());
            h = mix(h, c.aux());
            h = mix(h, c.anchorXFp());
            h = mix(h, c.anchorYFp());
            h = mix(h, ((long) c.herd().dirX() << 32) ^ (c.herd().dirY() & 0xFFFFFFFFL));
        }
        for (int i = 0; i < spears.size(); i++) {
            Spear s = spears.get(i);
            h = mix(h, s.id());
            h = mix(h, s.xFp);
            h = mix(h, s.yFp);
            h = mix(h, s.ticks);
        }
        Wulf w = wulf;
        Creature wb = w.body;
        h = mix(h, w.state.ordinal());
        h = mix(h, w.stateTick);
        h = mix(h, w.pursuitTicks);
        h = mix(h, w.origin.index());
        h = mix(h, w.arrivalEdge.ordinal());
        h = mix(h, w.arrivalAlongPx);
        h = mix(h, ((long) w.leaveDirX << 32) ^ (w.leaveDirY & 0xFFFFFFFFL));
        h = mix(h, w.ticksSinceGone);
        h = mix(h, w.ticksSinceRespawn);
        h = mix(h, w.ticksInRoom);
        h = mix(h, w.quietRooms);
        h = mix(h, w.parryFlash);
        h = mix(h, w.appearances);
        h = mix(h, w.parries);
        h = mix(h, w.evasions);
        h = mix(h, w.followedFlips);
        h = mix(h, wb.xFp);
        h = mix(h, wb.yFp);
        h = mix(h, ((long) wb.dirX << 32) ^ (wb.dirY & 0xFFFFFFFFL));
        h = mix(h, wb.faceX);
        h = mix(h, wb.timer());
        h = mix(h, wb.aux());
        h = mix(h, wb.hurtTicks);
        h = mix(h, wb.lastHitSwing);
        h = mix(h, wb.moveTicks);
        Quest q = quest;
        h = mix(h, q.piecesHeld);
        h = mix(h, q.slotMask);
        h = mix(h, (q.taken[0] ? 1 : 0) | (q.taken[1] ? 2 : 0) | (q.taken[2] ? 4 : 0) | (q.taken[3] ? 8 : 0));
        h = mix(h, q.lair);
        h = mix(h, q.keeperState.ordinal());
        h = mix(h, q.keeperTick);
        h = mix(h, q.keeperHere ? 1 : 0);
        h = mix(h, q.pickupFlash);
        h = mix(h, q.flyTicks);
        h = mix(h, q.flySlot);
        h = mix(h, q.hintTicks);
        h = mix(h, q.hintDirection.ordinal());
        h = mix(h, q.escapeBonus + 31 * q.timeBonus + 961 * q.livesBonus);
        for (int i = 0; i < q.hintUsed.length; i++) {
            if (q.hintUsed[i]) {
                h = mix(h, i);
            }
        }
        if (q.lair >= 0) {
            Creature g = q.guardian;
            h = mix(h, g.xFp);
            h = mix(h, g.yFp);
            h = mix(h, ((long) g.dirX << 32) ^ (g.dirY & 0xFFFFFFFFL));
            h = mix(h, g.timer());
            h = mix(h, g.phase());
            h = mix(h, g.aux());
            h = mix(h, g.hurtTicks);
            h = mix(h, g.lastHitSwing);
        }
        h = mix(h, q.keeper.xFp);
        h = mix(h, q.keeper.yFp);
        h = mix(h, effect.hashValue());
        for (int i = 0; i < orchidCycleStart.length; i++) {
            if (orchidCycleStart[i] != Long.MIN_VALUE) {
                h = mix(h, ((long) i << 40) ^ orchidCycleStart[i]);
            }
        }
        return h;
    }

    private static long mix(long h, long v) {
        long out = h;
        for (int i = 0; i < 8; i++) {
            out ^= (v >>> (i * 8)) & 0xFF;
            out *= 0x100000001b3L;
        }
        return out;
    }
}
