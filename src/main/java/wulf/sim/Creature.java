package wulf.sim;

import wulf.data.CreatureData;
import wulf.engine.Fixed;

/**
 * One creature's simulation state (AGENTS.md §12). The simulation owns position,
 * health and death; its behaviour (in {@code wulf.sim.ai}) steers it through the
 * public mutators and three general-purpose counters whose meaning each behaviour
 * documents.
 *
 * <p>Positions are the feet — bottom-centre — in world 8.8 fixed point, like the player.
 */
public final class Creature {

    /** APPEARING: the original's puff, still forming (§12.8) — harmless, but a blade can still take it. */
    public enum Mode { ALIVE, DYING, APPEARING }

    private final int id;
    private final CreatureData.Species species;
    private final int speciesIndex;
    private final Herd herd;

    int xFp;
    int yFp;
    int dirX = 1;
    int dirY;
    int faceX = 1;
    int hp;
    Mode mode = Mode.ALIVE;
    int modeTick;
    int hurtTicks;
    int lastHitSwing = -1;
    int moveTicks;
    /** §12.8: a swarm creature's colour, a palette index; -1 draws it in its own. */
    int tint = -1;
    /** §12.8: the room a swarm creature belongs to, by {@link wulf.world.RoomAddress#index()}; -1 for any other creature. */
    int homeRoom = -1;
    /** §12.8: how long it has waited, all told, in a room the player was not in ({@code $A493}). */
    int awayTicks;
    private int timer;
    private int phase;
    private int aux;
    private int anchorXFp;
    private int anchorYFp;

    Creature(int id, CreatureData.Species species, int speciesIndex, int xFp, int yFp, Herd herd) {
        this.id = id;
        this.species = species;
        this.speciesIndex = speciesIndex;
        this.herd = herd;
        this.xFp = xFp;
        this.yFp = yFp;
        this.hp = species.hp();
        this.anchorXFp = xFp;
        this.anchorYFp = yFp;
    }

    public int id() {
        return id;
    }

    public CreatureData.Species species() {
        return species;
    }

    public int speciesIndex() {
        return speciesIndex;
    }

    public Herd herd() {
        return herd;
    }

    public int xFp() {
        return xFp;
    }

    public int yFp() {
        return yFp;
    }

    public int centreXPx() {
        return Fixed.px(xFp) + species.collisionBox().x() + species.collisionBox().w() / 2;
    }

    public int centreYPx() {
        return Fixed.px(yFp) + species.collisionBox().y() + species.collisionBox().h() / 2;
    }

    public int dirX() {
        return dirX;
    }

    public int dirY() {
        return dirY;
    }

    /** Which way the sprite faces: 1 right, -1 left. Survives vertical movement. */
    public int faceX() {
        return faceX;
    }

    public int hp() {
        return hp;
    }

    public Mode mode() {
        return mode;
    }

    /** A puff still forming (§12.8). */
    public boolean appearing() {
        return mode == Mode.APPEARING;
    }

    /** The palette index it is drawn in, or -1 for its own colours (§12.8). */
    public int tint() {
        return tint;
    }

    public boolean alive() {
        return mode == Mode.ALIVE;
    }

    public int modeTick() {
        return modeTick;
    }

    /** Ticks left of the hit flash after a non-lethal sabre hit. */
    public int hurtTicks() {
        return hurtTicks;
    }

    /** Consecutive ticks of actual movement: drives the walk animation. */
    public int moveTicks() {
        return moveTicks;
    }

    public int timer() {
        return timer;
    }

    public int phase() {
        return phase;
    }

    public int aux() {
        return aux;
    }

    public int anchorXFp() {
        return anchorXFp;
    }

    public int anchorYFp() {
        return anchorYFp;
    }

    // ---------------------------------------------------------------- driven by behaviours

    public void direction(int dx, int dy) {
        dirX = dx;
        dirY = dy;
        face(dx);
    }

    public void face(int dx) {
        if (dx != 0) {
            faceX = dx;
        }
    }

    public void timer(int value) {
        timer = value;
    }

    public void phase(int value) {
        phase = value;
    }

    public void aux(int value) {
        aux = value;
    }

    public void anchor(int xFixed, int yFixed) {
        anchorXFp = xFixed;
        anchorYFp = yFixed;
    }

    /** Moves without collision. For behaviours re-seating themselves on their own anchor, never for travel. */
    public void place(int xFixed, int yFixed) {
        xFp = xFixed;
        yFp = yFixed;
    }
}
