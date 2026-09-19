package wulf.sim.effects;

import wulf.data.OrchidData;
import wulf.engine.Rng;

/**
 * The one effect the player is under (AGENTS.md §15.2). A new bloom replaces the
 * old outright — effects never stack, never extend — which keeps this to one small
 * state and makes every combination testable by not having any.
 */
public final class EffectState {

    private EffectKind kind = EffectKind.NONE;
    private int orchid = -1;
    private int remainingTicks;
    private int totalTicks;
    private int scaleFp = 256;
    private int deliriumTimer;
    private int deliriumHold;
    private int deliriumDx;
    private int deliriumDy;

    public EffectKind kind() {
        return kind;
    }

    /** Which orchid of {@code orchids.json} is at work, or -1: the panel's bar takes its colour. */
    public int orchid() {
        return orchid;
    }

    public int remainingTicks() {
        return remainingTicks;
    }

    /** How much of the effect is left, per mille: the panel's bar (§17.3). */
    public int remainingPerMille() {
        return totalTicks == 0 ? 0 : Math.max(0, Math.min(1000, remainingTicks * 1000 / totalTicks));
    }

    public boolean active() {
        return kind != EffectKind.NONE;
    }

    /** Takes a bloom: whatever was in the blood is gone. */
    public void take(int orchidIndex, OrchidData.Orchid taken) {
        kind = EffectKind.of(taken.effect());
        orchid = orchidIndex;
        remainingTicks = taken.ticks();
        totalTicks = taken.ticks();
        scaleFp = taken.speedScaleFp();
        deliriumTimer = 0;
        deliriumHold = 0;
        deliriumDx = 0;
        deliriumDy = 0;
    }

    public void clear() {
        kind = EffectKind.NONE;
        orchid = -1;
        remainingTicks = 0;
        totalTicks = 0;
        scaleFp = 256;
        deliriumHold = 0;
    }

    /** One tick older; the effect ends of its own accord. */
    public void tick() {
        if (kind == EffectKind.NONE) {
            return;
        }
        if (--remainingTicks <= 0) {
            clear();
        }
    }

    public int speedFp(int baseFp) {
        return kind.modifySpeedFp(baseFp, scaleFp);
    }

    /**
     * The direction the player actually goes, given the direction they asked for
     * (§15.2): reversed, or — under delirium — their own legs now and then.
     *
     * @return {dx, dy}
     */
    public int[] steer(int dx, int dy, OrchidData.Delirium delirium, Rng rng) {
        if (kind == EffectKind.REVERSAL) {
            return new int[] {-dx, -dy};
        }
        if (kind != EffectKind.DELIRIUM) {
            return new int[] {dx, dy};
        }
        if (deliriumHold > 0) {
            deliriumHold--;
            return new int[] {deliriumDx, deliriumDy};
        }
        if (--deliriumTimer <= 0) {
            deliriumTimer = delirium.everyMinTicks() + rng.nextInt(delirium.everyRandomTicks());
            deliriumHold = delirium.holdTicks();
            int turn = rng.nextInt(8);
            deliriumDx = switch (turn) {
                case 1, 2, 3 -> 1;
                case 5, 6, 7 -> -1;
                default -> 0;
            };
            deliriumDy = switch (turn) {
                case 0, 1, 7 -> -1;
                case 3, 4, 5 -> 1;
                default -> 0;
            };
            return new int[] {deliriumDx, deliriumDy};
        }
        return new int[] {dx, dy};
    }

    /** Everything that decides the state, for the determinism hash. */
    public long hashValue() {
        return ((long) kind.ordinal() << 48) ^ ((long) orchid << 40) ^ ((long) remainingTicks << 24)
                ^ ((long) totalTicks << 12) ^ ((long) deliriumTimer << 6) ^ (deliriumHold * 9L)
                ^ ((deliriumDx + 1) * 3L) ^ (deliriumDy + 1L) ^ scaleFp;
    }
}
