package wulf.data;

import java.util.List;
import java.util.Map;

/**
 * Bound view of {@code data/audio/music.json} (AGENTS.md §18.3). Original
 * compositions only, monophonic, square wave — and none of them plays during
 * {@code PLAYING}: in the jungle there is only the jungle.
 */
public record MusicData(int schemaVersion, Map<String, Tune> tunes) {

    /** The three tunes §18.3 names. */
    public static final String TITLE = "title";
    public static final String WIN = "win";
    public static final String GAME_OVER = "game_over";

    /** A rest, which is how silence is written in a note list. */
    public static final String REST = "R";

    public MusicData {
        tunes = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(tunes));
    }

    public record Tune(int bpm, boolean loop, List<Note> notes) {
        public Tune {
            notes = List.copyOf(notes);
        }

        public int totalMs() {
            int total = 0;
            for (Note n : notes) {
                total += n.ms();
            }
            return total;
        }
    }

    public record Note(String note, int octave, int ms) {
        public boolean rest() {
            return REST.equals(note);
        }
    }

    public Tune tune(String id) {
        Tune t = tunes.get(id);
        if (t == null) {
            throw new IllegalStateException("no tune called '" + id + "' in music.json");
        }
        return t;
    }
}
