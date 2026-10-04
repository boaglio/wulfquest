package wulf.input;

import java.util.List;

/**
 * The keys the shell reads that the simulation never sees (AGENTS.md §19.2):
 * the title screen's letters and digits, the function keys by name ("F5"), and
 * "any key at all", which is how attract mode ends.
 *
 * <p>Deliberately separate from {@link InputState}: nothing here is recorded in
 * a replay, and nothing here can reach the simulation.
 */
public record MenuInput(List<Character> typed, boolean anyKey, List<String> named) {

    public static final MenuInput NONE = new MenuInput(List.of(), false);

    public MenuInput {
        typed = List.copyOf(typed);
        named = List.copyOf(named);
    }

    public MenuInput(List<Character> typed, boolean anyKey) {
        this(typed, anyKey, List.of());
    }

    /** A letter or digit ({@code "M"}), or a named key ({@code "F5"}). */
    public boolean typed(String key) {
        if (key.length() > 1) {
            return named.contains(key.toUpperCase(java.util.Locale.ROOT));
        }
        return !key.isEmpty() && typed.contains(Character.toUpperCase(key.charAt(0)));
    }

    /** A named key alone, for tests. */
    public static MenuInput named(String key) {
        return new MenuInput(List.of(), true, List.of(key.toUpperCase(java.util.Locale.ROOT)));
    }

    public static MenuInput of(String keys) {
        List<Character> chars = new java.util.ArrayList<>();
        for (int i = 0; i < keys.length(); i++) {
            chars.add(Character.toUpperCase(keys.charAt(i)));
        }
        return new MenuInput(chars, !chars.isEmpty());
    }
}
