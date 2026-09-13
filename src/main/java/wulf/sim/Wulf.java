package wulf.sim;

import wulf.world.RoomAddress;

/**
 * The Wulf's simulation state (AGENTS.md §13). There is exactly one, carried across
 * room flips; {@link Simulation} drives it and everyone else reads it.
 *
 * <p>Its body is an ordinary {@link Creature} steered by CHASE_DIRECT, so it moves,
 * collides and turns by the same rules as the rhino — only faster, unkillable, and
 * never left behind by a flip.
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
        LEAVING
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

    /** On screen: warning, chasing or leaving. */
    public boolean visible() {
        return state == State.WARNING || state == State.PURSUE || state == State.LEAVING;
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
}
