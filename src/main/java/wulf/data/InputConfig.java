package wulf.data;

import java.util.List;
import java.util.Map;

/** Bound view of {@code data/config/input.json} (AGENTS.md §19.1). */
public record InputConfig(int schemaVersion, String active, Map<String, Profile> profiles, Gamepad gamepad) {

    public InputConfig {
        // Insertion order, not Map.copyOf's: the keys page numbers the profiles as
        // input.json lists them, so "1" must always be the first one in the file (§19.1).
        profiles = java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(profiles));
    }

    /** Key names per action. */
    public record Profile(
            List<String> up,
            List<String> down,
            List<String> left,
            List<String> right,
            List<String> fire,
            List<String> pause,
            List<String> quit,
            List<String> devKill,
            List<String> devMask,
            List<String> devWulf) {

        public Profile {
            up = List.copyOf(up);
            down = List.copyOf(down);
            left = List.copyOf(left);
            right = List.copyOf(right);
            fire = List.copyOf(fire);
            pause = List.copyOf(pause);
            quit = List.copyOf(quit);
            devKill = List.copyOf(devKill);
            devMask = List.copyOf(devMask);
            devWulf = List.copyOf(devWulf);
        }
    }

    /** The same profiles with a different one chosen — what the keys page does (§19.1). */
    public InputConfig withActive(String profile) {
        return profiles.containsKey(profile) ? new InputConfig(schemaVersion, profile, profiles, gamepad) : this;
    }

    /** Integer percent, not a fraction: nothing that reaches the game is a float (§6.2). */
    public record Gamepad(boolean enabled, int deadzonePercent, boolean dpadAsDirections) {
    }
}
