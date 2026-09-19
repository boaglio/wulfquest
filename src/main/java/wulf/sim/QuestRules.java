package wulf.sim;

import java.util.List;
import wulf.data.CreatureData;
import wulf.data.GuardianData;
import wulf.data.LandmarksData;
import wulf.data.WulfData;

/**
 * Everything the simulation needs to run the quest (AGENTS.md §14): the landmarks,
 * each lair's guardian as a species (in {@code landmarks.lairs()} order), the Keeper,
 * and the numbers from {@code game.json} and {@code loot.json} that score and pace it.
 */
public record QuestRules(
        boolean enabled,
        LandmarksData landmarks,
        List<CreatureData.Species> guardianSpecies,
        CreatureData.Species keeperSpecies,
        GuardianData.Orbit orbit,
        int stepAsidePx,
        int stepAsideTicks,
        int nudgeZonePx,
        int nudgePx,
        int pieceScore,
        int escapeBonus,
        int timeBonusMax,
        int timeBonusTicksDivisor,
        int lifeRemainingBonus,
        boolean caveHints) {

    /** No quest: M5's jungle, and most tests. */
    public static final QuestRules NONE = new QuestRules(false, LandmarksData.NONE, List.of(), WulfData.NONE.asSpecies(),
            new GuardianData.Orbit(8, 8, 0, 1, 0, 0, 0), 0, 1, 0, 0, 0, 0, 0, 1, 0, false);

    public QuestRules {
        guardianSpecies = List.copyOf(guardianSpecies);
    }
}
