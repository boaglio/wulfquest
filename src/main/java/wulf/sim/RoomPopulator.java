package wulf.sim;

import wulf.world.RoomAddress;

/** Fills a room with creatures as the player enters it (AGENTS.md §7.6). */
@FunctionalInterface
public interface RoomPopulator {

    RoomPopulator NONE = (spawner, room, runSeed, visit) -> {
    };

    /**
     * @param visit 0 on the first visit to this room in this game, then 1, 2, ...;
     *              with {@code runSeed} it decides the room's contents, so a replayed
     *              visit gets the same room
     */
    void populate(Spawner spawner, RoomAddress room, long runSeed, int visit);
}
