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
public record MenuInput(List<Character> typed, boolean anyKey, List<String> named, java.util.Set<Pad> pads) {

    /** The gamepad's stand-ins for the shell's letters (§19.3): the map's key, and the title's G and D. */
    public enum Pad { MAP, NEW_GAME, DIFFICULTY }

    public static final MenuInput NONE = new MenuInput(List.of(), false);

    /** The name a {@code menuSelect} key (§19.1) arrives under: Space or Enter, in any profile. */
    public static final String SELECT = "SELECT";

    public MenuInput {
        typed = List.copyOf(typed);
        named = List.copyOf(named);
        pads = java.util.Set.copyOf(pads);
    }

    public MenuInput(List<Character> typed, boolean anyKey, List<String> named) {
        this(typed, anyKey, named, java.util.Set.of());
    }

    public MenuInput(List<Character> typed, boolean anyKey) {
        this(typed, anyKey, List.of());
    }

    /** The keyboard's and the gamepad's together, for one tick. */
    public MenuInput and(MenuInput other) {
        if (other == NONE) {
            return this;
        }
        List<Character> chars = new java.util.ArrayList<>(typed);
        chars.addAll(other.typed);
        List<String> names = new java.util.ArrayList<>(named);
        names.addAll(other.named);
        java.util.Set<Pad> both = new java.util.HashSet<>(pads);
        both.addAll(other.pads);
        return new MenuInput(chars, anyKey || other.anyKey, names, both);
    }

    /** A key that chooses the line on a menu page was pressed (§19.1). */
    public boolean selected() {
        return named.contains(SELECT);
    }

    /** A gamepad button standing in for one of the shell's letters. */
    public boolean pad(Pad button) {
        return pads.contains(button);
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
