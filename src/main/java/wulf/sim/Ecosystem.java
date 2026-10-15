package wulf.sim;

import wulf.data.CreatureData;
import wulf.data.FlameData;
import wulf.data.GameConfig;
import wulf.data.OrchidData;

/**
 * What lives in the jungle and how rooms fill with it (AGENTS.md §12, §13): the
 * roster, the populator, the score for a first visit to a room (§16.3), the Wulf, and
 * the quest (§14), the orchids (§15), the flame (§12.7), and the original's swarm (§12.8) and big beasts (§12.9).
 */
public record Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                        QuestRules quest, OrchidData orchids, FlameData flame,
                        SwarmRules swarm, BeastRules beasts, OrchidSpots orchidSpots) {

    /** The same jungle with another quest: the game's own lairs, once it has hidden the amulet (§14.2). */
    public Ecosystem withQuest(QuestRules q) {
        return new Ecosystem(creatures, populator, roomFirstVisitScore, wulf, q, orchids, flame, swarm, beasts, orchidSpots);
    }

    /** The same jungle as job {@code n} (§14.9): fewer pieces, and a harder jungle. */
    public Ecosystem atLevel(int n, GameConfig.Level rules) {
        return withQuest(quest.atLevel(n, rules));
    }

    /**
     * The same jungle in Easy mode (§17.8): fewer small creatures at once, the nastier kinds
     * slower to come, the big beasts slower, no flame if it says so; the quest carries the rest — the lives, the
     * Wulf's pace, the glass and the shrines.
     */
    public Ecosystem easy(GameConfig.Easy e) {
        SwarmRules s = swarm;
        SwarmRules gentler = new SwarmRules(s.on(), Math.min(s.roomMax(), e.swarmRoomMax()), s.slots(), s.everyTicks(),
                s.appearTicks(), s.lingerTicks(), s.quietAfterGettingUpTicks(), s.area(), s.playerClearancePx(), s.pick(),
                s.open(), Math.min(s.openPerQuarter(), e.swarmOpenPerQuarter()), s.fold(), s.tints(), s.appearScores(),
                s.facingRightBonus());
        return new Ecosystem(creatures, populator, roomFirstVisitScore, wulf, quest.withEasy(e), orchids,
                e.flame() ? flame : FlameData.NONE, gentler, beasts.slower(e.beastSpeedPercent()), orchidSpots);
    }

    /** No creatures, no Wulf, no quest: M3's empty jungle. */
    public static final Ecosystem NONE = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 0, WulfRules.NONE,
            QuestRules.NONE, OrchidData.NONE, FlameData.NONE, SwarmRules.NONE,
            BeastRules.NONE, OrchidSpots.NONE);

    /** The original's jungle, its orchids finding their own anchors (§15.1). */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                     QuestRules quest, OrchidData orchids, FlameData flame, SwarmRules swarm, BeastRules beasts) {
        this(creatures, populator, roomFirstVisitScore, wulf, quest, orchids, flame, swarm, beasts, OrchidSpots.NONE);
    }

    /** Everything but the flame (§12.7): a jungle that lets you stand still, as most tests want. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                     QuestRules quest, OrchidData orchids) {
        this(creatures, populator, roomFirstVisitScore, wulf, quest, orchids, FlameData.NONE);
    }

    /** Everything but the original's swarm (§12.8): rooms filled on entry by {@code populator}. */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                     QuestRules quest, OrchidData orchids, FlameData flame) {
        this(creatures, populator, roomFirstVisitScore, wulf, quest, orchids, flame, SwarmRules.NONE);
    }

    /** The original's swarm without its big beasts (§12.8). */
    public Ecosystem(CreatureData creatures, RoomPopulator populator, int roomFirstVisitScore, WulfRules wulf,
                     QuestRules quest, OrchidData orchids, FlameData flame, SwarmRules swarm) {
        this(creatures, populator, roomFirstVisitScore, wulf, quest, orchids, flame, swarm, BeastRules.NONE);
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
