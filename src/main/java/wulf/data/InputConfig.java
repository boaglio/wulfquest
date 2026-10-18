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

    /**
     * The gamepad (§19.3). The deadzone is an integer percent, not a fraction: nothing that
     * reaches the game is a float (§6.2).
     */
    public record Gamepad(boolean enabled, int deadzonePercent, boolean dpadAsDirections, Buttons buttons) {
    }

    /**
     * Button names per action (§19.3): {@code fire}, {@code pause} and {@code quit} as the keys
     * of the same names; {@code map} opens the map in the jungle (§17.7), and on the title
     * {@code newGame} and {@code difficulty} do what its {@code G} and {@code D} do (§17.2).
     */
    public record Buttons(
            List<String> fire,
            List<String> pause,
            List<String> quit,
            List<String> map,
            List<String> newGame,
            List<String> difficulty) {

        public Buttons {
            fire = List.copyOf(fire);
            pause = List.copyOf(pause);
            quit = List.copyOf(quit);
            map = List.copyOf(map);
            newGame = List.copyOf(newGame);
            difficulty = List.copyOf(difficulty);
        }
    }
}
