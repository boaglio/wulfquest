package wulf.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lifetime counters (AGENTS.md §21.5). Local, and local only: there is no
 * network code in this project and never will be.
 *
 * <p>A value, like every other player-DB record: each {@code count} returns a
 * new one, so nothing can be mutated behind the shell's back.
 */
public record Stats(
        int schemaVersion,
        long gamesPlayed,
        long escapes,
        long deaths,
        long roomsVisited,
        long amuletPieces,
        long wulfEncounters,
        long wulfEvaded,
        long bestScore,
        long bestEscapeTicks,
        Map<String, Long> killsBySpecies,
        Map<String, Long> deathsByCause,
        Map<String, Long> orchidsByColour) {

    public static final int SCHEMA_VERSION = 1;

    public Stats {
        // Sorted, so a written file diffs cleanly whatever order the game met things in (§20.9).
        killsBySpecies = Map.copyOf(new TreeMap<>(killsBySpecies));
        deathsByCause = Map.copyOf(new TreeMap<>(deathsByCause));
        orchidsByColour = Map.copyOf(new TreeMap<>(orchidsByColour));
    }

    public static Stats empty() {
        return new Stats(SCHEMA_VERSION, 0, 0, 0, 0, 0, 0, 0, 0, 0, Map.of(), Map.of(), Map.of());
    }

    /** True while nothing has been played: the stats page says so rather than showing ten zeroes. */
    public boolean blank() {
        return gamesPlayed == 0 && roomsVisited == 0 && deaths == 0;
    }

    /**
     * One finished game folded in.
     *
     * @param escaped      whether this run went out through the arch
     * @param ticks        how long the run lasted, counted only when it escaped
     */
    public Stats afterGame(long score, boolean escaped, long ticks, long rooms, long pieces, long deathsThisGame,
                           long encounters, long evaded, Map<String, Long> kills, Map<String, Long> causes,
                           Map<String, Long> orchids) {
        long bestTicks = escaped && (bestEscapeTicks == 0 || ticks < bestEscapeTicks) ? ticks : bestEscapeTicks;
        return new Stats(SCHEMA_VERSION,
                gamesPlayed + 1,
                escapes + (escaped ? 1 : 0),
                deaths + deathsThisGame,
                roomsVisited + rooms,
                amuletPieces + pieces,
                wulfEncounters + encounters,
                wulfEvaded + evaded,
                Math.max(bestScore, score),
                bestTicks,
                merge(killsBySpecies, kills),
                merge(deathsByCause, causes),
                merge(orchidsByColour, orchids));
    }

    private static Map<String, Long> merge(Map<String, Long> into, Map<String, Long> more) {
        Map<String, Long> out = new LinkedHashMap<>(into);
        for (Map.Entry<String, Long> e : more.entrySet()) {
            out.merge(e.getKey(), e.getValue(), Long::sum);
        }
        return out;
    }
}
