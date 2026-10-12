package wulf.data;

import java.util.List;

/**
 * Load-time checks for the jobs after the first (AGENTS.md §14.9, {@code [NEW]}): that
 * {@code game.json} breaks the amulet into pieces that fill the panel's four slots once
 * each, that its level 0 is the game itself, and that {@code shell.json} has words for
 * every level (a tagline, verses, an offer), fitting the screen.
 */
public final class NewGamePlusValidator {

    private static final String GAME = "data/config/game.json";
    private static final String SHELL = "data/config/shell.json";
    private static final int ORIGINAL_PACE_PERCENT = 100;

    private NewGamePlusValidator() {
    }

    /** {@code game.json → newGamePlus} against the lairs of {@code landmarks.json}. */
    public static void check(GameConfig game, LandmarksData marks) {
        List<GameConfig.Level> levels = game.newGamePlus().levels();
        int all = 0;
        for (LandmarksData.Lair lair : marks.lairs()) {
            all |= lair.slotMask();
        }
        for (int n = 0; n < levels.size(); n++) {
            GameConfig.Level level = levels.get(n);
            String at = "/newGamePlus/levels/" + n;
            if (level.pieces().size() > marks.lairs().size()) {
                throw new DataException(GAME, at + "/pieces",
                        "has " + level.pieces().size() + " pieces; there are only " + marks.lairs().size() + " lairs to hide them in");
            }
            int filled = 0;
            for (int p = 0; p < level.pieces().size(); p++) {
                for (int slot : level.pieces().get(p)) {
                    if ((filled & 1 << slot) != 0) {
                        throw new DataException(GAME, at + "/pieces/" + p, "fills slot " + slot + ", which another piece fills");
                    }
                    filled |= 1 << slot;
                }
            }
            if (filled != all) {
                throw new DataException(GAME, at + "/pieces", "does not fill every slot of the amulet once");
            }
        }
        GameConfig.Level first = levels.get(0);
        if (first.pieces().size() != marks.lairs().size() || first.rampHeadStart() != 0
                || first.wulfSpeedPercent() != ORIGINAL_PACE_PERCENT) {
            throw new DataException(GAME, "/newGamePlus/levels/0",
                    "is the game itself: one piece a lair, no head start, the Wulf at 100%");
        }
        for (int n = 0; n < marks.lairs().size() && n < first.pieces().size(); n++) {
            if (first.slotMask(n) != marks.lairs().get(n).slotMask()) {
                throw new DataException(GAME, "/newGamePlus/levels/0/pieces/" + n,
                        "is not lair " + marks.lairs().get(n).id() + "'s own quarter");
            }
        }
    }

    /** {@code shell.json → newGamePlus}: a word for every level, and every word on the screen. */
    public static void checkWords(ShellConfig shell, GameConfig game, Palette palette, DisplayConfig display,
                                  FontData font) {
        ShellConfig.NewGamePlus words = shell.newGamePlus();
        List<GameConfig.Level> levels = game.newGamePlus().levels();
        if (words.levels().size() != levels.size()) {
            throw new DataException(SHELL, "/newGamePlus/levels",
                    "has " + words.levels().size() + " entries; game.json has " + levels.size() + " levels");
        }
        for (ShellConfig.MenuItem item : shell.title().menu()) {
            if (item.key().equals(words.key())) {
                throw new DataException(SHELL, "/newGamePlus/key", "is '" + words.key() + "', which the title menu uses");
            }
        }
        colour(palette, words.labelColour(), "/newGamePlus/labelColour");
        int width = display.canvas().w();
        FontData.Set big = font.sets().get("big");
        FontData.Set small = font.sets().get("small");
        for (int n = 0; n < words.levels().size(); n++) {
            ShellConfig.Job job = words.levels().get(n);
            String at = "/newGamePlus/levels/" + n;
            fits(job.label(), small.advance(), width, at + "/label");
            if (n == 0) {
                if (job.tagline() != null || job.verses() != null || job.offer() != null) {
                    throw new DataException(SHELL, at,
                            "is the game itself: its tagline is title's, its verses are amuletReveal's, and nothing offers it");
                }
                continue;
            }
            if (job.tagline() == null || job.verses() == null || job.offer() == null) {
                throw new DataException(SHELL, at, "needs its tagline, its verses and the offer that unlocks it");
            }
            fits(job.tagline(), small.advance(), width, at + "/tagline");
            int pieces = levels.get(n).pieces().size();
            if (job.verses().size() != pieces) {
                throw new DataException(SHELL, at + "/verses",
                        "has " + job.verses().size() + " verses; there is one for each of the " + pieces + " pieces");
            }
            for (int v = 0; v < job.verses().size(); v++) {
                for (int l = 0; l < job.verses().get(v).size(); l++) {
                    fits(job.verses().get(v).get(l), big.advance(), display.playfield().w(), at + "/verses/" + v + "/" + l);
                }
            }
            for (int i = 0; i < job.offer().lines().size(); i++) {
                ShellConfig.Line line = job.offer().lines().get(i);
                colour(palette, line.colour(), at + "/offer/lines/" + i + "/colour");
                for (int t = 0; t < line.text().size(); t++) {
                    fits(line.text().get(t), big.advance(), width, at + "/offer/lines/" + i + "/text/" + t);
                }
            }
        }
    }

    private static void colour(Palette palette, String name, String pointer) {
        if (palette.entries().stream().noneMatch(e -> e.name().equals(name))) {
            throw new DataException(SHELL, pointer, "names '" + name + "', which is not a palette entry");
        }
    }

    private static void fits(String text, int advance, int width, String pointer) {
        if (text.length() * advance > width) {
            throw new DataException(SHELL, pointer, "\"" + text + "\" is wider than the screen");
        }
    }
}
