package wulf.sim;

/**
 * The quest's simulation state (AGENTS.md §14): which quarters are held, the guardian
 * of the lair the player is in, the Keeper of the Arch, the shrines' hints, and the
 * escape. {@link Simulation} drives it; everyone else reads it.
 */
public final class Quest {

    /** Entity ids never handed to a creature. */
    static final int GUARDIAN_ID = -2;
    static final int KEEPER_ID = -3;
    static final int ROOMS = 256;

    public enum Keeper {
        /** In front of the arch, lethal but for the nudge zone. */
        BLOCKING,
        /** The amulet is whole: moving aside over {@code keeperStepAsideTicks}. */
        STEPPING_ASIDE,
        /** Aside for good, and harmless. */
        ASIDE
    }

    final boolean[] taken = new boolean[4];
    int piecesHeld;
    int slotMask;
    /** The lair the player is in, by index into {@code landmarks.lairs()}, or -1. */
    int lair = -1;
    Creature guardian;
    final Creature keeper;
    boolean keeperHere;
    Keeper keeperState = Keeper.BLOCKING;
    int keeperTick;
    int pickupFlash;
    int flyTicks;
    int flySlot = -1;
    int flyFromXPx;
    int flyFromYPx;
    final boolean[] hintUsed = new boolean[ROOMS];
    int hintTicks;
    Direction8 hintDirection = Direction8.N;
    boolean hintToExit;
    int escapeFromXFp;
    long escapeBonus;
    long timeBonus;
    long livesBonus;
    long scoreBeforeBonuses;

    Quest(Creature guardian, Creature keeper) {
        this.guardian = guardian;
        this.keeper = keeper;
    }

    public int piecesHeld() {
        return piecesHeld;
    }

    /** Bit {@code slot} set for every quarter held: the panel draws from this (§17.3). */
    public int slotMask() {
        return slotMask;
    }

    public boolean taken(int lairIndex) {
        return taken[lairIndex];
    }

    /** Whether the player is in a lair: then {@link #guardian()} is on screen. */
    public boolean inLair() {
        return lair >= 0;
    }

    public int lair() {
        return lair;
    }

    public Creature guardian() {
        return guardian;
    }

    /** Whether the player is in the exit room: then {@link #keeper()} is on screen. */
    public boolean keeperHere() {
        return keeperHere;
    }

    public Creature keeper() {
        return keeper;
    }

    public Keeper keeperState() {
        return keeperState;
    }

    public int keeperTick() {
        return keeperTick;
    }

    /** Ticks left of the white border flash after a pickup (§14.6). */
    public int pickupFlash() {
        return pickupFlash;
    }

    /** Ticks left of the quarter's flight to its panel slot, counting down. */
    public int flyTicks() {
        return flyTicks;
    }

    public int flySlot() {
        return flySlot;
    }

    /** Where the flying quarter started, in world pixels. */
    public int flyFromXPx() {
        return flyFromXPx;
    }

    public int flyFromYPx() {
        return flyFromYPx;
    }

    /** Ticks left of a shrine's panel message (§14.5). */
    public int hintTicks() {
        return hintTicks;
    }

    public Direction8 hintDirection() {
        return hintDirection;
    }

    /** True when the hint points to the way out, because the amulet is already whole. */
    public boolean hintToExit() {
        return hintToExit;
    }

    public long escapeBonus() {
        return escapeBonus;
    }

    public long timeBonus() {
        return timeBonus;
    }

    public long livesBonus() {
        return livesBonus;
    }

    /** The score collected before the escape's bonuses were added: the tally's first line. */
    public long scoreBeforeBonuses() {
        return scoreBeforeBonuses;
    }
}
