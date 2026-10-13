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
        NewGamePlus newGamePlus,
        Easy easy,
        int continues,
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

    /**
     * §14.4: in front of the Keeper, within {@code keeperNudgeZonePx}, a push of {@code keeperNudgePx}
     * instead of a death. The original has none ($9F2F): 0, the default, turns it off.
     */
    public record Exit(int keeperNudgeZonePx, int keeperNudgePx) {
    }

    /**
     * §14.9 [NEW]: the jobs Mr Jones has for a ranger who has won. Level 0 is the game itself,
     * the original's; each level after it is unlocked by winning the one before.
     */
    public record NewGamePlus(java.util.List<Level> levels) {
        public NewGamePlus {
            levels = java.util.List.copyOf(levels);
        }

        public Level level(int n) {
            return levels.get(n);
        }
    }

    /**
     * Easy mode (§17.8, {@code [NEW]}): the game the player chooses on the title when the
     * original's — Hard — is too much. The same jungle and quest; less that kills.
     *
     * @param livesStart          lives in reserve at the start
     * @param swarmRoomMax        small creatures in a room at once ({@code jungle.json → swarm.roomMax})
     * @param swarmOpenPerQuarter how many more kinds each quarter held lets in ({@code swarm.pick.openPerQuarter})
     * @param wulfSpeedPercent    the Wulf's pace against its own, on top of a later job's
     * @param flame               whether the flame comes for a player who lingers (§12.7)
     * @param showWayTicks        how long a looking glass shows the way (§14.10)
     * @param hintsOncePerLife    whether a shrine names the way only once a room a life (§14.5)
     */
    public record Easy(int livesStart, int swarmRoomMax, int swarmOpenPerQuarter, int wulfSpeedPercent, boolean flame,
                       int showWayTicks, boolean hintsOncePerLife) {
    }

    /**
     * One job (§14.9).
     *
     * @param pieces           one entry per piece hidden, the panel slots (0..3) it fills: four
     *                         quarters, two halves, or the amulet whole
     * @param rampHeadStart    quarters counted as held, on top of those that are, when the jungle
     *                         decides which creatures to send and how often the Wulf comes
     * @param wulfSpeedPercent the Wulf's pace, against its own
     */
    public record Level(java.util.List<java.util.List<Integer>> pieces, int rampHeadStart, int wulfSpeedPercent) {
        public Level {
            pieces = pieces.stream().map(java.util.List::copyOf).toList();
        }

        /** The original's game: every lair its own quarter, nothing added. */
        public static final Level ORIGINAL = new Level(java.util.List.of(), 0, 100);

        /** Each piece's slots as a mask: bit {@code slot} set for every slot it fills. */
        public int slotMask(int piece) {
            int mask = 0;
            for (int slot : pieces.get(piece)) {
                mask |= 1 << slot;
            }
            return mask;
        }
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
        return new GameConfig(schemaVersion, tickHz, maxCatchupTicks, transition, difficulty, exit, newGamePlus, easy,
                continues, merged);
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
