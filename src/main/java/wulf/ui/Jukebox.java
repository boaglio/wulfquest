package wulf.ui;

/**
 * What the shell can ask for out loud (AGENTS.md §17, §18.3): a tune per screen,
 * and a click when a key does something.
 *
 * <p>The shell holds one of these rather than a mixer, so the whole state
 * machine still runs — and is still tested — with no sound card anywhere.
 */
public interface Jukebox {

    /** A shell that makes no noise at all. */
    Jukebox SILENT = new Jukebox() {
        @Override
        public void tune(String id) {
            // silence
        }

        @Override
        public void sfx(String id) {
            // silence
        }
    };

    /** Plays a tune from {@code music.json}, or stops on null. Asking twice for the same one changes nothing. */
    void tune(String id);

    /** Plays one effect from {@code sfx.json}. */
    void sfx(String id);

    /** The player changed the sound settings (§21.3). */
    default void settings(boolean enabled, int volumePercent) {
        // a jukebox with no volume has nothing to set
    }
}
