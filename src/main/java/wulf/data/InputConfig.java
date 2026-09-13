package wulf.data;

import java.util.List;
import java.util.Map;

/** Bound view of {@code data/config/input.json} (AGENTS.md §19.1). */
public record InputConfig(int schemaVersion, String active, Map<String, Profile> profiles, Gamepad gamepad) {

    public InputConfig {
        profiles = Map.copyOf(profiles);
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
            List<String> devMask) {

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
        }
    }

    /** Integer percent, not a fraction: nothing that reaches the game is a float (§6.2). */
    public record Gamepad(boolean enabled, int deadzonePercent, boolean dpadAsDirections) {
    }
}
