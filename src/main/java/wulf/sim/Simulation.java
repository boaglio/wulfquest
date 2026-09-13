package wulf.sim;

import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/**
 * The game's single source of truth, advanced one fixed tick at a time
 * (AGENTS.md §6). Pure and deterministic: integer arithmetic only, no wall
 * clock, no AWT, no randomness yet. Given the same data and the same input
 * stream it produces the same {@link #stateHash()} on every tick, everywhere.
 *
 * <p>M3 covers Ranger Vale alone: movement, collision, flip-screen transitions,
 * the sabre, death and respawn.
 */
public final class Simulation {

    public static final int CELL_PX = 8;
    public static final int ROOM_W_PX = 256;
    public static final int ROOM_H_PX = 192;

    private final PlayerData rules;
    private final CollisionWorld world;
    private final int transitionFreezeTicks;
    private final Player player = new Player();

    private RoomAddress room;
    private int freeze;
    private long tick;
    private int roomsEntered;
    private PixelRect sabre = PixelRect.NONE;

    private Simulation(PlayerData rules, CollisionWorld world, int transitionFreezeTicks, int xFp, int yFp) {
        this.rules = rules;
        this.world = world;
        this.transitionFreezeTicks = transitionFreezeTicks;
        player.xFp = xFp;
        player.yFp = yFp;
        player.entryXFp = xFp;
        player.entryYFp = yFp;
        player.lives = rules.lives().start();
        this.room = new RoomAddress(roomColOf(rules.collisionBox(), xFp), roomRowOf(rules.collisionBox(), yFp));
    }

    /** A new game: the player on the free spot nearest the centre of {@code start}. */
    public static Simulation startingIn(PlayerData rules, CollisionWorld world, int transitionFreezeTicks,
                                        RoomAddress start) {
        int cx = Fixed.fp(start.col() * ROOM_W_PX + ROOM_W_PX / 2);
        int cy = Fixed.fp(start.row() * ROOM_H_PX + ROOM_H_PX / 2);
        int[] p = SpawnFinder.nearestFree(world, rules.collisionBox(), rules.spawn().insideRoomPx(), start, cx, cy);
        return new Simulation(rules, world, transitionFreezeTicks, p[0], p[1]);
    }

    /** A simulation with the player at an exact world position — for tests and tools. */
    public static Simulation at(PlayerData rules, CollisionWorld world, int transitionFreezeTicks, int xFp, int yFp) {
        return new Simulation(rules, world, transitionFreezeTicks, xFp, yFp);
    }

    public void tick(InputState in) {
        tick++;
        switch (player.mode) {
            case GAME_OVER -> {
                return;
            }
            case DYING -> {
                if (++player.modeTick >= rules.death().animTicks()) {
                    player.mode = Player.Mode.DOWN;
                    player.modeTick = 0;
                }
                return;
            }
            case DOWN -> {
                if (++player.modeTick >= rules.death().freezeTicks()) {
                    respawn();
                }
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
        updateRoom();
        sabre = computeSabre();
    }

    /**
     * Lethal contact (§11.7). Ignored while invulnerable or already down.
     *
     * @return whether the hit landed
     */
    public boolean kill() {
        if (player.mode != Player.Mode.ALIVE || player.invulnTicks > 0) {
            return false;
        }
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
                player.cooldown = s.cooldownTicks();
            }
        } else if (player.cooldown > 0) {
            player.cooldown--;
        }
        // A fresh key-down edge only: holding fire never auto-repeats (§11.6).
        if (player.swingTick < 0 && player.cooldown == 0 && in.firePressed()) {
            player.swingTick = 0;
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

    // ---------------------------------------------------------------- movement

    private void move(InputState in) {
        int dx = in.dx();
        int dy = in.dy();
        if (dx != 0 || dy != 0) {
            player.facing = Direction8.of(dx, dy);   // facing persists when the keys are released (§11.4)
        }
        PlayerData.Speed speed = rules.speed();
        int speedX = speed.xFp();
        int speedY = speed.yFp();
        if (player.swingTick >= 0 && rules.sabre().moveSpeedScaleFp() != Fixed.ONE) {
            speedX = Fixed.mul(speedX, rules.sabre().moveSpeedScaleFp());
            speedY = Fixed.mul(speedY, rules.sabre().moveSpeedScaleFp());
        }
        if (dx != 0 && dy != 0) {
            PlayerData.Box box = rules.collisionBox();
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
        player.xFp = resolveX(player.xFp, player.yFp, dx * speedX);
        player.yFp = resolveY(player.xFp, player.yFp, dy * speedY);
        boolean moved = player.xFp != oldX || player.yFp != oldY;
        player.walkTicks = moved ? player.walkTicks + 1 : 0;
    }

    private int resolveX(int x, int y, int delta) {
        if (delta == 0) {
            return x;
        }
        int target = x + delta;
        PlayerData.Box box = rules.collisionBox();
        if (!Collision.boxBlocked(world, box, target, y)) {
            return target;
        }
        // Blocked: settle on the nearest free WHOLE pixel short of the wall (§7.4).
        // Collision is tested per whole pixel, so keeping the target's fraction would
        // let a pinned player creep about inside the last free pixel.
        int from = Fixed.fp(Fixed.px(target));
        int to = Fixed.fp(Fixed.px(x));
        if (delta > 0) {
            for (int cand = from; cand >= to; cand -= Fixed.ONE) {
                if (!Collision.boxBlocked(world, box, cand, y)) {
                    return cand;
                }
            }
        } else {
            for (int cand = from + Fixed.ONE; cand <= to; cand += Fixed.ONE) {
                if (!Collision.boxBlocked(world, box, cand, y)) {
                    return cand;
                }
            }
        }
        return x;
    }

    private int resolveY(int x, int y, int delta) {
        if (delta == 0) {
            return y;
        }
        int target = y + delta;
        PlayerData.Box box = rules.collisionBox();
        if (!Collision.boxBlocked(world, box, x, target)) {
            return target;
        }
        int from = Fixed.fp(Fixed.px(target));
        int to = Fixed.fp(Fixed.px(y));
        if (delta > 0) {
            for (int cand = from; cand >= to; cand -= Fixed.ONE) {
                if (!Collision.boxBlocked(world, box, x, cand)) {
                    return cand;
                }
            }
        } else {
            for (int cand = from + Fixed.ONE; cand <= to; cand += Fixed.ONE) {
                if (!Collision.boxBlocked(world, box, x, cand)) {
                    return cand;
                }
            }
        }
        return y;
    }

    // ---------------------------------------------------------------- rooms

    private void updateRoom() {
        int col = roomColOf(rules.collisionBox(), player.xFp);
        int row = roomRowOf(rules.collisionBox(), player.yFp);
        if (col == room.col() && row == room.row()) {
            return;
        }
        // §7.5: the feet-box centre crossed an edge. Position is untouched — world
        // coordinates carry the cross-edge offset across exactly.
        room = new RoomAddress(col, row);
        freeze = transitionFreezeTicks;
        roomsEntered++;
        player.entryXFp = player.xFp;
        player.entryYFp = player.yFp;
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

    /** The Ultimate-style border strobe while dying: two ticks on, two off, four times (§11.7). */
    public boolean borderAlarm() {
        return player.mode == Player.Mode.DYING && player.modeTick < 16 && player.modeTick % 4 < 2;
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
