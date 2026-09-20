package wulf.data;

import java.util.HashSet;
import java.util.Set;

/**
 * Semantic checks for {@code shell.json} beyond its schema (AGENTS.md §20.6):
 * the colours it cycles must exist, its hotkeys must not collide with each
 * other or with the alphabet the hi-score selector uses, and the alphabet must
 * be something the font can actually draw.
 */
public final class ShellValidator {

    private static final String SOURCE = "data/config/shell.json";

    private ShellValidator() {
    }

    public static void check(ShellConfig shell, Palette palette, InputConfig input) {
        for (int i = 0; i < shell.title().cycle().colours().size(); i++) {
            String colour = shell.title().cycle().colours().get(i);
            if (!inPalette(palette, colour)) {
                throw new DataException(SOURCE, "/title/cycle/colours/" + i,
                        "names '" + colour + "', which is not a palette entry");
            }
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < shell.title().menu().size(); i++) {
            ShellConfig.MenuItem item = shell.title().menu().get(i);
            if (!keys.add(item.key())) {
                throw new DataException(SOURCE, "/title/menu/" + i + "/key",
                        "binds '" + item.key() + "' twice on the title screen");
            }
        }
        // The keys page offers a profile per digit; a menu hotkey on one of those digits would fight it.
        for (int i = 0; i < input.profiles().size(); i++) {
            String digit = String.valueOf((char) ('1' + i));
            if (keys.contains(digit)) {
                throw new DataException(SOURCE, "/title/menu",
                        "binds '" + digit + "', which the keys page already uses to choose an input profile");
            }
        }
        if (shell.hiScores().alphabet().chars().distinct().count() != shell.hiScores().alphabet().length()) {
            throw new DataException(SOURCE, "/hiScores/alphabet", "repeats a letter");
        }
    }

    private static boolean inPalette(Palette palette, String name) {
        for (Palette.Entry e : palette.entries()) {
            if (e.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
