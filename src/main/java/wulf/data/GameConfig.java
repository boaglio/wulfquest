package wulf.data;

import java.util.Map;

/** Bound view of {@code data/config/game.json} (AGENTS.md §6.1, §7.5, §12.6, §14.4). */
public record GameConfig(
        int schemaVersion,
        int tickHz,
        int maxCatchupTicks,
        Transition transition,
        Difficulty difficulty,
        Exit exit,
        Map<String, Boolean> features) {

    public GameConfig {
        features = Map.copyOf(features);
    }

    public record Transition(int freezeTicks, int wulfArrivalDelayTicks) {
    }

    public record Difficulty(java.util.List<Integer> budgetBonusByPieces, int maxRoomBudget) {
        public Difficulty {
            budgetBonusByPieces = java.util.List.copyOf(budgetBonusByPieces);
        }
    }

    public record Exit(int keeperNudgeZonePx, int keeperStepAsideTicks) {
    }

    /** A feature flag (AGENTS.md §1.1); unknown names are a programmer error. */
    public boolean feature(String name) {
        Boolean b = features.get(name);
        if (b == null) {
            throw new IllegalStateException("unknown feature flag '" + name + "'");
        }
        return b;
    }

    public long tickNanos() {
        return 1_000_000_000L / tickHz;
    }
}
