package wulf.sim;

import wulf.engine.Rng;

/** How a {@link RoomPopulator} places creatures into the room being entered. */
public interface Spawner {

    /**
     * Places a creature with its feet at a room-local pixel position, if its box fits
     * the room and, unless it ignores scenery, the scenery.
     *
     * @param rng seeds the creature's own starting state, so a room replays exactly
     * @return whether it was placed
     */
    boolean spawn(String speciesId, int localXPx, int localYPx, Herd herd, Rng rng);

    Herd newHerd();

    /** Where the player entered the room, room-local pixels: spawns keep their distance (§12.2). */
    int entryLocalXPx();

    int entryLocalYPx();

    /** Amulet pieces held, for the difficulty ramp (§12.6). */
    int amuletPieces();

    /**
     * Whether the player could get to a creature placed here without leaving the room
     * (§12.6). Always true for species that ignore scenery.
     */
    boolean reachable(String speciesId, int localXPx, int localYPx);

    /** Lets a species' behaviour move a proposed spawn height: spiders hang from the top of the room. */
    int preferredLocalYPx(String speciesId, int proposedLocalYPx);
}
