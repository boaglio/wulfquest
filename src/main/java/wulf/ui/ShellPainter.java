package wulf.ui;

import wulf.Content;
import wulf.data.DisplayConfig;
import wulf.data.PlayerDb;
import wulf.data.ShellConfig;
import wulf.render.Fonts;
import wulf.render.Framebuffer;
import wulf.sim.Simulation;

/**
 * Draws whichever screen the {@link Shell} is on (AGENTS.md §17.1). The shell
 * decides; this decides nothing — which keeps the state machine testable with
 * no framebuffer and the screens testable with no state machine.
 */
public final class ShellPainter {

    private final ShellConfig config;
    private final PlayerDb db;
    private final DisplayConfig display;
    private final GamePainter game;
    private final Chrome chrome;
    private final TitleScreen title;
    private final HiScoreScreen hiScores;
    private final HiScoreEntryScreen hiScoreEntry;
    private final StatsScreen stats;
    private final KeyConfigScreen keys;
    private final SoundScreen sound;
    private final GameOverScreen gameOver;
    private final MenuScreen menus;
    private final TallyScreen tally;
    private final EndingScreen ending;
    private final IntroScreen intro;
    private final CreditsRoll roll;
    private final AmuletRevealScreen reveal;
    private final MapScreen map;
    private final Content content;
    private final int tickHz;

    public ShellPainter(Content content, ShellConfig config, PlayerDb db, Fonts fonts) {
        this.content = content;
        this.config = config;
        this.db = db;
        this.display = content.display();
        this.tickHz = content.game().tickHz();
        this.game = new GamePainter(content, fonts);
        this.chrome = new Chrome(fonts, content.palette());
        wulf.render.SpriteBank sprites = new wulf.render.SpriteBank(content.sprites());
        wulf.render.Sprite marker = sprites.get(config.title().marker().sprite());
        this.title = new TitleScreen(chrome, config.title(), content.palette(), config.newGamePlus(),
                config.difficulty(), marker);
        this.hiScores = new HiScoreScreen(chrome, config.hiScores(), config.difficulty().hiScoreMark(),
                content.palette().indexOf(config.difficulty().easyColour()));
        this.hiScoreEntry = new HiScoreEntryScreen(chrome, config.hiScores());
        this.stats = new StatsScreen(chrome, config.stats());
        this.keys = new KeyConfigScreen(chrome, config.keyConfig(), config.map());
        this.sound = new SoundScreen(chrome, config.sound());
        this.gameOver = new GameOverScreen(chrome, config.gameOver());
        this.menus = new MenuScreen(chrome, display, config, marker);
        this.tally = new TallyScreen(fonts.small(), content.palette(), config.tally());
        wulf.render.Sprite amulet = sprites.get(content.landmarks().amulet().sprite());
        java.util.List<String> framesBySlot = wulf.render.QuestPainter.framesBySlot(content.landmarks());
        this.reveal = new AmuletRevealScreen(chrome, display, amulet, framesBySlot, config.amuletReveal());
        this.intro = config.attract().intro() == null ? null
                : new IntroScreen(config.attract().intro(), chrome.big(), content.palette(), sprites);
        this.ending = new EndingScreen(config.ending(), chrome.big(), content.palette(), sprites, amulet, framesBySlot);
        this.roll = new CreditsRoll(config.credits(), chrome.big(), chrome.small(), content.palette());
        this.map = new MapScreen(display, content.rooms(), content.landmarks(), content.palette(), config.map(),
                sprites.get(config.map().quarterIcon().sprite()), sprites.get(config.map().exitIcon().sprite()),
                game.way());
    }

    public GamePainter game() {
        return game;
    }

    public void paint(Framebuffer fb, Shell shell, boolean dev) {
        game.retro(db.settings());   // §17.9: as the options page left them
        switch (shell.state()) {
            case PAUSED, SAVE_GAME, LOAD_GAME, OPTIONS, NEW_GAME -> {
                if (shell.menuInGame() && shell.session() != null) {
                    playing(fb, shell, dev);
                }
                menus.paint(fb, shell, db.settings());
            }
            case TITLE -> title.paint(fb, shell.stateTick(), shell.notice(), shell.titleLines(), shell.cursor(),
                    saved(shell), shell.easy(),
                    shell.unlocked() > 0 ? config.newGamePlus().levels().get(shell.level()) : null);
            case INTRO -> intro.paint(fb, shell.stateTick());
            case ATTRACT -> attract(fb, shell);
            case HISCORES -> hiScores.paint(fb, db.scores(), shell.highlight());
            case STATS -> stats.paint(fb, db.stats(), tickHz);
            case KEY_CONFIG -> keys.paint(fb, content.input(), db.settings().inputProfile());
            case SOUND -> sound.paint(fb, db.settings());
            case PLAYING -> playing(fb, shell, dev);
            case GAME_OVER -> {
                playing(fb, shell, dev);
                Simulation sim = shell.session().sim();
                int left = shell.continuing() ? sim.continuesLeft() : 0;
                long ticksLeft = Math.max(0, config.gameOver().continueTicks() - shell.stateTick());
                gameOver.paint(fb, display.playfield().y(), display.playfield().h(),
                        GameOverScreen.percentDone(sim.roomsVisited(), sim.quest().quarters(),
                                content.map().roomCount(), content.landmarks().lairs().size()),
                        left, (int) ((ticksLeft + tickHz - 1) / tickHz));
            }
            case ENDING -> ending.paint(fb, shell.stateTick(), config.endingFor(shell.session().sim().level()));
            case ROLL -> roll.paint(fb, shell.rollTick(), config.theEndFor(shell.session().sim().level()),
                    shell.rollPaused());
            case OFFER -> ending.paintOffer(fb, shell.stateTick(),
                    config.newGamePlus().levels().get(shell.offered()).offer());
            case TALLY -> {
                Simulation sim = shell.session().sim();
                tally.paint(fb, sim.quest(), sim.score(), db.scores().best(), (int) shell.stateTick());
            }
            case HISCORE_ENTRY -> hiScoreEntry.paint(fb, shell.entry(), shell.pending().score(),
                    db.scores().placeOf(shell.pending(), config.hiScores().size()), shell.stateTick());
        }
    }

    private void band(Framebuffer fb, int y, int h) {
        fb.fillRect(0, y, fb.width(), h, chrome.ground());
    }

    private void playing(Framebuffer fb, Shell shell, boolean dev) {
        GameSession session = shell.session();
        game.paint(fb, session.sim(), db.scores().best(),
                session.mapOpen() ? config.map().messageFor(db.settings().inputProfile()) : session.message(dev), session.showMask());
        if (session.mapOpen()) {
            map.paint(fb, session.sim(), shell.stateTick());   // §17.7
            return;
        }
        if (session.revealing()) {
            reveal.paint(fb, session.sim().quest().slotMask(), session.verse(),
                    session.revealWaitsForFire() ? session.revealPrompt() : null,
                    game.amulets().forLevel(session.sim().level()));   // §14.9: the job's own amulet
            return;
        }
        if (session.paused()) {
            // §17.4: the jungle stays visible, dropped to the non-bright palette.
            DisplayConfig.Playfield f = display.playfield();
            fb.dim(f.x(), f.y(), f.w(), f.h());
        }
    }

    /** §17.2: the demo behind a dimmed overlay, with a line saying how to stop it. */
    private void attract(Framebuffer fb, Shell shell) {
        Shell.Demo demo = shell.demo();
        if (demo == null) {
            title.paint(fb, shell.stateTick(), shell.notice());
            return;
        }
        game.paintWorld(fb, demo.sim(), false);
        DisplayConfig.Playfield f = display.playfield();
        fb.dim(f.x(), f.y(), f.w(), f.h());
        if (config.attract().dimEveryOtherRow()) {
            fb.stripe(f.x(), f.y(), f.w(), f.h(), chrome.ground());
        }
        // Both lines get their own band of ground, or the jungle behind them eats the letters.
        band(fb, f.y() + 20, chrome.big().glyphH() + 6);
        chrome.big().drawCentred(fb, config.title().wordmark(), 0, fb.width(), f.y() + 23, chrome.heading());
        int bannerY = f.y() + f.h() - 26;
        band(fb, bannerY - 3, chrome.small().glyphH() + 6);
        chrome.small().drawCentred(fb, config.attract().banner(), 0, fb.width(), bannerY, chrome.bright());
    }

    /** §17.2: the saved game the title's {@code continue} goes on with, as the load page lists it; null for none. */
    private String saved(Shell shell) {
        wulf.data.QuickSaveData d = shell.continueGame();
        return d == null ? null
                : config.title().continueDetail().replace("{line}", MenuScreen.slotLine(config.saves(), shell.continueSlot(), d));
    }
}
