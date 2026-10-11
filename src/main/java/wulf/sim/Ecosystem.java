package wulf.sim;

import wulf.data.CreatureData;
import wulf.data.FlameData;
import wulf.data.OrchidData;

/**
 * What lives in the jungle and how rooms fill with it (AGENTS.md §12, §13): the
 * roster, the populator, the score for a first visit to a room (§16.3), the Wulf, and
 * the quest (§14), the orchids (§15), and the flame (§12.7).
 */
public record Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                        QuestRules quest, OrchidData orchids, FlameData flame) {

    /** No creatures, no Wulf, no quest: M3's empty jungle. */
    public static final Ecosystem NONE = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 0, WulfRules.NONE,
            QuestRules.NONE, OrchidData.NONE, FlameData.NONE);

    /** Everything but the flame (§12.7): a jungle that lets you stand still, as most tests want. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                     QuestRules quest, OrchidData orchids) {
        this(creatures, populator, roomFirstVisitScore, wulf, quest, orchids, FlameData.NONE);
    }

    /** Creatures without a Wulf or a quest. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore) {
        this(creatures, populator, roomFirstVisitScore, WulfRules.NONE, QuestRules.NONE, OrchidData.NONE);
    }

    /** Creatures and a Wulf, without a quest. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf) {
        this(creatures, populator, roomFirstVisitScore, wulf, QuestRules.NONE, OrchidData.NONE);
    }

    /** Creatures, a Wulf and a quest, in a jungle that does not flower. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                     QuestRules quest) {
        this(creatures, populator, roomFirstVisitScore, wulf, quest, OrchidData.NONE);
    }
}
