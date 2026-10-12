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
        Ending ending,
        Tally tally,
        HiScores hiScores,
        KeyConfig keyConfig,
        Sound sound,
        Stats stats,
        AmuletReveal amuletReveal,
        MapScreen map,
        QuickSave quickSave,
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

    /**
     * The map screen (§17.7, {@code [NEW]}): {@code key} opens and closes it during play; rooms
     * visited are drawn in {@code visited}, rooms only the map scroll shows in {@code revealed},
     * and the room Vale is in blinks in {@code here} every {@code blinkTicks}. A lair an eye has
     * shown, or whose quarter is taken, carries {@code quarterIcon} (§14.8).
     */
    public record MapScreen(String key, String message, String visited, String revealed, String here,
                            int blinkTicks, QuarterIcon quarterIcon) {
    }

    /**
     * The mark on the map for a quarter's room (§17.7): {@code shiny} shown in turn, each for
     * {@code frameTicks}, while the quarter waits there; the {@code taken} frame once it is held.
     */
    public record QuarterIcon(String sprite, List<String> shiny, int frameTicks, String taken) {
        public QuarterIcon {
            shiny = List.copyOf(shiny);
        }
    }

    /**
     * Quick save and quick load (§21.4, {@code [NEW]}, {@code features.quickSave}): the keys,
     * by name ({@code "F5"}), and how long "SAVED" or a refusal stays on the panel.
     */
    public record QuickSave(String saveKey, String loadKey, int noteTicks) {
    }

    /** @param progress the line under it, {@code {percent}} standing for how much of the adventure is done (§17.5) */
    public record GameOver(String text, String progress, int holdTicks) {
    }

    /**
     * The screen a quarter brings up (§14.6): the amulet so far, drawn large on a
     * cleared playfield, over the verse for how many quarters are held — the first
     * verse for one, the fourth for all four. The game stands still underneath until fire
     * is pressed — from {@code skipAfterTicks} on, when {@code prompt} shows — so the press
     * that took the quarter cannot close it unseen.
     */
    public record AmuletReveal(int skipAfterTicks, int scale, String prompt, List<List<String>> verses) {
        public AmuletReveal {
            List<List<String>> copies = new java.util.ArrayList<>();
            for (List<String> verse : verses) {
                copies.add(List.copyOf(verse));
            }
            verses = List.copyOf(copies);
        }

        /** No reveal at all: what a bare {@code GameSession} in a test gets. */
        public static final AmuletReveal NONE = new AmuletReveal(0, 1, "", List.of());
    }

    /**
     * The tally (§14.7): {@code heading}, then one line of {@code labels} every
     * {@code lineEveryTicks} — collected, escape bonus, time bonus, lives bonus — then
     * {@code total}, {@code newBest} when it is one, and {@code prompt}. Fire counts only
     * from {@code holdTicks}.
     */
    public record Tally(int holdTicks, String heading, List<String> labels, String total, String newBest,
                        String prompt, int lineEveryTicks, String headingColour, String labelColour,
                        String figureColour) {
        public Tally {
            labels = List.copyOf(labels);
        }
    }

    /**
     * The closing scene (§14.7, {@code [NEW]}), played on the whole canvas once Vale is
     * through the arch: a clearing drawn from {@code backdrop}; Vale walks in carrying the
     * amulet to Mr Jones, who takes it; the {@code lines} type out, each from its own tick;
     * {@code tune} plays from {@code tuneAt}; fire goes on from {@code promptAt}.
     */
    public record Ending(List<Placed> backdrop, int groundY, Walker vale, Jones jones, Amulet amulet,
                         List<Line> lines, int ticksPerChar, String tune, int tuneAt, String prompt,
                         int promptAt, String promptColour) {
        public Ending {
            backdrop = List.copyOf(backdrop);
            lines = List.copyOf(lines);
        }

        /** The tick the scene is finished by: the last line typed, the tune started, the prompt up. */
        public int lastTick() {
            int last = Math.max(tuneAt, promptAt);
            for (Line line : lines) {
                last = Math.max(last, line.at() + line.length() * ticksPerChar);
            }
            return last;
        }
    }

    /** A sprite's frame drawn top-left at ({@code x}, {@code y}) on the canvas. */
    public record Placed(String sprite, String frame, int x, int y) {
    }

    /** Vale: walking frames in turn from {@code fromX} to {@code toX} over {@code walkTicks}, then {@code stand}. */
    public record Walker(String sprite, List<String> walk, int ticksPerFrame, String stand, int fromX, int toX,
                         int walkTicks) {
        public Walker {
            walk = List.copyOf(walk);
        }
    }

    /** Mr Jones, standing at {@code x}; his mouth moves every {@code talkTicksPerFrame} while his line types. */
    public record Jones(String sprite, int x, String stand, String talk, String hold, String holdTalk,
                        int talkTicksPerFrame) {
    }

    /**
     * The amulet, whole, drawn at half size: its centre {@code carry} from Vale's feet, until
     * {@code handOverAt}, then over {@code handOverTicks} to {@code hold} from Mr Jones's.
     */
    public record Amulet(Offset carry, Offset hold, int handOverAt, int handOverTicks) {
    }

    public record Offset(int dx, int dy) {
    }

    /** One speech: {@code text} typed out from tick {@code at}, top line at {@code y}; {@code speaker} is vale or jones. */
    public record Line(String speaker, int at, int y, String colour, List<String> text) {
        public static final String VALE = "vale";
        public static final String JONES = "jones";

        public Line {
            text = List.copyOf(text);
        }

        public int length() {
            int n = 0;
            for (String t : text) {
                n += t.length();
            }
            return n;
        }
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

    /**
     * The credits (§17.2): the page the title opens, {@code heading} over {@code lines} and
     * {@code prompt}; and after a win the same lines rolled up the screen (§14.7).
     */
    public record Credits(String heading, List<String> lines, String prompt, Roll roll) {
        public Credits {
            lines = List.copyOf(lines);
        }

        /** Everything the roll shows, a row each: {@code before}, the heading, a gap, the lines, {@code after}. */
        public List<String> rollRows() {
            List<String> rows = new java.util.ArrayList<>(roll.before());
            rows.add(heading);
            rows.add("");
            rows.addAll(lines);
            rows.addAll(roll.after());
            return List.copyOf(rows);
        }

        /** How long the roll takes: from its first row at {@code fromY} until its last is off the top. */
        public int rollTicks() {
            return (roll.fromY() + rollRows().size() * roll.lineHeightPx()) * roll.ticksPerPx();
        }
    }

    /**
     * The roll after a win: {@code before}, the heading and lines, {@code after}, a row every
     * {@code lineHeightPx}, starting with its first row at {@code fromY} and rising a pixel every
     * {@code ticksPerPx}. The rows of {@code before} and {@code after}, and the heading, are in
     * the display font and {@code headingColour}; the lines in the small one and {@code bodyColour}.
     */
    public record Roll(List<String> before, List<String> after, int fromY, int lineHeightPx, int ticksPerPx,
                       String headingColour, String bodyColour) {
        public Roll {
            before = List.copyOf(before);
            after = List.copyOf(after);
        }
    }
}
