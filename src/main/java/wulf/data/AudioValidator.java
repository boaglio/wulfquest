package wulf.data;

import java.util.List;

/**
 * Semantic checks for the audio database beyond its schemas (AGENTS.md §20.6).
 *
 * <p>The names the code announces must exist, the priorities must actually
 * order the sounds §18.2 says they order, and only a sound meant to repeat may
 * say it loops — a looping fanfare would never stop.
 */
public final class AudioValidator {

    private static final String SFX_SOURCE = "data/audio/sfx.json";
    private static final String MUSIC_SOURCE = "data/audio/music.json";

    /** §18.2's stealing order, loudest first. Each must outrank the next. */
    private static final List<String> PRIORITY_ORDER = List.of("wulf_howl", "player_die", "amulet_piece");

    /** §18.2: the growl is the one sound that holds; everything else is an event. */
    private static final String THE_LOOPING_ONE = "wulf_growl";

    private AudioValidator() {
    }

    public static void check(SfxData sfx, MusicData music) {
        for (String id : PRIORITY_ORDER) {
            if (!sfx.has(id)) {
                throw new DataException(SFX_SOURCE, "/sfx", "has no '" + id + "', which §18.2 names");
            }
        }
        for (int i = 0; i + 1 < PRIORITY_ORDER.size(); i++) {
            SfxData.Effect louder = sfx.effect(PRIORITY_ORDER.get(i));
            SfxData.Effect quieter = sfx.effect(PRIORITY_ORDER.get(i + 1));
            if (louder.priority() <= quieter.priority()) {
                throw new DataException(SFX_SOURCE, "/sfx/" + PRIORITY_ORDER.get(i) + "/priority",
                        "is " + louder.priority() + ", which does not outrank " + PRIORITY_ORDER.get(i + 1)
                                + " at " + quieter.priority() + " (§18.2)");
            }
        }
        for (var entry : sfx.sfx().entrySet()) {
            if (entry.getValue().loop() && !THE_LOOPING_ONE.equals(entry.getKey())) {
                throw new DataException(SFX_SOURCE, "/sfx/" + entry.getKey() + "/loop",
                        "is true, but '" + THE_LOOPING_ONE + "' is the only sound that holds (§18.2)");
            }
            for (int i = 0; i < entry.getValue().steps().size(); i++) {
                SfxData.Step step = entry.getValue().steps().get(i);
                if (!SfxData.Step.SQUARE.equals(step.shape()) && !SfxData.Step.NOISE.equals(step.shape())) {
                    throw new DataException(SFX_SOURCE, "/sfx/" + entry.getKey() + "/steps/" + i + "/shape",
                            "is '" + step.shape() + "'; §18.1 has square waves and noise, and nothing else");
                }
            }
        }
        if (sfx.channels() > 2) {
            throw new DataException(SFX_SOURCE, "/channels",
                    "is " + sfx.channels() + "; §18.1 allows two at most");
        }
        for (String id : List.of(MusicData.TITLE, MusicData.WIN, MusicData.GAME_OVER)) {
            if (!music.tunes().containsKey(id)) {
                throw new DataException(MUSIC_SOURCE, "/tunes", "has no '" + id + "', which §18.3 names");
            }
        }
        // §18.3: the title theme loops, and the two that mark an ending do not.
        if (!music.tune(MusicData.TITLE).loop()) {
            throw new DataException(MUSIC_SOURCE, "/tunes/title/loop", "is false; the title theme loops (§18.3)");
        }
        for (String id : List.of(MusicData.WIN, MusicData.GAME_OVER)) {
            if (music.tune(id).loop()) {
                throw new DataException(MUSIC_SOURCE, "/tunes/" + id + "/loop",
                        "is true; a tune that marks an ending has to end (§18.3)");
            }
        }
    }
}
