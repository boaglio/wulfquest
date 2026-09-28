package wulf.data;

import java.util.List;

/**
 * Bound view of {@code data/config/shell.json} (AGENTS.md §17.1–§17.5): every
 * string the shell shows and every beat it counts.
 *
 * <p>Nothing here is a constant in Java. A menu entry's {@code screen} names a
 * {@code Shell.State}, checked at load so a typo is a DATA ERROR and not a dead
 * key on the title screen.
 */
public record ShellConfig(
        int schemaVersion,
        int screenHoldTicks,
        Title title,
        Attract attract,
        GameOver gameOver,
        Tally tally,
        HiScores hiScores,
        KeyConfig keyConfig,
        Sound sound,
        Stats stats,
        AmuletReveal amuletReveal,
        Credits credits) {

    public record Title(String wordmark, String tagline, String prompt, Cycle cycle, List<MenuItem> menu,
                        String footer) {
        public Title {
            menu = List.copyOf(menu);
        }
    }

    /** The wordmark's colours, walked one row at a time (§17.2). */
    public record Cycle(List<String> colours, int ticksPerStep) {
        public Cycle {
            colours = List.copyOf(colours);
        }
    }

    public record MenuItem(String key, String screen, String label) {
    }

    public record Attract(int idleTicks, String replay, String banner, boolean dimEveryOtherRow) {
    }

    /** @param progress the line under it, {@code {percent}} standing for how much of the adventure is done (§17.5) */
    public record GameOver(String text, String progress, int holdTicks) {
    }

    /**
     * The screen a quarter brings up (§14.6): the amulet so far, drawn large on a
     * cleared playfield, over the verse for how many quarters are held — the first
     * verse for one, the fourth for all four. The game stands still underneath.
     */
    public record AmuletReveal(int holdTicks, int skipAfterTicks, int scale, List<List<String>> verses) {
        public AmuletReveal {
            List<List<String>> copies = new java.util.ArrayList<>();
            for (List<String> verse : verses) {
                copies.add(List.copyOf(verse));
            }
            verses = List.copyOf(copies);
        }

        /** No reveal at all: what a bare {@code GameSession} in a test gets. */
        public static final AmuletReveal NONE = new AmuletReveal(0, 0, 1, List.of());
    }

    public record Tally(int holdTicks) {
    }

    public record HiScores(String heading, String entryHeading, String entryPrompt, int size, int nameLength,
                           String alphabet, int cursorBlinkTicks, int repeatDelayTicks, int repeatEveryTicks) {
    }

    public record KeyConfig(String heading, String prompt, List<String> actionOrder) {
        public KeyConfig {
            actionOrder = List.copyOf(actionOrder);
        }
    }

    /** §21.3: the two audio settings the player can actually reach. */
    public record Sound(String heading, String prompt, String onLabel, String offLabel, String volumeLabel,
                        int volumeStepPercent) {
    }

    public record Stats(String heading, String emptyLine) {
    }

    public record Credits(String heading, List<String> lines) {
        public Credits {
            lines = List.copyOf(lines);
        }
    }
}
