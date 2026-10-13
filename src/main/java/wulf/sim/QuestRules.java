package wulf.sim;

import java.util.List;
import wulf.data.CreatureData;
import wulf.data.GameConfig;
import wulf.data.GuardianData;
import wulf.data.LandmarksData;
import wulf.data.WulfData;

/**
 * Everything the simulation needs to run the quest (AGENTS.md §14): the landmarks,
 * each lair's guardian as a species (in {@code landmarks.lairs()} order), the Keeper,
 * the numbers from {@code game.json} and {@code loot.json} that score and pace it, and the
 * treasures scattered through the jungle (§16.4), and which job this game is (§14.9).
 *
 * @param level      0 for the game itself; 1, 2… for the jobs Mr Jones has after it (§14.9)
 * @param levelRules what that job changes: how many pieces, and how much harder the jungle is
 * @param easy       Easy mode's rules (§17.8), or null for Hard: the game as the original plays it
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
        Treasures treasures,
        int level,
        GameConfig.Level levelRules,
        GameConfig.Easy easy) {

    /** No quest: M5's jungle, and most tests. */
    public static final QuestRules NONE = new QuestRules(false, LandmarksData.NONE, List.of(), WulfData.NONE.asSpecies(),
            new GuardianData.Orbit(8, 8, 0, 1, 0, 0, 0), 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, false, Treasures.NONE);

    /** The game itself (§14.9): every lair its own quarter. */
    public QuestRules(boolean enabled, LandmarksData landmarks, List<CreatureData.Species> guardianSpecies,
                      CreatureData.Species keeperSpecies, GuardianData.Orbit orbit, int stepAsideFp, int asideMinPx,
                      int asideMaxPx, int nudgeZonePx, int nudgePx, int pieceScore, int escapeBonus, int timeBonusMax,
                      int timeBonusTicksDivisor, int lifeRemainingBonus, boolean caveHints, Treasures treasures) {
        this(enabled, landmarks, guardianSpecies, keeperSpecies, orbit, stepAsideFp, asideMinPx, asideMaxPx, nudgeZonePx,
                nudgePx, pieceScore, escapeBonus, timeBonusMax, timeBonusTicksDivisor, lifeRemainingBonus, caveHints,
                treasures, 0, GameConfig.Level.ORIGINAL, null);
    }

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
                caveHints, treasures, level, levelRules, easy);
    }

    /**
     * §14.9: only {@code kept} of the lairs, each with its own guardian, the {@code j}-th holding a
     * piece that fills {@code masks[j]}'s slots of the panel.
     */
    public QuestRules keeping(int[] kept, int[] masks) {
        List<CreatureData.Species> some = new java.util.ArrayList<>(kept.length);
        for (int k : kept) {
            some.add(guardianSpecies.get(k));
        }
        return new QuestRules(enabled, landmarks.keeping(kept, masks), some, keeperSpecies, orbit, stepAsideFp,
                asideMinPx, asideMaxPx, nudgeZonePx, nudgePx, pieceScore, escapeBonus, timeBonusMax,
                timeBonusTicksDivisor, lifeRemainingBonus, caveHints, treasures, level, levelRules, easy);
    }

    /**
     * The same rules as job {@code n} (§14.9). Before the amulet is hidden only: the game keeps
     * that job's share of the lairs when it hides it.
     */
    public QuestRules atLevel(int n, GameConfig.Level rules) {
        return new QuestRules(enabled, landmarks, guardianSpecies, keeperSpecies, orbit, stepAsideFp, asideMinPx,
                asideMaxPx, nudgeZonePx, nudgePx, pieceScore, escapeBonus, timeBonusMax, timeBonusTicksDivisor,
                lifeRemainingBonus, caveHints, treasures, n, rules, easy);
    }

    /** The same rules in Easy mode (§17.8); null for Hard. */
    public QuestRules withEasy(GameConfig.Easy e) {
        return new QuestRules(enabled, landmarks, guardianSpecies, keeperSpecies, orbit, stepAsideFp, asideMinPx,
                asideMaxPx, nudgeZonePx, nudgePx, pieceScore, escapeBonus, timeBonusMax, timeBonusTicksDivisor,
                lifeRemainingBonus, caveHints, treasures, level, levelRules, e);
    }

    /** Whether this is Easy mode (§17.8). */
    public boolean isEasy() {
        return easy != null;
    }
}
