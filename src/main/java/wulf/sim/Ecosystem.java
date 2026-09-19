package wulf.sim;

import wulf.data.CreatureData;

/**
 * What lives in the jungle and how rooms fill with it (AGENTS.md §12, §13): the
 * roster, the populator, the score for a first visit to a room (§16.3), the Wulf, and
 * the quest (§14).
 */
public record Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                        QuestRules quest) {

    /** No creatures, no Wulf, no quest: M3's empty jungle. */
    public static final Ecosystem NONE = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 0, WulfRules.NONE,
            QuestRules.NONE);

    /** Creatures without a Wulf or a quest. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore) {
        this(creatures, populator, roomFirstVisitScore, WulfRules.NONE, QuestRules.NONE);
    }

    /** Creatures and a Wulf, without a quest. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf) {
        this(creatures, populator, roomFirstVisitScore, wulf, QuestRules.NONE);
    }
}
