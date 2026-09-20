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
    private final CreditsScreen credits;
    private final HiScoreScreen hiScores;
    private final HiScoreEntryScreen hiScoreEntry;
    private final StatsScreen stats;
    private final KeyConfigScreen keys;
    private final SoundScreen sound;
    private final GameOverScreen gameOver;
    private final TallyScreen tally;
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
        this.title = new TitleScreen(chrome, config.title(), content.palette());
        this.credits = new CreditsScreen(chrome, config.credits());
        this.hiScores = new HiScoreScreen(chrome, config.hiScores());
        this.hiScoreEntry = new HiScoreEntryScreen(chrome, config.hiScores());
        this.stats = new StatsScreen(chrome, config.stats());
        this.keys = new KeyConfigScreen(chrome, config.keyConfig());
        this.sound = new SoundScreen(chrome, config.sound());
        this.gameOver = new GameOverScreen(chrome, config.gameOver());
        this.tally = new TallyScreen(fonts.small(), content.palette());
    }

    public GamePainter game() {
        return game;
    }

    public void paint(Framebuffer fb, Shell shell, boolean dev) {
        switch (shell.state()) {
            case TITLE -> title.paint(fb, shell.stateTick(), db.scores().best(), shell.notice());
            case ATTRACT -> attract(fb, shell);
            case CREDITS -> credits.paint(fb);
            case HISCORES -> hiScores.paint(fb, db.scores(), shell.highlight());
            case STATS -> stats.paint(fb, db.stats(), tickHz);
            case KEY_CONFIG -> keys.paint(fb, content.input(), db.settings().inputProfile());
            case SOUND -> sound.paint(fb, db.settings());
            case PLAYING -> playing(fb, shell, dev);
            case GAME_OVER -> {
                playing(fb, shell, dev);
                gameOver.paint(fb, display.playfield().y(), display.playfield().h());
            }
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
        game.paint(fb, session.sim(), db.scores().best(), session.message(dev), session.showMask());
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
            title.paint(fb, shell.stateTick(), db.scores().best(), shell.notice());
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
}
