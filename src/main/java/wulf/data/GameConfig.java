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

    /** §14.4: in front of the Keeper, a push of {@code keeperNudgePx} instead of a death. */
    public record Exit(int keeperNudgeZonePx, int keeperStepAsideTicks, int keeperNudgePx) {
    }

    /**
     * This config with the player's feature choices laid over it (§21.3). Only
     * flags this build knows are taken: a stale name in settings is ignored
     * rather than fatal, because the player DB outlives any one build.
     */
    public GameConfig withFeatures(Map<String, Boolean> overrides) {
        if (overrides.isEmpty()) {
            return this;
        }
        Map<String, Boolean> merged = new java.util.LinkedHashMap<>(features);
        for (Map.Entry<String, Boolean> e : overrides.entrySet()) {
            if (merged.containsKey(e.getKey())) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        return new GameConfig(schemaVersion, tickHz, maxCatchupTicks, transition, difficulty, exit, merged);
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
