package wulf.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The player's choices (AGENTS.md §21.3). Written whenever one changes.
 *
 * <p>Feature flags here override {@code game.json}: the content database ships
 * the defaults, and this is what the player chose.
 */
public record Settings(
        int schemaVersion,
        int scale,
        Audio audio,
        String inputProfile,
        Crt crt,
        Map<String, Boolean> features,
        Boolean easy) {

    public static final int SCHEMA_VERSION = 1;

    public Settings {
        features = Map.copyOf(features);
        easy = easy != null && easy;
    }

    /** Hard, the original's game (§17.8): what a settings file from before Easy mode means. */
    public Settings(int schemaVersion, int scale, Audio audio, String inputProfile, Crt crt,
                    Map<String, Boolean> features) {
        this(schemaVersion, scale, audio, inputProfile, crt, features, false);
    }

    /** The retro switches, as the options page sets them (§17.9). */
    public Settings withCrt(Crt value) {
        return new Settings(schemaVersion, scale, audio, inputProfile, value, features, easy);
    }

    /** Easy mode or Hard, as chosen on the title (§17.8). */
    public Settings withEasy(boolean on) {
        return new Settings(schemaVersion, scale, audio, inputProfile, crt, features, on);
    }

    public record Audio(boolean enabled, int volumePercent) {
    }

    public record Crt(boolean scanlines, boolean glow, boolean attributeClash) {
    }

    public Settings withInputProfile(String profile) {
        return new Settings(schemaVersion, scale, audio, profile, crt, features, easy);
    }

    public Settings withAudio(boolean enabled, int volumePercent) {
        return new Settings(schemaVersion, scale, new Audio(enabled, volumePercent), inputProfile, crt, features, easy);
    }

    public Settings withScale(int newScale) {
        return new Settings(schemaVersion, newScale, audio, inputProfile, crt, features, easy);
    }

    public Settings withFeature(String name, boolean on) {
        Map<String, Boolean> merged = new LinkedHashMap<>(features);
        merged.put(name, on);
        return new Settings(schemaVersion, scale, audio, inputProfile, crt, merged, easy);
    }
}
