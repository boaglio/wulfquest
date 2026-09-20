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
        Map<String, Boolean> features) {

    public static final int SCHEMA_VERSION = 1;

    public Settings {
        features = Map.copyOf(features);
    }

    public record Audio(boolean enabled, int volumePercent) {
    }

    public record Crt(boolean scanlines, boolean glow, boolean attributeClash) {
    }

    public Settings withInputProfile(String profile) {
        return new Settings(schemaVersion, scale, audio, profile, crt, features);
    }

    public Settings withAudio(boolean enabled, int volumePercent) {
        return new Settings(schemaVersion, scale, new Audio(enabled, volumePercent), inputProfile, crt, features);
    }

    public Settings withScale(int newScale) {
        return new Settings(schemaVersion, newScale, audio, inputProfile, crt, features);
    }

    public Settings withFeature(String name, boolean on) {
        Map<String, Boolean> merged = new LinkedHashMap<>(features);
        merged.put(name, on);
        return new Settings(schemaVersion, scale, audio, inputProfile, crt, merged);
    }
}
