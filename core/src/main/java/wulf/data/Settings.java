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
        Boolean easy,
        Boolean fullscreen) {

    public static final int SCHEMA_VERSION = 1;

    public Settings {
        features = Map.copyOf(features);
        easy = easy != null && easy;
        fullscreen = fullscreen != null && fullscreen;
    }

    /** Windowed (§5.3): what a settings file from before the full-screen option means. */
    public Settings(int schemaVersion, int scale, Audio audio, String inputProfile, Crt crt,
                    Map<String, Boolean> features, Boolean easy) {
        this(schemaVersion, scale, audio, inputProfile, crt, features, easy, false);
    }

    /** Hard, the original's game (§17.8): what a settings file from before Easy mode means. */
    public Settings(int schemaVersion, int scale, Audio audio, String inputProfile, Crt crt,
                    Map<String, Boolean> features) {
        this(schemaVersion, scale, audio, inputProfile, crt, features, false, false);
    }

    /** The retro switches, as the options page sets them (§17.9). */
    public Settings withCrt(Crt value) {
        return new Settings(schemaVersion, scale, audio, inputProfile, value, features, easy, fullscreen);
    }

    /** Easy mode or Hard, as chosen on the title (§17.8). */
    public Settings withEasy(boolean on) {
        return new Settings(schemaVersion, scale, audio, inputProfile, crt, features, on, fullscreen);
    }

    public record Audio(boolean enabled, int volumePercent) {
    }

    public record Crt(boolean scanlines, boolean glow, boolean attributeClash) {
    }

    public Settings withInputProfile(String profile) {
        return new Settings(schemaVersion, scale, audio, profile, crt, features, easy, fullscreen);
    }

    public Settings withAudio(boolean enabled, int volumePercent) {
        return new Settings(schemaVersion, scale, new Audio(enabled, volumePercent), inputProfile, crt, features, easy, fullscreen);
    }

    public Settings withScale(int newScale) {
        return new Settings(schemaVersion, newScale, audio, inputProfile, crt, features, easy, fullscreen);
    }

    /** Over the whole screen, or in a window (§5.3, chosen on the options page, §17.9). */
    public Settings withFullscreen(boolean on) {
        return new Settings(schemaVersion, scale, audio, inputProfile, crt, features, easy, on);
    }

    public Settings withFeature(String name, boolean on) {
        Map<String, Boolean> merged = new LinkedHashMap<>(features);
        merged.put(name, on);
        return new Settings(schemaVersion, scale, audio, inputProfile, crt, merged, easy, fullscreen);
    }
}
