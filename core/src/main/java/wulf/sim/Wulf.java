package wulf.sim;

import wulf.world.RoomAddress;

/**
 * The Wulf's simulation state (AGENTS.md §13). There is exactly one; {@link Simulation}
 * drives it and everyone else reads it.
 *
 * <p>The original's (§13.8, the default) prowls one row of the map for the whole game,
 * in its own room whether the player is there or not. The roaming one ({@code
 * features.roamingWulf}, [NEW]) is an ordinary {@link Creature} steered by CHASE_DIRECT,
 * carried across room flips — only faster, unkillable, and never left behind.
 */
public final class Wulf {

    /** The body's entity id: never handed to a creature. */
    static final int ID = -1;

    public enum State {
        /** Not here. Rolls for an appearance on room entry and every {@code rollEveryTicks}. */
        ABSENT,
        /** Standing at an edge, howling, for {@code warningTicks}. Already lethal to touch. */
        WARNING,
        /** Chasing. */
        PURSUE,
        /** Followed the player through a flip; off screen until the arrival delay has passed. */
        ARRIVING,
        /** Gave up: running off the nearest edge. */
        LEAVING,
        /** §13.8: running along its row, here or elsewhere. */
        PROWLING,
        /** §13.8: stopped by a sabre held towards it as it came. */
        SITTING,
        /** §13.8: getting up again, moving at its old speed. */
        RISING
    }

    /**
     * §13.8, the original's {@code IX+$02} while it prowls: may it still turn towards the
     * player, and be made to sit? Free again whenever the player is off its row.
     */
    public enum Latch {
        /** The player is not on its row: it will turn towards them when they are. */
        FREE,
        /** It has turned towards the player; a sabre can still make it sit. */
        STEERED,
        /** It has sat and got up: no more turning, no more sitting, until the player leaves the row. */
        SPENT
    }

    /** A room edge. West and east edges run along y; north and south along x. */
    public enum Edge {
        WEST, EAST, NORTH, SOUTH;

        boolean alongY() {
            return this == WEST || this == EAST;
        }
    }

    final Creature body;
    State state = State.ABSENT;
    int stateTick;
    int pursuitTicks;
    RoomAddress origin = new RoomAddress(0, 0);
    Edge arrivalEdge = Edge.WEST;
    int arrivalAlongPx;
    int leaveDirX;
    int leaveDirY;
    int ticksSinceGone;
    int ticksSinceRespawn;
    int ticksInRoom;
    int quietRooms;
    int parryFlash;
    int appearances;
    int parries;
    int evasions;
    int followedFlips;
    /** §13.8: the room it is in, which may not be the player's. */
    RoomAddress at = new RoomAddress(0, 0);
    /** §13.8: -1 west, 1 east. */
    int heading = -1;
    int speedFp;
    Latch latch = Latch.FREE;
    /** §13.8: in the player's room this tick, so seen, heard and touchable. */
    boolean here;

    Wulf(Creature body) {
        this.body = body;
    }

    public Creature body() {
        return body;
    }

    public State state() {
        return state;
    }

    /** Ticks in the current state. */
    public int stateTick() {
        return stateTick;
    }

    /** Ticks since the chase began, towards {@code giveUpTicks}. */
    public int pursuitTicks() {
        return pursuitTicks;
    }

    /** The room it appeared in: the give-up distance is measured from here. */
    public RoomAddress origin() {
        return origin;
    }

    public Edge arrivalEdge() {
        return arrivalEdge;
    }

    /** On screen: warning, chasing or leaving; or, prowling (§13.8), in the player's room. */
    public boolean visible() {
        return switch (state) {
            case WARNING, PURSUE, LEAVING -> true;
            case PROWLING, SITTING, RISING -> here;
            case ABSENT, ARRIVING -> false;
        };
    }

    /** Whether touching it kills, and a swing can parry it: whenever it is on screen. */
    public boolean touchable() {
        return visible();
    }

    public int appearances() {
        return appearances;
    }

    public int parries() {
        return parries;
    }

    /** Chases that ended with the player alive: given up, or outrun by distance. */
    public int evasions() {
        return evasions;
    }

    /** Room flips it has followed the player through, this chase. */
    public int followedFlips() {
        return followedFlips;
    }

    public int quietRooms() {
        return quietRooms;
    }

    /** §13.8: the room it is in. */
    public RoomAddress at() {
        return at;
    }

    public int heading() {
        return heading;
    }

    public Latch latch() {
        return latch;
    }
}
