package wulf.sim;

import wulf.data.CreatureData;

/**
 * What lives in the jungle and how rooms fill with it (AGENTS.md §12): the
 * roster, the populator, and the score for a first visit to a room (§16.3).
 */
public record Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore) {

    /** No creatures: M3's empty jungle. */
    public static final Ecosystem NONE = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 0);
}
