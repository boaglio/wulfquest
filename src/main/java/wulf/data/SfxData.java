package wulf.data;

import java.util.Map;

/**
 * Bound view of {@code data/audio/sfx.json} (AGENTS.md §18.2). Every sound in
 * the game is a list of steps described here; no sample file exists, and none
 * ever will (§2.1, §18.1).
 *
 * <p>Volumes and vibrato depth are per-mille integers, not fractions — nothing
 * that reaches the game from the database is a float.
 */
public record SfxData(
        int schemaVersion,
        int sampleRateHz,
        int channels,
        int bufferFrames,
        Cadence cadence,
        Map<String, Effect> sfx) {

    public SfxData {
        // The file's order, so a contact sheet of the sounds is always the same list.
        sfx = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(sfx));
    }

    /** How often a repeating sound repeats while the thing making it keeps happening. */
    public record Cadence(int footstepEveryTicks) {
    }

    /**
     * @param priority §18.2's stealing order: a louder event takes channel 0
     * @param loop     whether it repeats until stopped — only the growl does
     */
    public record Effect(int priority, boolean loop, java.util.List<Step> steps) {
        public Effect {
            steps = java.util.List.copyOf(steps);
        }
    }

    /**
     * @param slideToHz            glides from {@code hz} to here across the step; 0 means no glide
     * @param vibratoDepthPerMille how far the vibrato bends the pitch, per mille
     */
    public record Step(String shape, int hz, int ms, int volPerMille, int slideToHz, int vibratoHz,
                       int vibratoDepthPerMille) {

        public static final String SQUARE = "square";
        public static final String NOISE = "noise";

        public boolean slides() {
            return slideToHz > 0 && slideToHz != hz;
        }

        public boolean vibrates() {
            return vibratoHz > 0 && vibratoDepthPerMille > 0;
        }
    }

    public Effect effect(String id) {
        Effect e = sfx.get(id);
        if (e == null) {
            throw new IllegalStateException("no sound called '" + id + "' in sfx.json");
        }
        return e;
    }

    public boolean has(String id) {
        return sfx.containsKey(id);
    }
}
