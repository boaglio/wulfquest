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
    /** The panel's places for the amulet: four quarters, however many pieces a job breaks it into (§14.9). */
    public static final int SLOTS = 4;

    public enum Keeper {
        /** In the doorway under the arch, lethal ($9F2F). */
        BLOCKING,
        /** The amulet is whole, so it is harmless: sliding away from the player's half of the room ($9F40). */
        STEPPING_ASIDE,
        /** The amulet is whole, and it stands at the end it slid to — until the player crosses to that half. */
        ASIDE
    }

    final boolean[] taken = new boolean[SLOTS];
    int piecesHeld;
    int slotMask;
    /** The lair the player is in, by index into {@code landmarks.lairs()}, or -1. */
    int lair = -1;
    Creature guardian;
    final Creature keeper;
    /** Where the Keeper stands, as room-local feet x in 8.8: it keeps its place when the player leaves (§14.4). */
    int keeperLocalXFp = Integer.MIN_VALUE;
    boolean keeperHere;
    Keeper keeperState = Keeper.BLOCKING;
    int keeperTick;
    int pickupFlash;
    int flyTicks;
    int flySlot = -1;
    /** Every slot the flying piece fills: one quarter, or a later job's half or whole (§14.9). */
    int flyMask;
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

    /** §14.8: the map scroll taken, and which eyes; at most {@link #MAX_EYES}. */
    static final int MAX_EYES = 8;
    boolean mapTaken;
    final boolean[] eyeTaken = new boolean[MAX_EYES];

    Quest(Creature guardian, Creature keeper) {
        this.guardian = guardian;
        this.keeper = keeper;
    }

    /** Whether the map scroll has been found: the map screen then shows every room (§14.8). */
    public boolean mapTaken() {
        return mapTaken;
    }

    /** Whether eye {@code i} of {@code landmarks.json → items.eyes} has been found. */
    public boolean eyeTaken(int i) {
        return eyeTaken[i];
    }

    public int piecesHeld() {
        return piecesHeld;
    }

    /**
     * How much of the amulet is held, in quarters: the pieces held in the game itself, twice
     * that for halves, four for a whole (§14.9).
     */
    public int quarters() {
        return Integer.bitCount(slotMask);
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

    /** Every slot the flying piece fills. */
    public int flyMask() {
        return flyMask;
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
