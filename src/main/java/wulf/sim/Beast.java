package wulf.sim;

import wulf.data.CreatureData;
import wulf.engine.Fixed;
import wulf.world.RoomAddress;

/**
 * One of the original's big beasts (AGENTS.md §12.9, [CANON]): a rhino, a tribesman, or the
 * sleeper that is a hippo or a warthog. Unlike a room's creatures they live on the whole map,
 * moving whether or not the player is near, and nothing kills them. Positions are the feet,
 * in world 8.8 fixed point.
 */
public final class Beast {

    public enum Kind { RHINO, TRIBESMAN, SLEEPER }

    final int id;
    final Kind kind;
    /** What it is now, by index into the rules' kinds: a sleeper set down in a new room may wake a hippo or a warthog ({@code $9F06}). */
    int kindIndex;
    CreatureData.Species species;
    /** A palette index: the colour the actor table gives it. */
    final int tint;
    RoomAddress at;
    int xFp;
    int yFp;
    int vxFp;
    int vyFp;
    int faceX = 1;
    boolean asleep;
    /** A sleeper woken stands this many ticks before it charges ({@code $9EF7}). */
    int pauseTicks;
    /** A tribesman level with an amulet quarter flashes and calls ({@code $9D41}). */
    boolean hinting;
    int moveTicks;

    Beast(int id, Kind kind, int kindIndex, CreatureData.Species species, int tint, RoomAddress at, int xFp, int yFp) {
        this.id = id;
        this.kind = kind;
        this.kindIndex = kindIndex;
        this.species = species;
        this.tint = tint;
        this.at = at;
        this.xFp = xFp;
        this.yFp = yFp;
    }

    public int id() {
        return id;
    }

    public Kind kind() {
        return kind;
    }

    public CreatureData.Species species() {
        return species;
    }

    public int tint() {
        return tint;
    }

    public RoomAddress at() {
        return at;
    }

    public int xFp() {
        return xFp;
    }

    public int yFp() {
        return yFp;
    }

    public int faceX() {
        return faceX;
    }

    public boolean asleep() {
        return asleep;
    }

    public boolean hinting() {
        return hinting;
    }

    /** Consecutive ticks of actual movement: drives the walk animation. */
    public int moveTicks() {
        return moveTicks;
    }

    public int centreXPx() {
        return Fixed.px(xFp) + species.collisionBox().x() + species.collisionBox().w() / 2;
    }

    public int centreYPx() {
        return Fixed.px(yFp) + species.collisionBox().y() + species.collisionBox().h() / 2;
    }
}
