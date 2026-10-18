package wulf.ui;

import java.util.function.IntFunction;
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
        TITLE, ATTRACT, CREDITS, HISCORES, STATS, KEY_CONFIG, SOUND, PLAYING, GAME_OVER, ENDING, TALLY, ROLL,
        OFFER, HISCORE_ENTRY, PAUSED, SAVE_GAME, LOAD_GAME, OPTIONS
    }

    /** The sounds the shell itself makes; the jungle's own are the simulation's (§18.2). */
    private static final String SFX_MOVE = "menu_move";
    private static final String SFX_PICK = "menu_pick";

    /** One attract-mode demo: a simulation and the recording that drives it (§17.2). */
    public record Demo(Simulation sim, RecordedInput.Cursor input, int ticks, boolean dev) {
    }

    private final ShellConfig config;
    private final PlayerDb db;
    /** A new game of job {@code level} (§14.9), 0 for the game itself, in Easy mode or Hard (§17.8). */
    @FunctionalInterface
    public interface GameMaker {
        Simulation make(int level, boolean easy);
    }

    private final GameMaker newGame;
    private final Supplier<Demo> newDemo;
    private final Supplier<String> today;
    /** The input profiles the keys page offers, in the order it shows them (§19.1). */
    private final java.util.List<String> profiles;
    private Jukebox jukebox = Jukebox.SILENT;
    /** §21.4: the save slots; null when none are given (a test's shell), and the menus say so. */
    private SaveSlots saves;
    private int continueSlot;
    private wulf.data.QuickSaveData continueGame;
    /** §17.4, §17.9, §21.4: the line chosen on a menu page, the page Escape goes back to, and a word for the player. */
    private int cursor;
    private State back = State.TITLE;
    private String menuNote;
    private boolean heldUp;
    private boolean heldDown;

    private State state = State.TITLE;
    private long stateTick;
    private long rollTick;
    private boolean rollPaused;
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
    /** §14.9: the furthest job offered — from the ledger, or this session's practice — and the one the title starts. */
    private int unlocked;
    private int level;
    /** The job Mr Jones offers after this win's credits, or 0 for none. */
    private int offered;
    /** §17.6, {@code --endings}: the jobs whose endings are still to be shown, in turn; empty otherwise. */
    private final java.util.ArrayDeque<Integer> showing = new java.util.ArrayDeque<>();
    /** Whether {@code --endings} is under way: until the last ending is back at the title. */
    private boolean show;
    /** §17.8: whether the next game is in Easy mode; from the settings, switched on the title. */
    private boolean easy;

    /** A shell whose every game is the game itself. */
    public Shell(ShellConfig config, PlayerDb db, java.util.List<String> profiles, Supplier<Simulation> newGame,
                 Supplier<Demo> newDemo, Supplier<String> today) {
        this(config, db, profiles, n -> newGame.get(), newDemo, today);
    }

    /** A shell whose games ignore the mode chosen: Hard, as most tests want. */
    public Shell(ShellConfig config, PlayerDb db, java.util.List<String> profiles, IntFunction<Simulation> newGame,
                 Supplier<Demo> newDemo, Supplier<String> today) {
        this(config, db, profiles, (GameMaker) (n, e) -> newGame.apply(n), newDemo, today);
    }

    public Shell(ShellConfig config, PlayerDb db, java.util.List<String> profiles, GameMaker newGame,
                 Supplier<Demo> newDemo, Supplier<String> today) {
        this.config = config;
        this.profiles = java.util.List.copyOf(profiles);
        this.db = db;
        this.newGame = newGame;
        this.newDemo = newDemo;
        this.today = today;
        this.notice = db.notices().isEmpty() ? null : db.notices().get(0);
        this.unlocked = Math.min(db.stats().unlocked(), config.newGamePlus().levels().size() - 1);
        this.level = unlocked;   // the next game is the newest job; the title's key goes back
        this.easy = db.settings().easy();
    }

    /**
     * Gives the shell a voice (§18.3). Called once, before the first tick; the
     * title's tune starts with it.
     */
    public void jukebox(Jukebox box) {
        this.jukebox = box == null ? Jukebox.SILENT : box;
        this.jukebox.tune(tuneFor(state));
    }

    /** §17.6, {@code --new-game-plus}: job {@code n} open and chosen, for this session only. */
    public void practiseJob(int n) {
        level = Math.min(n, config.newGamePlus().levels().size() - 1);
        unlocked = Math.max(unlocked, level);
    }

    /**
     * §17.6, {@code --endings}: every job's ending in turn — each a game won on the spot, as
     * practice: the closing scene, the tally, the roll, and Mr Jones's offer of the next job if
     * there is one — then the title. Nothing reaches the ledger or the table.
     */
    public void showEndings() {
        showing.clear();
        for (int n = 0; n < config.newGamePlus().levels().size(); n++) {
            showing.add(n);
        }
        show = true;
    }

    /** Whether {@code --endings} is under way. */
    public boolean showingEndings() {
        return show;
    }

    /** §21.4: the save slots, with what they need. Without them the menus offer nothing to load or save. */
    public void saveSlots(SaveSlots slots) {
        this.saves = slots;
        lookForAGame();
    }

    /** §17.2: the newest saved game, which fire on the title goes on with; 0 for none. */
    private void lookForAGame() {
        continueSlot = saves == null ? 0 : saves.newest(config.saves().slots()).orElse(0);
        continueGame = continueSlot == 0 ? null : saves.peek(continueSlot).orElse(null);
    }

    /** §17.2: what is in {@link #continueSlot}, read once when the title came up; null for none. */
    public wulf.data.QuickSaveData continueGame() {
        return continueSlot == 0 ? null : continueGame;
    }

    /** §17.2: the slot fire on the title loads, from 1; 0 when it starts a new game. */
    public int continueSlot() {
        return continueSlot;
    }

    // ---------------------------------------------------------------- the machine

    public void tick(InputState in, MenuInput menu, boolean dev) {
        stateTick++;
        switch (state) {
            case TITLE -> {
                if (!showing.isEmpty()) {
                    showNextEnding();
                } else {
                    show = false;   // the last ending shown: the title as ever
                    title(in, menu);
                }
            }
            case ATTRACT -> attract(in, menu);
            case CREDITS, HISCORES, STATS -> page(in);
            case KEY_CONFIG -> keyConfig(in, menu);
            case SOUND -> sound(in, menu);
            case PLAYING -> playing(in, menu, dev);
            case GAME_OVER -> gameOver(in);
            case ENDING -> ending(in);
            case TALLY -> tally(in);
            case ROLL -> roll(in);
            case OFFER -> offer(in);
            case HISCORE_ENTRY -> hiScoreEntry(in);
            case PAUSED -> paused(in);
            case SAVE_GAME -> saveGame(in);
            case LOAD_GAME -> loadGame(in);
            case OPTIONS -> options(in);
        }
    }

    private void title(InputState in, MenuInput menu) {
        if (in.quitPressed()) {
            quit = true;
            return;
        }
        if (menu.typed(config.difficulty().key()) || menu.pad(MenuInput.Pad.DIFFICULTY)) {
            easy = !easy;   // §17.8: Hard and Easy in turn, kept in the settings
            db.putSettings(db.settings().withEasy(easy));
            jukebox.sfx(SFX_MOVE);
            idleTicks = 0;
            return;
        }
        if (unlocked > 0 && menu.typed(config.newGamePlus().key())) {
            level = (level + 1) % (unlocked + 1);   // §14.9: through the jobs unlocked, and back to the first
            jukebox.sfx(SFX_MOVE);
            idleTicks = 0;
            return;
        }
        String screen = TitleScreen.screenFor(config.title().menu(), menu);
        if (screen != null) {
            jukebox.sfx(SFX_PICK);
            openMenu(State.valueOf(screen), State.TITLE);
            return;
        }
        if (menu.typed(config.title().newGameKey()) || menu.pad(MenuInput.Pad.NEW_GAME)) {
            jukebox.sfx(SFX_PICK);
            startGame();
            return;
        }
        if (in.firePressed()) {
            jukebox.sfx(SFX_PICK);
            if (continueSlot > 0) {
                goOn(continueSlot);   // §17.2: a saved game waits, and fire goes on with it
            } else {
                startGame();
            }
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

    private void playing(InputState in, MenuInput menu, boolean dev) {
        if (menu.typed(config.map().keyFor(db.settings().inputProfile())) || menu.pad(MenuInput.Pad.MAP)) {
            session.toggleMap();   // §17.7
            jukebox.sfx(SFX_PICK);
        }
        if (in.quitPressed()) {
            // Escape leaves the jungle rather than the program: the run is banked as if it ended.
            endRun();
            return;
        }
        if (in.pausePressed() && !session.revealing() && !session.mapOpen() && !session.over() && !session.won()) {
            jukebox.sfx(SFX_PICK);
            openMenu(State.PAUSED, State.PLAYING);   // §17.4: the game stands still under the menu
            return;
        }
        boolean wasRevealing = session.revealing();
        session.tick(in, dev);
        if (session.revealing() != wasRevealing) {
            // §14.6, as the original played one: a tune under the amulet reveal, and silence again
            // after — the jungle itself has no music (§18.3). The user approved this one exception.
            jukebox.tune(session.revealing() ? wulf.data.MusicData.REVEAL : tuneFor(State.PLAYING));
        }
        if (session.won()) {
            unlock(session.sim());
            go(State.ENDING);
        } else if (session.over()) {
            go(State.GAME_OVER);
        }
    }

    // ---------------------------------------------------------------- the menus (§17.4, §17.9, §21.4)

    private void openMenu(State page, State from) {
        cursor = 0;
        back = from;
        menuNote = null;
        heldUp = false;
        heldDown = false;
        go(page);
    }

    /** Up and down move the cursor through {@code lines}, wrapping, on the press and not the hold. */
    private void steer(InputState in, int lines) {
        int dy = (in.down() && !heldDown ? 1 : 0) - (in.up() && !heldUp ? 1 : 0);
        heldUp = in.up();
        heldDown = in.down();
        if (dy != 0 && lines > 0) {
            cursor = Math.floorMod(cursor + dy, lines);
            menuNote = null;
            jukebox.sfx(SFX_MOVE);
        }
    }

    private void paused(InputState in) {
        java.util.List<ShellConfig.Entry> items = config.pause().items();
        steer(in, items.size());
        if (in.quitPressed() || in.pausePressed()) {
            go(State.PLAYING);
            return;
        }
        if (!in.firePressed()) {
            return;
        }
        jukebox.sfx(SFX_PICK);
        switch (items.get(cursor).id()) {
            case "resume" -> go(State.PLAYING);
            case "save" -> openMenu(State.SAVE_GAME, State.PAUSED);
            case "load" -> openMenu(State.LOAD_GAME, State.PAUSED);
            case "options" -> openMenu(State.OPTIONS, State.PAUSED);
            case "quit" -> endRun();   // as Escape from the jungle: the run is banked
            default -> { }
        }
    }

    /** Back a step: to the pause menu, on the line that came here, or to the title. */
    private void backOut() {
        State to = back;
        State from = state;
        if (to == State.PAUSED) {
            openMenu(State.PAUSED, State.PLAYING);
            java.util.List<ShellConfig.Entry> items = config.pause().items();
            String id = from == State.SAVE_GAME ? "save" : from == State.LOAD_GAME ? "load" : "options";
            for (int i = 0; i < items.size(); i++) {
                cursor = items.get(i).id().equals(id) ? i : cursor;
            }
        } else {
            go(State.TITLE);
        }
    }

    private void saveGame(InputState in) {
        steer(in, config.saves().slots());
        if (in.quitPressed()) {
            backOut();
        } else if (in.firePressed() && saves != null) {
            int n = cursor + 1;
            saves.save(session, n);
            jukebox.sfx(SFX_PICK);
            session.note(config.saves().saved().replace("{n}", Integer.toString(n)), config.saves().noteTicks());
            go(State.PLAYING);
        }
    }

    private void loadGame(InputState in) {
        steer(in, config.saves().slots());
        if (in.quitPressed()) {
            backOut();
            return;
        }
        if (!in.firePressed() || saves == null || stateTick < config.screenHoldTicks() && back == State.TITLE) {
            return;
        }
        int n = cursor + 1;
        SaveSlots.Loaded loaded = saves.load(n);
        if (!loaded.ok()) {
            menuNote = "EMPTY".equals(loaded.refusal())
                    ? config.saves().emptySlot().replace("{n}", Integer.toString(n)) : loaded.refusal();
            jukebox.sfx(SFX_MOVE);
            return;
        }
        jukebox.sfx(SFX_PICK);
        play(loaded, n);
    }

    /** §17.2: fire on the title, a saved game waiting; one that will not load says why, and fire then starts anew. */
    private void goOn(int n) {
        SaveSlots.Loaded loaded = saves.load(n);
        if (!loaded.ok()) {
            notice = config.saves().refused().replace("{n}", Integer.toString(n)).replace("{why}", loaded.refusal());
            continueSlot = 0;
            return;
        }
        play(loaded, n);
    }

    private void play(SaveSlots.Loaded loaded, int n) {
        session = new GameSession(loaded.sim(), revealFor(loaded.sim()), loaded.played());
        session.note(config.saves().loaded().replace("{n}", Integer.toString(n)), config.saves().noteTicks());
        go(State.PLAYING);
    }

    private void options(InputState in) {
        java.util.List<ShellConfig.Entry> items = config.options().items();
        steer(in, items.size());
        if (in.quitPressed()) {
            backOut();
            return;
        }
        int dx = (in.right() && !heldRight ? 1 : 0) - (in.left() && !heldLeft ? 1 : 0);
        heldLeft = in.left();
        heldRight = in.right();
        if (dx != 0 && items.get(cursor).id().equals("volume")) {
            Settings s = db.settings();
            int volume = Math.max(0, Math.min(100, s.audio().volumePercent() + dx * config.sound().volumeStepPercent()));
            sounded(s.withAudio(s.audio().enabled(), volume));
            return;
        }
        if (!in.firePressed() || stateTick < config.screenHoldTicks() && back == State.TITLE) {
            return;
        }
        String id = items.get(cursor).id();
        if (id.equals("back")) {
            jukebox.sfx(SFX_PICK);
            backOut();
            return;
        }
        if (id.equals("volume")) {
            return;   // the arrows turn it, not fire
        }
        if (id.equals("sound")) {
            Settings s = db.settings();
            sounded(s.withAudio(!s.audio().enabled(), s.audio().volumePercent()));
            return;
        }
        db.putSettings(id.equals("eightBit") ? eightBit(db.settings(), config.options().eightBit())
                : toggled(db.settings(), id));
        jukebox.sfx(SFX_PICK);
    }

    /**
     * §17.9: 8-BIT MODE — every switch of the original's look ({@code options.eightBit}) on
     * together, or, when they all are, off together.
     */
    static Settings eightBit(Settings s, java.util.List<String> switches) {
        boolean on = !eightBitOn(s, switches);
        for (String id : switches) {
            if (optionOn(s, id) != on) {
                s = toggled(s, id);
            }
        }
        return s;
    }

    /** Whether 8-BIT MODE shows on: every one of its switches is. */
    public static boolean eightBitOn(Settings s, java.util.List<String> switches) {
        return switches.stream().allMatch(id -> optionOn(s, id));
    }

    /** §21.3: the sound changed from the options — written, and heard at once. */
    private void sounded(Settings after) {
        db.putSettings(after);
        jukebox.settings(after.audio().enabled(), after.audio().volumePercent());
        jukebox.tune(menuInGame() ? null : tuneFor(state));   // over a game the menus stay silent (§18.3)
        jukebox.sfx(SFX_MOVE);
    }

    /** §17.9: an options switch flipped. */
    static Settings toggled(Settings s, String id) {
        Settings.Crt c = s.crt();
        return switch (id) {
            case "scanlines" -> s.withCrt(new Settings.Crt(!c.scanlines(), c.glow(), c.attributeClash()));
            case "attributeClash" -> s.withCrt(new Settings.Crt(c.scanlines(), c.glow(), !c.attributeClash()));
            default -> s.withFeature(id, !optionOn(s, id));
        };
    }

    /** §17.9: whether an options switch is on. */
    public static boolean optionOn(Settings s, String id) {
        return switch (id) {
            case "sound" -> s.audio().enabled();
            case "scanlines" -> s.crt().scanlines();
            case "attributeClash" -> s.crt().attributeClash();
            default -> Boolean.TRUE.equals(s.features().get(id));
        };
    }

    private void gameOver(InputState in) {
        if (continuing()) {
            // §17.5: fire goes on, where he fell; escape, or the time running out, banks the run.
            if (in.quitPressed() || stateTick >= config.gameOver().continueTicks()) {
                endRun();
            } else if (stateTick >= config.screenHoldTicks() && in.firePressed()) {
                session.carryOn(in);
                if (!session.over()) {
                    jukebox.sfx(SFX_PICK);
                    go(State.PLAYING);
                }
            }
            return;
        }
        boolean skipped = stateTick >= config.screenHoldTicks() && in.firePressed();
        if (skipped || stateTick >= config.gameOver().holdTicks()) {
            endRun();
        }
    }

    /** §17.5: whether the game-over screen offers to go on: the game has continues left. */
    public boolean continuing() {
        return state == State.GAME_OVER && session != null && session.sim().continuesLeft() > 0;
    }

    /**
     * §14.7: the closing scene. Its tune comes in on its own tick, after the words; fire goes on
     * to the tally once the prompt is up. Escape banks the run, as everywhere else.
     */
    private void ending(InputState in) {
        ShellConfig.Ending e = config.endingFor(session.sim().level());   // §14.9: each job its own
        if (stateTick == Math.max(1, e.tuneAt())) {
            jukebox.tune(e.tune());
        }
        if (in.quitPressed()) {
            endRun();
        } else if (stateTick >= e.promptAt() && in.firePressed()) {
            go(State.TALLY);
        }
    }

    private void tally(InputState in) {
        // A beat before fire counts, so the press that won the game cannot skip the tally.
        if (in.quitPressed()) {
            endRun();
        } else if (stateTick >= config.tally().holdTicks() && in.firePressed()) {
            go(State.ROLL);
        }
    }

    /**
     * §14.7: the roll rises on its own; fire stops it to read and starts it again, and Escape
     * skips to what comes after it — Mr Jones's offer if he has one, else the run banked.
     */
    private void roll(InputState in) {
        if (stateTick >= config.screenHoldTicks() && in.firePressed()) {
            rollPaused = !rollPaused;
            jukebox.sfx(SFX_MOVE);
        }
        if (!rollPaused) {
            rollTick++;
        }
        if (in.quitPressed() || rollTick >= config.credits().rollTicks()) {
            rollPaused = false;
            if (offered > 0) {
                go(State.OFFER);
            } else {
                endRun();
            }
        }
    }

    /** How far the credits have rolled (§14.7): ticks, not counting those stood still. */
    public long rollTick() {
        return rollTick;
    }

    /** Whether the credits are stopped to read (§14.7). */
    public boolean rollPaused() {
        return rollPaused;
    }

    /** §14.9: Mr Jones has a new job; fire, once his words are out, banks the run. */
    private void offer(InputState in) {
        int promptAt = config.newGamePlus().levels().get(offered).offer().promptAt();
        if (in.quitPressed() || stateTick >= promptAt && in.firePressed()) {
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
                    pending.ticks(), pending.date(), pending.easy());
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

    private void showNextEnding() {
        int job = showing.poll();
        Simulation sim = newGame.make(job, easy);
        sim.winForShow();
        session = new GameSession(sim, revealFor(sim));
        go(State.PLAYING);   // the first tick sees the win and goes to the ending
    }

    private void startGame() {
        Simulation sim = newGame.make(level, easy);
        session = new GameSession(sim, revealFor(sim));
        go(State.PLAYING);
    }

    /** The reveal's verses for {@code sim}'s job: the game's own, or one a piece of a later job's (§14.9). */
    private ShellConfig.AmuletReveal revealFor(Simulation sim) {
        ShellConfig.AmuletReveal r = config.amuletReveal();
        java.util.List<ShellConfig.Job> jobs = config.newGamePlus().levels();
        if (sim.level() <= 0 || sim.level() >= jobs.size()) {
            return r;
        }
        return new ShellConfig.AmuletReveal(r.skipAfterTicks(), r.scale(), r.prompt(), jobs.get(sim.level()).verses());
    }

    /**
     * §14.9: winning a job opens the next, if there is one not yet open. It is kept in the ledger
     * — but a practice win opens it for this session only — and the next game from the title is it.
     */
    private void unlock(Simulation sim) {
        int next = sim.level() + 1;
        offered = 0;
        if (show) {
            // --endings: every offer is shown, whatever is open; nothing is unlocked.
            offered = next < config.newGamePlus().levels().size() ? next : 0;
            return;
        }
        if (next >= config.newGamePlus().levels().size() || next <= unlocked) {
            return;
        }
        unlocked = next;
        level = next;
        offered = next;
        if (!sim.practice()) {
            db.putStats(db.stats().unlocking(next));
        }
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
        offered = 0;
        Simulation sim = session.sim();
        boolean escaped = session.won();
        if (sim.practice()) {
            // §17.6: --lives, --infinite-lives, --god — a practice run is not a run the table can rank.
            session = null;
            go(State.TITLE);
            return;
        }
        db.putStats(recordGame(db.stats(), sim, escaped));
        HighScores.Entry row = new HighScores.Entry("", sim.bestScore(), sim.quest().quarters(), sim.tick(),
                today.get(), sim.easy());   // §17.5: the run's best, before or after a continue
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
        return before.afterGame(sim.bestScore(), escaped, sim.tick(), sim.roomsEntered(), sim.quest().quarters(),
                sim.deaths(), sim.wulf().appearances(), sim.wulf().evasions(),
                counts(sim.killsBySpecies()), counts(sim.deathsByCause()), counts(sim.orchidsByColour()));
    }

    private static java.util.Map<String, Long> counts(java.util.Map<String, Integer> from) {
        java.util.Map<String, Long> out = new java.util.LinkedHashMap<>();
        from.forEach((k, v) -> out.put(k, (long) v));
        return out;
    }

    private void go(State next) {
        if (next == State.TITLE && state != State.TITLE) {
            lookForAGame();   // a game saved, or quit, since: the title offers the newest
        }
        state = next;
        stateTick = 0;
        rollTick = 0;
        rollPaused = false;
        // The menus over a game are as silent as the jungle under them (§18.3).
        boolean inGame = next == State.PAUSED || next == State.SAVE_GAME
                || back == State.PAUSED && (next == State.LOAD_GAME || next == State.OPTIONS);
        jukebox.tune(inGame ? null : tuneFor(next));
    }

    /**
     * §18.3: the title's theme over every menu screen, a fanfare for the arch, a
     * descending figure for the end — and in the jungle, nothing. Silence is the
     * tension; do not put music behind {@code PLAYING}. The closing scene starts silent
     * too: its own tune comes in on its tick (§14.7).
     */
    static String tuneFor(State state) {
        return switch (state) {
            case PLAYING, ENDING, OFFER -> null;
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

    /** §14.9: the job the title starts, and the furthest one open. */
    public int level() {
        return level;
    }

    /** The line chosen on the menu showing (§17.4, §17.9, §21.4). */
    public int cursor() {
        return cursor;
    }

    /** Whether the menu showing is over a game: drawn over the frozen jungle, not a page of its own. */
    public boolean menuInGame() {
        return state == State.PAUSED || state == State.SAVE_GAME || back == State.PAUSED;
    }

    /** A word for the player on the menu showing — a slot that is empty, a save refused — or null. */
    public String menuNote() {
        return menuNote;
    }

    /** The save slots, for the lists to show what is in them; null when there are none. */
    public SaveSlots saveSlots() {
        return saves;
    }

    /** Whether the next game from the title is in Easy mode (§17.8). */
    public boolean easy() {
        return easy;
    }

    public int unlocked() {
        return unlocked;
    }

    /** The job Mr Jones is offering on the {@link State#OFFER} screen; 0 otherwise. */
    public int offered() {
        return offered;
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
