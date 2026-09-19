package wulf.sim;

import wulf.data.OrchidData;
import wulf.engine.Rng;
import wulf.world.CollisionWorld;
import wulf.world.RoomAddress;

/**
 * Where orchids grow and what stage they are at (AGENTS.md §15.1). Pure arithmetic
 * on the clock: an anchor keeps only the tick its cycle began, so the jungle goes on
 * flowering in rooms nobody is in without a tick of work being spent on them.
 */
public final class OrchidField {

    public enum Stage { SEED, SPROUT, BUD, BLOOM, WILT }

    private final OrchidData data;

    public OrchidField(OrchidData data) {
        this.data = data;
    }

    /**
     * The anchors of a room, as room-local feet positions, packed {@code x, y} pairs.
     * Deterministic from the run seed and the room: the same jungle every time.
     */
    public int[] anchors(RoomAddress room, CollisionWorld world, long runSeed) {
        OrchidData.Anchors rules = data.anchors();
        if (rules.perRoom() == 0 || data.orchids().isEmpty()) {
            return new int[0];
        }
        Rng rng = new Rng(Rng.hash(runSeed, room.col(), room.row(), 0x0F10));
        int[] found = new int[rules.perRoom() * 2];
        int count = 0;
        int span = Simulation.ROOM_W_PX - 2 * rules.insetPx();
        int height = Simulation.ROOM_H_PX - 2 * rules.insetPx();
        for (int attempt = 0; attempt < rules.attempts() && count < rules.perRoom(); attempt++) {
            int x = rules.insetPx() + rng.nextInt(span);
            int y = rules.insetPx() + rng.nextInt(height);
            if (blocked(room, world, x, y)) {
                continue;
            }
            boolean crowded = false;
            for (int i = 0; i < count && !crowded; i++) {
                crowded = Math.abs(found[2 * i] - x) < rules.minSpacingPx()
                        && Math.abs(found[2 * i + 1] - y) < rules.minSpacingPx();
            }
            if (!crowded) {
                found[2 * count] = x;
                found[2 * count + 1] = y;
                count++;
            }
        }
        int[] out = new int[count * 2];
        System.arraycopy(found, 0, out, 0, out.length);
        return out;
    }

    /** A bloom is 16x16 on the ground: its whole footprint must be clear. */
    private boolean blocked(RoomAddress room, CollisionWorld world, int localX, int localY) {
        int left = room.col() * Simulation.ROOM_W_PX + localX - 8;
        int top = room.row() * Simulation.ROOM_H_PX + localY - 16;
        for (int y = top; y < top + 16; y += 4) {
            for (int x = left; x < left + 16; x += 4) {
                if (world.isSolid(Math.floorDiv(x, Simulation.CELL_PX), Math.floorDiv(y, Simulation.CELL_PX))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Where in its cycle an anchor is, given when that cycle began. */
    public int phase(long tick, long cycleStartTick) {
        int total = data.cycle().totalTicks();
        return Math.floorMod(tick - cycleStartTick, total);
    }

    public Stage stageAt(int phase) {
        OrchidData.Cycle c = data.cycle();
        int at = Math.floorMod(phase, c.totalTicks());
        if (at < c.seedTicks()) {
            return Stage.SEED;
        }
        at -= c.seedTicks();
        if (at < c.sproutTicks()) {
            return Stage.SPROUT;
        }
        at -= c.sproutTicks();
        if (at < c.budTicks()) {
            return Stage.BUD;
        }
        at -= c.budTicks();
        return at < c.bloomTicks() ? Stage.BLOOM : Stage.WILT;
    }

    /** Ticks into the current stage: the bloom's sway, and the bud's warning, count from here. */
    public int stageTick(int phase) {
        OrchidData.Cycle c = data.cycle();
        int at = Math.floorMod(phase, c.totalTicks());
        for (int length : new int[] {c.seedTicks(), c.sproutTicks(), c.budTicks(), c.bloomTicks()}) {
            if (at < length) {
                return at;
            }
            at -= length;
        }
        return at;
    }

    /**
     * Which orchid this anchor is growing this time round. Decided per cycle, so the
     * colour a player sees at bud is the colour they get at bloom (§15.1).
     */
    public int orchidAt(long runSeed, RoomAddress room, int anchor, long cycleStartTick) {
        Rng rng = new Rng(Rng.hash(runSeed, room.index(), anchor, cycleStartTick));
        int total = 0;
        for (OrchidData.Orchid o : data.orchids()) {
            total += o.weight();
        }
        int roll = rng.nextInt(total);
        for (int i = 0; i < data.orchids().size(); i++) {
            roll -= data.orchids().get(i).weight();
            if (roll < 0) {
                return i;
            }
        }
        return data.orchids().size() - 1;
    }

    /** The cycle an anchor starts on, staggered so a room's flowers do not bloom in step. */
    public long firstCycleStart(long runSeed, RoomAddress room, int anchor) {
        Rng rng = new Rng(Rng.hash(runSeed, room.index(), anchor, 0x5EED));
        return -rng.nextInt(data.cycle().totalTicks());
    }

    public OrchidData data() {
        return data;
    }
}
