package wulf.data;

import java.util.List;

/**
 * {@code quicksave.json} in the player database (AGENTS.md §21.4, [NEW]): one game kept as
 * what made it — its seed, where it started, and every tick's input — plus the state hash
 * it reached. Loading replays it; the hash says whether it came out the same.
 *
 * @param contentHash the content database's hash when saved: another version's rules may not replay it
 * @param practiced   whether the game was already a practice run (§17.6), which is in the state hash
 * @param input       run-length {@code [ticks, bits]} pairs, as in a replay (§22.6)
 * @param newGamePlus which job the game is (§14.9); absent, as in a save from before them, the game itself
 * @param easy        whether the game is in Easy mode (§17.8); absent, as in a save from before it, Hard
 * @param date        when it was saved; with {@code quarters}, {@code score} and {@code room}, what the
 *                    slot list shows without replaying it (§21.4) — absent in a save from before slots
 */
public record QuickSaveData(int schemaVersion, String contentHash, long seed, String startRoom, boolean dev,
                            boolean practiced, int ticks, List<int[]> input, String stateHash, Integer newGamePlus,
                            Boolean easy, String date, Integer quarters, Long score, String room) {

    public static final int SCHEMA_VERSION = 1;

    public QuickSaveData {
        input = List.copyOf(input);
        newGamePlus = newGamePlus == null ? 0 : newGamePlus;
        easy = easy != null && easy;
        quarters = quarters == null ? 0 : quarters;
        score = score == null ? 0L : score;
    }
}
