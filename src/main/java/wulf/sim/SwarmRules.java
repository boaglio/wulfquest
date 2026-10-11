package wulf.sim;

import wulf.data.CreatureData;

/**
 * Everything the simulation needs to bring the original's small creatures (AGENTS.md §12.8),
 * resolved: the pick table as roster indices and the tints as palette indices.
 *
 * @param pick  {@code jungle.json → swarm.pick.table}, each entry an index into the roster
 * @param tints palette indices, bright half
 */
public record SwarmRules(boolean on, int roomMax, int slots, int everyTicks, int appearTicks, int lingerTicks,
                         int quietAfterGettingUpTicks, CreatureData.Box area, int playerClearancePx, int[] pick,
                         int open, int openPerQuarter, int fold, int[] tints, int[] appearScores, int facingRightBonus) {

    /** No swarm: the invented jungle, whose rooms are filled on entry (§12.6), and most tests. */
    public static final SwarmRules NONE = new SwarmRules(false, 0, 0, 1, 1, 0, 0, new CreatureData.Box(0, 0, 1, 1), 0,
            new int[0], 1, 0, 1, new int[0], new int[] {0}, 0);

    public SwarmRules {
        pick = pick.clone();
        tints = tints.clone();
        appearScores = appearScores.clone();
    }

    /** {@code $A3EC}: which roster entry a roll of the table gives, with {@code quarters} of the amulet held. */
    public int pick(int roll, int quarters) {
        int i = roll < open + openPerQuarter * quarters ? roll : roll % fold;
        return pick[i];
    }

    /** What a puff struck while it forms is worth, {@code ticks} into forming. */
    public int appearScore(int ticks) {
        int frame = ticks / Math.max(1, everyTicks);
        return appearScores[Math.min(frame, appearScores.length - 1)];
    }
}
