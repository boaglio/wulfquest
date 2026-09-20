package wulf.input;

import java.util.List;

/**
 * The keys the shell reads that the simulation never sees (AGENTS.md §19.2):
 * the title screen's letters and digits, and "any key at all", which is how
 * attract mode ends.
 *
 * <p>Deliberately separate from {@link InputState}: nothing here is recorded in
 * a replay, and nothing here can reach the simulation.
 */
public record MenuInput(List<Character> typed, boolean anyKey) {

    public static final MenuInput NONE = new MenuInput(List.of(), false);

    public MenuInput {
        typed = List.copyOf(typed);
    }

    public boolean typed(String key) {
        return !key.isEmpty() && typed.contains(Character.toUpperCase(key.charAt(0)));
    }

    public static MenuInput of(String keys) {
        List<Character> chars = new java.util.ArrayList<>();
        for (int i = 0; i < keys.length(); i++) {
            chars.add(Character.toUpperCase(keys.charAt(i)));
        }
        return new MenuInput(chars, !chars.isEmpty());
    }
}
