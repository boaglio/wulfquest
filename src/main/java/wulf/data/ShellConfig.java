package wulf.data;

import java.util.List;
import java.util.Map;

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
        Saves saves,
        Credits credits,
        NewGamePlus newGamePlus,
        Difficulty difficulty,
        Pause pause,
        Options options) {

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
    public record MapScreen(String key, Map<String, String> profileKeys, String message, String visited,
                            String revealed, String here, int blinkTicks, QuarterIcon quarterIcon, Trail trail) {
        public MapScreen {
            profileKeys = profileKeys == null ? Map.of() : Map.copyOf(profileKeys);
            trail = trail == null ? Trail.NONE : trail;
        }

        /** The key that opens the map in input profile {@code profile}: its own, or {@code key}. */
        public String keyFor(String profile) {
            return profileKeys.getOrDefault(profile, key);
        }

        /** What the playfield says while the map is open, naming that key. */
        public String messageFor(String profile) {
            return message.replace("{key}", keyFor(profile));
        }
    }

    /**
     * The way a looking glass shows, on the map (§14.10): a dot of {@code colour} every
     * {@code dotEveryPx} along it, marching on a pixel every {@code marchTicks}.
     */
    public record Trail(String colour, int dotEveryPx, int marchTicks) {
        public static final Trail NONE = new Trail("white", 1, 1);
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
     * The save slots (§21.4, {@code [NEW]}), from the pause menu and the title. A slot's row is
     * {@code slot} with {@code {n}}, {@code {mode}}, {@code {job}}, {@code {quarters}} and
     * {@code {score}} filled in, and under it {@code detail} with {@code {date}} and
     * {@code {room}}; an empty one is {@code empty}. {@code jobNames} names job n, the game's
     * own first; the notes stay on the panel {@code noteTicks}.
     */
    public record Saves(int slots, String saveHeading, String loadHeading, String slot, String detail, String empty,
                        List<String> jobNames, ModeNames modeNames, String saved, String loaded, String emptySlot,
                        int noteTicks, String footer) {
        public Saves {
            jobNames = List.copyOf(jobNames);
        }
    }

    public record ModeNames(String hard, String easy) {
    }

    /** One line of a menu: what it does ({@code id}, which the shell knows) and what it says. */
    public record Entry(String id, String label) {
    }

    /** The pause menu (§17.4, {@code [NEW]}): {@code items} over the frozen jungle. */
    public record Pause(String heading, List<Entry> items, String footer) {
        public Pause {
            items = List.copyOf(items);
        }

        /** The ids the shell acts on, in any order. */
        public static final List<String> IDS = List.of("resume", "save", "load", "options", "quit");
    }

    /** The options page (§17.9, {@code [NEW]}): each item a switch, shown {@code on} or {@code off}, and back. */
    public record Options(String heading, List<Entry> items, String on, String off, String footer,
                          String volumeFooter) {
        public Options {
            items = List.copyOf(items);
        }

        /** The ids the shell acts on: the switches it knows, and back. */
        public static final List<String> IDS = List.of("sound", "volume", "scanlines", "attributeClash", "inkedCreatures",
                "back");
    }

    /**
     * @param progress       the line under it, {@code {percent}} standing for how much of the adventure is done (§17.5)
     * @param continuePrompt §17.5: shown while the game may go on; fire takes it
     * @param continuesLeft  under it, with {@code {n}} the continues left
     * @param continueTicks  how long the offer stands before the run is banked
     * @param countdownScale the seconds left, under all that, this many times the big font
     */
    public record GameOver(String text, String progress, int holdTicks, String continuePrompt, String continuesLeft,
                           int continueTicks, int countdownScale) {
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
                         int promptAt, String promptColour, List<String> amuletFrames, Ride ride) {
        public Ending {
            backdrop = List.copyOf(backdrop);
            lines = List.copyOf(lines);
        }

        /** The game's own ending: its four quarters put together, and no ride. */
        public Ending(List<Placed> backdrop, int groundY, Walker vale, Jones jones, Amulet amulet,
                      List<Line> lines, int ticksPerChar, String tune, int tuneAt, String prompt,
                      int promptAt, String promptColour) {
            this(backdrop, groundY, vale, jones, amulet, lines, ticksPerChar, tune, tuneAt, prompt, promptAt,
                    promptColour, null, null);
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

    /**
     * The jobs after the first (§14.9, {@code [NEW]}): one entry per level of
     * {@code game.json → newGamePlus}. Once one is unlocked, the title shows the chosen job's
     * {@code label}, and {@code key} steps through those unlocked.
     */
    public record NewGamePlus(String key, String labelColour, List<Job> levels) {
        public NewGamePlus {
            levels = List.copyOf(levels);
        }

        /** Only the game itself: what a test's shell gets. */
        public static final NewGamePlus NONE = new NewGamePlus("N", "white", List.of(new Job("", null, null, null)));
    }

    /**
     * Easy mode or Hard (§17.8, {@code [NEW]}): {@code key} on the title switches, and the title
     * shows {@code hard} or {@code easy} for the one the next game is; an Easy game shows
     * {@code panelLabel} on the panel, and its row on the hi-score table {@code hiScoreMark},
     * both in {@code easyColour}.
     */
    public record Difficulty(String key, String hard, String easy, String easyColour, String panelLabel,
                             String hiScoreMark) {
        /** What a test's shell gets. */
        public static final Difficulty NONE = new Difficulty("D", "D HARD", "D EASY", "white", "EASY", "E");
    }

    /**
     * One job's words (§14.9).
     *
     * @param tagline the title's tagline while this job is chosen; null for the game itself, which has
     *                {@code title.tagline}
     * @param verses the amulet reveal's verses for this job, one per piece; null for the game
     *               itself, which has {@code amuletReveal.verses}
     * @param offer  the scene after the credits of the win that unlocks this job; null for the first
     * @param ending this job's own closing scene (§14.7); null for the game itself, which has {@code ending}
     * @param theEnd the first row of the credits roll after winning it (§14.7); null for the roll's own
     * @param amulet this job's own amulet (§14.9): a frame of the amulet sprite for each panel slot,
     *               0..3 reading across; null for the game itself, whose quarters are its lairs'.
     */
    public record Job(String label, String tagline, List<List<String>> verses, Offer offer, JobEnding ending,
                      String theEnd, List<String> amulet) {
        public Job {
            verses = verses == null ? null : verses.stream().map(List::copyOf).toList();
            amulet = amulet == null ? null : List.copyOf(amulet);
        }

        /** A job that ends as the game does. */
        public Job(String label, String tagline, List<List<String>> verses, Offer offer) {
            this(label, tagline, verses, offer, null, null, null);
        }

        public Job(String label, String tagline, List<List<String>> verses, Offer offer, JobEnding ending) {
            this(label, tagline, verses, offer, ending, null, null);
        }
    }

    /**
     * Mr Jones offers the next job (§14.9): the clearing of the closing scene, Vale standing
     * before him, the amulet gone to its museum; the {@code lines} type out as the ending's do, and
     * fire goes on from {@code promptAt}.
     */
    public record Offer(List<Line> lines, int promptAt) {
        public Offer {
            lines = List.copyOf(lines);
        }
    }

    /** A sprite's frame drawn top-left at ({@code x}, {@code y}) on the canvas. */
    public record Placed(String sprite, String frame, int x, int y) {
    }

    /**
     * A later job's ending (§14.7, §14.9): its own words, its own beats — and, for the last, the
     * ride home; the amulet handed over is the job's own ({@link Job#amulet}). What it leaves out
     * is the game's own ending's.
     *
     */
    public record JobEnding(List<Line> lines, int tuneAt, int promptAt, Ride ride) {
        public JobEnding {
            lines = List.copyOf(lines);
        }
    }

    /**
     * The ride home (§14.7), the last ending's: at {@code comeAt} two saddled horses trot in from
     * {@code fromX} over {@code comeTicks}, each to stand where one of them does; at {@code mountAt}
     * Vale and Mr Jones are up; from {@code rideAt} they gallop {@code toX - Vale's x} to the
     * right over {@code rideTicks}, and are gone. Frames step every {@code ticksPerFrame}.
     */
    public record Ride(String sprite, int ticksPerFrame, int comeAt, int comeTicks, int fromX, List<String> valeHorse,
                       List<String> jonesHorse, int mountAt, List<String> valeRiding, List<String> jonesRiding,
                       int rideAt, int rideTicks, int toX) {
        public Ride {
            valeHorse = List.copyOf(valeHorse);
            jonesHorse = List.copyOf(jonesHorse);
            valeRiding = List.copyOf(valeRiding);
            jonesRiding = List.copyOf(jonesRiding);
        }
    }

    /** Job {@code level}'s own amulet, a frame a slot (§14.9); null for the game's own quarters. */
    public List<String> amuletFor(int level) {
        return level > 0 && level < newGamePlus.levels().size() ? newGamePlus.levels().get(level).amulet() : null;
    }

    /** The first row of the credits roll after winning job {@code level} (§14.7): its own, or the roll's. */
    public String theEndFor(int level) {
        String own = level >= 0 && level < newGamePlus.levels().size() ? newGamePlus.levels().get(level).theEnd() : null;
        return own != null ? own : credits.roll().theEnd();
    }

    /**
     * The ending of job {@code level} (§14.9): the game's own for 0 or a job without one of its
     * own, else the game's with the job's amulet, words, beats and ride laid over it.
     */
    public Ending endingFor(int level) {
        if (level <= 0 || level >= newGamePlus.levels().size() || newGamePlus.levels().get(level).ending() == null) {
            return ending;
        }
        JobEnding j = newGamePlus.levels().get(level).ending();
        Ending e = ending;
        return new Ending(e.backdrop(), e.groundY(), e.vale(), e.jones(), e.amulet(), j.lines(), e.ticksPerChar(),
                e.tune(), j.tuneAt(), e.prompt(), j.promptAt(), e.promptColour(),
                newGamePlus.levels().get(level).amulet(), j.ride());
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

        /** Everything the roll shows, a row each: {@code theEnd}, {@code before}, the heading, a gap, the lines, {@code after}. */
        public List<String> rollRows() {
            return rollRows(roll.theEnd());
        }

        /** The same, with the job's own {@code theEnd} at the top (§14.7). */
        public List<String> rollRows(String theEnd) {
            List<String> rows = new java.util.ArrayList<>();
            rows.add(theEnd);
            rows.addAll(roll.before());
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
     *
     * @param theEnd the roll's first row, unless the job won says otherwise ({@code newGamePlus.levels[n].theEnd})
     */
    public record Roll(String theEnd, List<String> before, List<String> after, int fromY, int lineHeightPx,
                       int ticksPerPx, String headingColour, String bodyColour) {
        public Roll {
            before = List.copyOf(before);
            after = List.copyOf(after);
        }
    }
}
