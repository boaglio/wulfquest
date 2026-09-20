package wulf.ui;

import java.util.function.Supplier;
import wulf.data.HighScores;
import wulf.data.PlayerDb;
import wulf.data.Settings;
import wulf.data.ShellConfig;
import wulf.data.Stats;
import wulf.input.InputState;
import wulf.input.MenuInput;
import wulf.input.RecordedInput;
import wulf.sim.Simulation;

/**
 * The game's state machine (AGENTS.md §17.1). Everything outside one round of
 * play lives here: the title, the pages it opens, attract mode, the end of a
 * run, and the hi-score table it may earn a place in.
 *
 * <p>Headless and clock-free: it is driven one tick at a time by input alone, so
 * a test can walk the whole machine without a window. The only thing it reaches
 * out to is {@link PlayerDb}, which writes on its own thread.
 */
public final class Shell {

    public enum State {
        TITLE, ATTRACT, CREDITS, HISCORES, STATS, KEY_CONFIG, SOUND, PLAYING, GAME_OVER, TALLY, HISCORE_ENTRY
    }

    /** The sounds the shell itself makes; the jungle's own are the simulation's (§18.2). */
    private static final String SFX_MOVE = "menu_move";
    private static final String SFX_PICK = "menu_pick";

    /** One attract-mode demo: a simulation and the recording that drives it (§17.2). */
    public record Demo(Simulation sim, RecordedInput.Cursor input, int ticks, boolean dev) {
    }

    private final ShellConfig config;
    private final PlayerDb db;
    private final Supplier<Simulation> newGame;
    private final Supplier<Demo> newDemo;
    private final Supplier<String> today;
    /** The input profiles the keys page offers, in the order it shows them (§19.1). */
    private final java.util.List<String> profiles;
    private Jukebox jukebox = Jukebox.SILENT;

    private State state = State.TITLE;
    private long stateTick;
    private long idleTicks;
    private boolean quit;
    private String notice;

    private GameSession session;
    private Demo demo;
    private int demoTick;
    private HiScoreEntry entry;
    private HighScores.Entry pending;
    private int highlight = -1;
    /** Edge detection for the sound page's arrows; the sim's own input has no left/right edge. */
    private boolean heldLeft;
    private boolean heldRight;

    public Shell(ShellConfig config, PlayerDb db, java.util.List<String> profiles, Supplier<Simulation> newGame,
                 Supplier<Demo> newDemo, Supplier<String> today) {
        this.config = config;
        this.profiles = java.util.List.copyOf(profiles);
        this.db = db;
        this.newGame = newGame;
        this.newDemo = newDemo;
        this.today = today;
        this.notice = db.notices().isEmpty() ? null : db.notices().get(0);
    }

    /**
     * Gives the shell a voice (§18.3). Called once, before the first tick; the
     * title's tune starts with it.
     */
    public void jukebox(Jukebox box) {
        this.jukebox = box == null ? Jukebox.SILENT : box;
        this.jukebox.tune(tuneFor(state));
    }

    // ---------------------------------------------------------------- the machine

    public void tick(InputState in, MenuInput menu, boolean dev) {
        stateTick++;
        switch (state) {
            case TITLE -> title(in, menu);
            case ATTRACT -> attract(in, menu);
            case CREDITS, HISCORES, STATS -> page(in);
            case KEY_CONFIG -> keyConfig(in, menu);
            case SOUND -> sound(in, menu);
            case PLAYING -> playing(in, dev);
            case GAME_OVER -> gameOver(in);
            case TALLY -> tally(in);
            case HISCORE_ENTRY -> hiScoreEntry(in);
        }
    }

    private void title(InputState in, MenuInput menu) {
        if (in.quitPressed()) {
            quit = true;
            return;
        }
        String screen = TitleScreen.screenFor(config.title().menu(), menu);
        if (screen != null) {
            jukebox.sfx(SFX_PICK);
            go(State.valueOf(screen));
            return;
        }
        if (in.firePressed()) {
            jukebox.sfx(SFX_PICK);
            startGame();
            return;
        }
        boolean busy = menu.anyKey() || in.up() || in.down() || in.left() || in.right() || in.fire();
        idleTicks = busy ? 0 : idleTicks + 1;
        if (idleTicks >= config.attract().idleTicks()) {
            startDemo();
        }
    }

    /** §17.2: the demo plays behind the title, and any key at all brings the title back. */
    private void attract(InputState in, MenuInput menu) {
        if (menu.anyKey() || in.firePressed() || in.quitPressed()) {
            stopDemo();
            return;
        }
        if (demo == null || demoTick >= demo.ticks() || !demo.input().hasNext()) {
            stopDemo();
            return;
        }
        demo.sim().play(demo.input().next(), demo.dev());
        demoTick++;
    }

    private void page(InputState in) {
        if (stateTick >= config.screenHoldTicks() && (in.firePressed() || in.quitPressed())) {
            go(State.TITLE);
            highlight = -1;
        }
    }

    private void keyConfig(InputState in, MenuInput menu) {
        for (int i = 0; i < profiles.size(); i++) {
            if (menu.typed(String.valueOf((char) ('1' + i)))) {
                Settings chosen = db.settings().withInputProfile(profiles.get(i));
                db.putSettings(chosen);
                jukebox.sfx(SFX_PICK);
            }
        }
        page(in);
    }

    /** §21.3: sound on or off with a digit, louder or quieter with the arrows, written either way. */
    private void sound(InputState in, MenuInput menu) {
        Settings before = db.settings();
        Settings after = before;
        if (menu.typed("1")) {
            after = after.withAudio(true, after.audio().volumePercent());
        } else if (menu.typed("2")) {
            after = after.withAudio(false, after.audio().volumePercent());
        }
        int step = config.sound().volumeStepPercent();
        int dx = (in.right() && !heldRight ? 1 : 0) - (in.left() && !heldLeft ? 1 : 0);
        heldLeft = in.left();
        heldRight = in.right();
        if (dx != 0) {
            int volume = Math.max(0, Math.min(100, after.audio().volumePercent() + dx * step));
            after = after.withAudio(after.audio().enabled(), volume);
        }
        if (!after.equals(before)) {
            db.putSettings(after);
            jukebox.settings(after.audio().enabled(), after.audio().volumePercent());
            jukebox.tune(tuneFor(state));   // switching the sound back on starts the tune again
            jukebox.sfx(SFX_MOVE);
        }
        page(in);
    }

    private void playing(InputState in, boolean dev) {
        if (in.quitPressed()) {
            // Escape leaves the jungle rather than the program: the run is banked as if it ended.
            endRun();
            return;
        }
        session.tick(in, dev);
        if (session.won()) {
            go(State.TALLY);
        } else if (session.over()) {
            go(State.GAME_OVER);
        }
    }

    private void gameOver(InputState in) {
        boolean skipped = stateTick >= config.screenHoldTicks() && in.firePressed();
        if (skipped || stateTick >= config.gameOver().holdTicks()) {
            endRun();
        }
    }

    private void tally(InputState in) {
        // A beat before fire counts, so the press that won the game cannot skip the tally.
        if (stateTick >= config.tally().holdTicks() && in.firePressed()) {
            endRun();
        }
    }

    private void hiScoreEntry(InputState in) {
        String before = entry.name();
        int cursor = entry.cursor();
        boolean committed = entry.tick(in);
        if (!before.equals(entry.name()) || cursor != entry.cursor()) {
            jukebox.sfx(SFX_MOVE);
        }
        if (committed) {
            jukebox.sfx(SFX_PICK);
            HighScores.Entry row = new HighScores.Entry(entry.name(), pending.score(), pending.pieces(),
                    pending.ticks(), pending.date());
            HighScores table = db.scores().with(row, config.hiScores().size());
            db.putScores(table);
            db.flush();
            highlight = table.entries().indexOf(row);
            pending = null;
            entry = null;
            go(State.HISCORES);
        }
    }

    // ---------------------------------------------------------------- transitions

    private void startGame() {
        session = new GameSession(newGame.get());
        go(State.PLAYING);
    }

    private void startDemo() {
        demo = newDemo == null ? null : newDemo.get();
        demoTick = 0;
        idleTicks = 0;
        if (demo != null) {
            go(State.ATTRACT);
        }
    }

    private void stopDemo() {
        demo = null;
        demoTick = 0;
        idleTicks = 0;
        go(State.TITLE);
    }

    /**
     * One game, finished: the ledger folded in, the table offered, and the way
     * back to the title (§21.2, §21.5).
     */
    private void endRun() {
        Simulation sim = session.sim();
        boolean escaped = session.won();
        db.putStats(recordGame(db.stats(), sim, escaped));
        HighScores.Entry row = new HighScores.Entry("", sim.score(), sim.quest().piecesHeld(), sim.tick(), today.get());
        session = null;
        if (db.scores().qualifies(row.score(), row.ticks(), config.hiScores().size())) {
            pending = row;
            entry = new HiScoreEntry(config.hiScores());
            go(State.HISCORE_ENTRY);
        } else {
            db.flush();
            go(State.TITLE);
        }
    }

    /** The counters this run adds to the ledger (§21.5). */
    static Stats recordGame(Stats before, Simulation sim, boolean escaped) {
        return before.afterGame(sim.score(), escaped, sim.tick(), sim.roomsEntered(), sim.quest().piecesHeld(),
                sim.deaths(), sim.wulf().appearances(), sim.wulf().evasions(),
                counts(sim.killsBySpecies()), counts(sim.deathsByCause()), counts(sim.orchidsByColour()));
    }

    private static java.util.Map<String, Long> counts(java.util.Map<String, Integer> from) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        from.forEach((k, v) -> out.put(k, (long) v));
        return out;
    }

    private void go(State next) {
        state = next;
        stateTick = 0;
        jukebox.tune(tuneFor(next));
    }

    /**
     * §18.3: the title's theme over every menu screen, a fanfare for the arch, a
     * descending figure for the end — and in the jungle, nothing. Silence is the
     * tension; do not put music behind {@code PLAYING}.
     */
    static String tuneFor(State state) {
        return switch (state) {
            case PLAYING -> null;
            case GAME_OVER -> wulf.data.MusicData.GAME_OVER;
            case TALLY -> wulf.data.MusicData.WIN;
            default -> wulf.data.MusicData.TITLE;
        };
    }

    // ---------------------------------------------------------------- what the painter reads

    public State state() {
        return state;
    }

    public long stateTick() {
        return stateTick;
    }

    public boolean quit() {
        return quit;
    }

    public GameSession session() {
        return session;
    }

    public Demo demo() {
        return demo;
    }

    public HiScoreEntry entry() {
        return entry;
    }

    public HighScores.Entry pending() {
        return pending;
    }

    public int highlight() {
        return highlight;
    }

    public String notice() {
        return notice;
    }

    /** The simulation currently on screen: the game being played, or the demo. */
    public Simulation onScreen() {
        if (session != null) {
            return session.sim();
        }
        return demo == null ? null : demo.sim();
    }
}
