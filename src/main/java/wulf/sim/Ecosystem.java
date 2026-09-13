package wulf.sim;

import wulf.data.CreatureData;

/**
 * What lives in the jungle and how rooms fill with it (AGENTS.md §12, §13): the
 * roster, the populator, the score for a first visit to a room (§16.3), and the Wulf.
 */
public record Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf) {

    /** No creatures and no Wulf: M3's empty jungle. */
    public static final Ecosystem NONE = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 0, WulfRules.NONE);

    /** Creatures without a Wulf. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore) {
        this(creatures, populator, roomFirstVisitScore, WulfRules.NONE);
    }
}
