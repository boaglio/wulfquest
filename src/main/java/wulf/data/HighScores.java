package wulf.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The hi-score table (AGENTS.md §21.2): top {@code size}, sorted descending by
 * score and then ascending by ticks, so the faster of two equal runs is the
 * better one.
 *
 * <p>The table is a value: {@link #with} returns a new one. How many rows it
 * keeps comes from {@code shell.json}, never from here.
 */
public record HighScores(int schemaVersion, List<Entry> entries) {

    public static final int SCHEMA_VERSION = 1;

    /** Best first, then quickest. */
    private static final Comparator<Entry> RANKING =
            Comparator.comparingLong(Entry::score).reversed().thenComparingLong(Entry::ticks);

    public HighScores {
        entries = List.copyOf(entries);
    }

    public record Entry(String name, long score, int pieces, long ticks, String date) {
    }

    public static HighScores empty() {
        return new HighScores(SCHEMA_VERSION, List.of());
    }

    /** Already ranked, and no longer than {@code size}. */
    public HighScores ranked(int size) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(RANKING);
        return new HighScores(SCHEMA_VERSION, sorted.subList(0, Math.min(size, sorted.size())));
    }

    /**
     * Whether a run earns a place — true while the table has room, and otherwise
     * only if it beats the last row. A zero never qualifies: pressing fire and
     * dying is not an achievement.
     */
    public boolean qualifies(long score, long ticks, int size) {
        if (score <= 0) {
            return false;
        }
        List<Entry> ranked = ranked(size).entries();
        if (ranked.size() < size) {
            return true;
        }
        Entry last = ranked.get(ranked.size() - 1);
        return RANKING.compare(new Entry("", score, 0, ticks, ""), last) < 0;
    }

    /** This table with one more run in it, re-ranked and trimmed. */
    public HighScores with(Entry entry, int size) {
        List<Entry> all = new ArrayList<>(entries);
        all.add(entry);
        return new HighScores(SCHEMA_VERSION, all).ranked(size);
    }

    /** The row a run would take, 0-based, or -1 if it would not make the table. */
    public int placeOf(Entry entry, int size) {
        return with(entry, size).entries().indexOf(entry);
    }

    /** The best score on the table, or 0 when it is empty — the panel's {@code HI}. */
    public long best() {
        long best = 0;
        for (Entry e : entries) {
            best = Math.max(best, e.score());
        }
        return best;
    }
}
