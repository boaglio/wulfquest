package wulf.sim;

import java.util.List;
import wulf.data.CreatureData;
import wulf.data.GuardianData;
import wulf.data.LandmarksData;
import wulf.data.WulfData;

/**
 * Everything the simulation needs to run the quest (AGENTS.md §14): the landmarks,
 * each lair's guardian as a species (in {@code landmarks.lairs()} order), the Keeper,
 * the numbers from {@code game.json} and {@code loot.json} that score and pace it, and the
 * treasures scattered through the jungle (§16.4).
 */
public record QuestRules(
        boolean enabled,
        LandmarksData landmarks,
        List<CreatureData.Species> guardianSpecies,
        CreatureData.Species keeperSpecies,
        GuardianData.Orbit orbit,
        int stepAsideFp,
        int asideMinPx,
        int asideMaxPx,
        int nudgeZonePx,
        int nudgePx,
        int pieceScore,
        int escapeBonus,
        int timeBonusMax,
        int timeBonusTicksDivisor,
        int lifeRemainingBonus,
        boolean caveHints,
        Treasures treasures) {

    /** No quest: M5's jungle, and most tests. */
    public static final QuestRules NONE = new QuestRules(false, LandmarksData.NONE, List.of(), WulfData.NONE.asSpecies(),
            new GuardianData.Orbit(8, 8, 0, 1, 0, 0, 0), 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, false, Treasures.NONE);

    public QuestRules {
        guardianSpecies = List.copyOf(guardianSpecies);
        if (landmarks.placed() && guardianSpecies.size() == landmarks.lairs().size()) {
            // A lair too tight for the usual circle names its own (§14.2); every other keeps its species' own.
            List<CreatureData.Species> fitted = new java.util.ArrayList<>(guardianSpecies.size());
            for (int i = 0; i < guardianSpecies.size(); i++) {
                Integer r = landmarks.lairs().get(i).orbitPx();
                fitted.add(r == null ? guardianSpecies.get(i) : guardianSpecies.get(i).withParam("orbitRadiusPx", r));
            }
            guardianSpecies = List.copyOf(fitted);
        }
    }

    /** The same rules over other landmarks: the lairs laid out as one amulet set (§14.2). */
    public QuestRules withLandmarks(LandmarksData marks) {
        return new QuestRules(enabled, marks, guardianSpecies, keeperSpecies, orbit, stepAsideFp, asideMinPx, asideMaxPx,
                nudgeZonePx, nudgePx, pieceScore, escapeBonus, timeBonusMax, timeBonusTicksDivisor, lifeRemainingBonus,
                caveHints, treasures);
    }
}
