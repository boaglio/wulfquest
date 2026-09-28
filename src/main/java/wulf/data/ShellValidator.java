package wulf.data;

import java.util.HashSet;
import java.util.List;
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

    /**
     * The amulet reveal (§14.6): a verse for every count of quarters, a hold the fire
     * button can cut short but not before it has been seen, and every line and the
     * enlarged amulet fitting the playfield in the display font.
     */
    public static void checkAmuletReveal(ShellConfig shell, LandmarksData marks, DisplayConfig display, FontData font) {
        ShellConfig.AmuletReveal r = shell.amuletReveal();
        int quarters = marks.lairs().size();
        if (r.verses().size() != quarters) {
            throw new DataException(SOURCE, "/amuletReveal/verses",
                    "has " + r.verses().size() + " verses; there is one for each of the " + quarters + " quarters held");
        }
        if (r.skipAfterTicks() > r.holdTicks()) {
            throw new DataException(SOURCE, "/amuletReveal/skipAfterTicks",
                    "is " + r.skipAfterTicks() + ", longer than the " + r.holdTicks() + " ticks the screen is held");
        }
        FontData.Set big = font.sets().get("big");
        DisplayConfig.Playfield field = display.playfield();
        for (int v = 0; v < r.verses().size(); v++) {
            for (int l = 0; l < r.verses().get(v).size(); l++) {
                String line = r.verses().get(v).get(l);
                if (line.length() * big.advance() > field.w()) {
                    throw new DataException(SOURCE, "/amuletReveal/verses/" + v + "/" + l,
                            "is " + line.length() + " characters, wider than the playfield");
                }
            }
        }
        int tallest = 0;
        for (List<String> verse : r.verses()) {
            tallest = Math.max(tallest, verse.size());
        }
        // Two quarters down, a line of space, then the verse.
        if (2 * marks.amulet().size().h() * r.scale() + (tallest + 1) * big.lineHeight() + REVEAL_MARGINS_PX > field.h()) {
            throw new DataException(SOURCE, "/amuletReveal/scale",
                    "is " + r.scale() + ": the amulet and its verse no longer fit the playfield");
        }
    }

    /** The game-over progress line must fit the playfield at its widest, "100". */
    public static void checkGameOver(ShellConfig shell, DisplayConfig display, FontData font) {
        String widest = shell.gameOver().progress().replace("{percent}", "100");
        if (widest.length() * font.sets().get("big").advance() > display.playfield().w()) {
            throw new DataException(SOURCE, "/gameOver/progress", "is wider than the playfield at 100%");
        }
    }

    /** The space the reveal keeps above the amulet, between it and the verse, and below. Layout, not a game number. */
    private static final int REVEAL_MARGINS_PX = 16;

    private static boolean inPalette(Palette palette, String name) {
        for (Palette.Entry e : palette.entries()) {
            if (e.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
