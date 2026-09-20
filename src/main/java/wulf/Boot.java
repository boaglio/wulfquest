package wulf;

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import wulf.data.DataException;
import wulf.data.DisplayConfig;
import wulf.data.FontData;
import wulf.data.JsonDb;
import wulf.data.Palette;
import wulf.engine.GameLoop;
import wulf.input.InputMap;
import wulf.input.InputState;
import wulf.input.KeyboardInput;
import wulf.render.Fonts;
import wulf.render.Framebuffer;
import wulf.render.PanelPainter;
import wulf.render.RoomPainter;
import wulf.render.Scaler;
import wulf.render.SpriteBank;
import wulf.render.Window;
import wulf.sim.Ecosystem;
import wulf.sim.Simulation;
import wulf.audio.Mixer;
import wulf.data.PlayerDb;
import wulf.data.UserDataDir;
import wulf.engine.Replay;
import wulf.input.MenuInput;
import wulf.input.RecordedInput;
import wulf.ui.DataErrorScreen;
import wulf.ui.GameSession;
import wulf.ui.Jukebox;
import wulf.ui.Shell;
import wulf.ui.ShellPainter;
import wulf.world.Room;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;
import wulf.engine.ReplayRecorder;
import java.util.List;
import java.util.Locale;

/**
 * Entry point. See AGENTS.md §3 for the argument list and §24 for what each
 * milestone adds here.
 *
 * <p>M3: Ranger Vale walks the jungle. Movement, collision, flip-screen rooms,
 * the sabre, death and respawn. {@code --browse} keeps M2's room browser. A data
 * error anywhere shows {@link DataErrorScreen} instead of throwing.
 *
 * <p>No game value belongs in this file (AGENTS.md §27 rule 2).
 */
public final class Boot {

    private Boot() {
    }

    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        if (parsed.error() != null) {
            // A typo on the command line is a usage error, not a crash (EX_USAGE).
            System.err.println("wulfquest: " + parsed.error());
            System.err.println("run with --help for the options");
            System.exit(64);
            return;
        }
        if (parsed.help()) {
            Args.printUsage();
            return;
        }
        JsonDb db = new JsonDb(parsed.dataDir());
        try {
            new Boot().run(Content.load(db), parsed, db);
        } catch (DataException e) {
            reportDataError(db, parsed, e);
        }
    }

    private void run(Content c, Args parsed, JsonDb db) {
        System.out.println("Wulf Quest — M8");
        System.out.println("  content hash : " + c.db().contentHash());
        System.out.println("  files loaded : " + c.db().loaded().size());
        System.out.println("  map          : " + c.map().gridW() + "x" + c.map().gridH()
                + " rooms, start " + c.map().startRoom()
                + ", " + c.map().templateCount() + " templates ("
                + c.map().usedTemplateIds().size() + " used), "
                + c.map().sceneryIds().size() + " scenery objects, "
                + c.map().totalRoomPlacements() + " placements");
        System.out.println("  scenery      : " + c.scenery().ids().size() + " objects, "
                + c.sprites().all().size() + " sprites");
        System.out.println("  player       : " + c.player().displayName() + ", "
                + c.player().lives().start() + " lives, sprite '" + c.player().sprite() + "'");
        System.out.println("  creatures    : " + c.creatures().creatures().size() + " species across "
                + c.biomes().inUse().size() + " biomes");
        if (parsed.headless()) {
            System.out.println("  --headless: skipping the window");
            return;
        }
        if (parsed.browse()) {
            runBrowser(c, parsed);
        } else {
            runGame(c, parsed, db);
        }
    }

    /** What a window-backed mode needs. */
    private record Screen(Framebuffer fb, Scaler scaler, Window window) {
    }

    private static Screen openScreen(Content c, Args parsed, String title) {
        DisplayConfig d = c.display();
        int scale = d.scale().clamp(parsed.scale() > 0 ? parsed.scale() : d.scale().defaultScale());
        Framebuffer fb = new Framebuffer(d.canvas().w(), d.canvas().h());
        Scaler scaler = new Scaler(c.palette().toArgb(), fb.width(), fb.height(), scale);
        int border = c.palette().indexOf(d.border().idleColour());
        Window window = new Window(title, fb.width() * scale, fb.height() * scale,
                new Color(c.palette().toArgb()[border]));
        return new Screen(fb, scaler, window);
    }

    // ------------------------------------------------------------------ play

    private void runGame(Content base, Args parsed, JsonDb db) {
        if (parsed.debugEffect() != null && base.orchids().indexOfEffect(parsed.debugEffect()) < 0) {
            System.err.println("wulfquest: --debug-effect " + parsed.debugEffect() + " is not one of the orchids'"
                    + " effects; see data/entities/orchids.json");
            return;
        }
        PlayerDb player = new PlayerDb(parsed.userDir(), db, base.defaultScores(), base.defaultSettings());
        Content c = base.withSettings(player.settings());
        for (String notice : player.notices()) {
            System.out.println("  player db    : " + notice);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(player::flush, "wulf-playerdb-flush"));

        // --no-audio silences this run only; what the player chose on the sound page stands.
        Mixer mixer = new Mixer(c.sfx(), c.music(), !parsed.noAudio() && player.settings().audio().enabled(),
                player.settings().audio().volumePercent());
        mixer.warmUp();
        mixer.start();

        Screen screen = openScreen(c, parsed, "Wulf Quest");
        Fonts fonts = new Fonts("art/font/font.json", c.font());
        ShellPainter painter = new ShellPainter(c, c.shell(), player, fonts);

        WorldGrid world = new WorldGrid(c.rooms());
        RoomAddress start = parsed.room() != null ? parsed.room() : c.map().startRoom();
        KeyboardInput keys = new KeyboardInput(new InputMap(c.input()));
        screen.window().canvas().addKeyListener(keys);
        screen.window().canvas().addFocusListener(keys);
        Ecosystem eco = c.ecosystem();
        // The run seed decides every room's creatures (§6.3). Game n of a session uses seed + n.
        long firstSeed = parsed.seeded() ? parsed.seed() : System.nanoTime();
        AtomicLong nextSeed = new AtomicLong(firstSeed);
        Supplier<Simulation> newGame = () -> {
            Simulation fresh = Simulation.startingIn(c.player(), world, c.game().transition().freezeTicks(), start, eco,
                    nextSeed.getAndIncrement());
            if (parsed.debugEffect() != null) {
                fresh.giveEffect(parsed.debugEffect());   // --debug-effect: every game starts under it
            }
            fresh.sounds(mixer, c.sfx().cadence().footstepEveryTicks());
            return fresh;
        };
        Shell shell = new Shell(c.shell(), player, List.copyOf(c.input().profiles().keySet()), newGame,
                demoSource(c, world, eco), Boot::today);
        shell.jukebox(new Jukebox() {
            @Override
            public void tune(String id) {
                mixer.tune(id);
            }

            @Override
            public void sfx(String id) {
                mixer.play(id);
            }

            @Override
            public void settings(boolean on, int volumePercent) {
                mixer.setEnabled(on && !parsed.noAudio());
                mixer.setVolumePercent(volumePercent);
            }
        });

        ReplayRecorder recorder = parsed.record() == null ? null
                : new ReplayRecorder(nameOf(parsed.record()), c.db().contentHash(), firstSeed, start, parsed.dev());
        System.out.println("  run seed     : " + firstSeed + (parsed.seeded() ? "" : "   (replay with --seed " + firstSeed + ")"));
        System.out.println("  player db    : " + player.dir());
        System.out.println("  audio        : " + (mixer.enabled()
                ? c.sfx().sfx().size() + " sounds, " + c.music().tunes().size() + " tunes, "
                        + player.settings().audio().volumePercent() + "% volume"
                : parsed.noAudio() ? "off (--no-audio)" : "off (settings.json)"));
        System.out.println("  controls     : arrows or WASD walk, Space or Z swing, P pause, Esc leaves the jungle"
                + (parsed.dev() ? "   [dev: K kill, M collision mask, H summon the Wulf]" : ""));

        GameLoop loop = new GameLoop(c.game().tickHz(), c.game().maxCatchupTicks());
        loop.run(new GameLoop.Stepper() {
            @Override
            public void tick() {
                InputState in = keys.sample();
                MenuInput menu = keys.drainMenu();
                GameSession playing = shell.session();
                long before = playing == null ? 0 : playing.sim().tick();
                shell.tick(in, menu, parsed.dev());
                if (recorder != null && playing != null && shell.session() == playing
                        && playing.sim().tick() != before) {
                    recorder.record(in, playing.sim());   // the first game only: a restart is a new run
                }
            }

            @Override
            public void render() {
                painter.paint(screen.fb(), shell, parsed.dev());
                screen.window().present(screen.scaler().render(screen.fb()));
            }

            @Override
            public boolean running() {
                return !shell.quit() && screen.window().open();
            }
        });
        screen.window().close();
        mixer.close();
        player.close();
        if (recorder != null) {
            recorder.write(parsed.record());
            System.out.println("  recorded     : " + recorder.ticks() + " ticks to " + parsed.record());
        }
    }

    /**
     * Attract mode's demo (§17.2): the recorded run replayed into a fresh
     * simulation. A missing or unreadable replay simply means no demo — the
     * title screen waits instead, and the game is still playable.
     */
    private static Supplier<Shell.Demo> demoSource(Content c, WorldGrid world, Ecosystem eco) {
        String file = c.shell().attract().replay();
        if (!Replay.findable(file)) {
            System.out.println("  attract      : no " + file + "; the title screen will wait instead");
            return null;
        }
        return () -> {
            try {
                Replay replay = Replay.readAnywhere(file);
                Simulation sim = Simulation.startingIn(c.player(), world, c.game().transition().freezeTicks(),
                        replay.start(), eco, replay.seed());
                RecordedInput input = replay.recorded();
                return new Shell.Demo(sim, input.cursor(), input.ticks(), replay.dev());
            } catch (RuntimeException e) {
                System.err.println("wulfquest: cannot play " + file + " as the attract demo: " + e.getMessage());
                return null;
            }
        };
    }

    /** The date a hi-score row is stamped with (§21.2). Outside the simulation, so a clock is fine here. */
    private static String today() {
        return java.time.LocalDate.now().toString();
    }

    private static String nameOf(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".json") ? name.substring(0, name.length() - 5) : name;
    }

    // ------------------------------------------------------------------ room browser (M2)

    private void runBrowser(Content c, Args parsed) {
        DisplayConfig display = c.display();
        Palette palette = c.palette();
        Screen screen = openScreen(c, parsed, "Wulf Quest — room browser");
        PanelPainter panel = new PanelPainter(display, new Fonts("art/font/font.json", c.font()), palette, null, List.of());
        RoomPainter rooms = new RoomPainter(display, new SpriteBank(c.sprites()), c.scenery(), palette.indexOf("black"));
        int border = palette.indexOf(display.border().idleColour());
        int frameColour = palette.indexOf("blue");
        int maskColour = palette.indexOf("brightRed");

        AtomicReference<RoomAddress> current =
                new AtomicReference<>(parsed.room() != null ? parsed.room() : c.map().startRoom());
        AtomicBoolean showMask = new AtomicBoolean(false);
        AtomicBoolean quit = new AtomicBoolean(false);

        System.out.println("  room browser : arrows move between rooms, [ ] or PgUp/PgDn step through all 256,");
        System.out.println("                 M shows the collision mask, Esc quits");

        screen.window().canvas().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                RoomAddress r = current.get();
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> quit.set(true);
                    case KeyEvent.VK_LEFT -> current.set(step(r, -1, 0));
                    case KeyEvent.VK_RIGHT -> current.set(step(r, 1, 0));
                    case KeyEvent.VK_UP -> current.set(step(r, 0, -1));
                    case KeyEvent.VK_DOWN -> current.set(step(r, 0, 1));
                    case KeyEvent.VK_PAGE_UP -> current.set(linear(r, -1));
                    case KeyEvent.VK_PAGE_DOWN -> current.set(linear(r, 1));
                    case KeyEvent.VK_M -> showMask.set(!showMask.get());
                    default -> {
                        // By character, not key code: bracket keys sit elsewhere on ABNT2 and other layouts.
                        char ch = e.getKeyChar();
                        if (ch == '[' || ch == ',') {
                            current.set(linear(r, -1));
                        } else if (ch == ']' || ch == '.') {
                            current.set(linear(r, 1));
                        }
                    }
                }
            }
        });

        GameLoop loop = new GameLoop(c.game().tickHz(), c.game().maxCatchupTicks());
        loop.run(new GameLoop.Stepper() {
            @Override
            public void tick() {
                // the browser has no simulation
            }

            @Override
            public void render() {
                Room room = c.rooms().room(current.get());
                Framebuffer fb = screen.fb();
                fb.clear(border);
                DisplayConfig.Playfield f = display.playfield();
                fb.drawRect(f.x() - 1, f.y() - 1, f.w() + 2, f.h() + 2, frameColour);
                rooms.paint(fb, room);
                if (showMask.get()) {
                    rooms.paintMask(fb, room, maskColour);
                }
                String message = "ROOM " + room.address() + "  TYPE " + room.type()
                        + "  WALK " + room.mask().walkablePercent() + "%" + (showMask.get() ? "  MASK" : "");
                panel.paint(fb, 0L, 0L, 5, 0, -1, 0, message);
                screen.window().present(screen.scaler().render(fb));
            }

            @Override
            public boolean running() {
                return !quit.get() && screen.window().open();
            }
        });
        screen.window().close();
    }

    static RoomAddress step(RoomAddress r, int dx, int dy) {
        int col = Math.max(0, Math.min(RoomAddress.GRID_W - 1, r.col() + dx));
        int row = Math.max(0, Math.min(RoomAddress.GRID_H - 1, r.row() + dy));
        return new RoomAddress(col, row);
    }

    static RoomAddress linear(RoomAddress r, int delta) {
        int total = RoomAddress.GRID_W * RoomAddress.GRID_H;
        int i = Math.floorMod(r.index() + delta, total);
        return new RoomAddress(i % RoomAddress.GRID_W, i / RoomAddress.GRID_W);
    }

    // ------------------------------------------------------------------ data errors

    private static void reportDataError(JsonDb db, Args parsed, DataException e) {
        System.err.println();
        System.err.println("DATA ERROR");
        System.err.println("  file    : " + e.file());
        System.err.println("  pointer : " + e.pointer());
        System.err.println("  problem : " + e.detail());
        System.err.println();
        if (parsed.headless()) {
            System.exit(2);
        }
        try {
            showDataErrorWindow(db, parsed, e);
        } catch (RuntimeException fatal) {
            // The palette or font is itself broken; the console report above stands.
            System.err.println("(cannot render the DATA ERROR screen: " + fatal.getMessage() + ")");
        }
        System.exit(2);
    }

    private static void showDataErrorWindow(JsonDb db, Args parsed, DataException error) {
        Palette palette = db.load("art/palette", Palette.class);
        Fonts fonts = new Fonts("art/font/font.json", db.load("art/font/font", FontData.class));
        DisplayConfig display = db.load("config/display", DisplayConfig.class);

        Framebuffer fb = new Framebuffer(display.canvas().w(), display.canvas().h());
        int scale = display.scale().clamp(parsed.scale() > 0 ? parsed.scale() : display.scale().defaultScale());
        Scaler scaler = new Scaler(palette.toArgb(), fb.width(), fb.height(), scale);
        new DataErrorScreen(fonts.small(), palette).paint(fb, error);

        Window window = new Window("Wulf Quest — DATA ERROR", fb.width() * scale, fb.height() * scale, Color.BLACK);
        AtomicBoolean quit = new AtomicBoolean(false);
        window.canvas().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                quit.set(true);
            }
        });
        while (!quit.get() && window.open()) {
            window.present(scaler.render(fb));
            try {
                Thread.sleep(50L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        window.close();
    }

    // ------------------------------------------------------------------ arguments

    /**
     * Command-line arguments (AGENTS.md §3.1). A malformed argument never throws:
     * it comes back as {@link #error()}, which {@link #main} reports and exits 64.
     */
    record Args(Path dataDir, Path userDir, int scale, RoomAddress room, long seed, boolean seeded, Path record,
                String debugEffect, boolean headless, boolean browse, boolean dev, boolean noAudio, boolean help,
                String error) {

        static Args parse(String[] argv) {
            Path dataDir = defaultDataDir();
            Path userDir = UserDataDir.resolve();
            int scale = 0;
            RoomAddress room = null;
            long seed = 0;
            boolean seeded = false;
            Path record = null;
            String debugEffect = null;
            boolean headless = false;
            boolean browse = false;
            boolean dev = false;
            boolean noAudio = false;
            boolean help = false;
            try {
                for (int i = 0; i < argv.length; i++) {
                    switch (argv[i]) {
                        case "--dev" -> dev = true;
                        case "--headless", "--no-window" -> headless = true;
                        case "--browse" -> browse = true;
                        case "--no-audio" -> noAudio = true;
                        case "--help", "-h" -> help = true;
                        case "--scale" -> scale = number(argv, ++i, "--scale");
                        case "--room" -> room = roomArg(argv, ++i);
                        case "--record" -> record = Path.of(value(argv, ++i, "--record"));
                        case "--debug-effect" -> debugEffect = value(argv, ++i, "--debug-effect").toUpperCase(Locale.ROOT);
                        case "--seed" -> {
                            seed = longNumber(argv, ++i, "--seed");
                            seeded = true;
                        }
                        case "--data-dir" -> dataDir = Path.of(value(argv, ++i, "--data-dir"));
                        case "--user-dir" -> userDir = Path.of(value(argv, ++i, "--user-dir"));
                        default -> throw new IllegalArgumentException("unknown option: " + argv[i]);
                    }
                }
            } catch (IllegalArgumentException e) {
                return new Args(dataDir, userDir, scale, room, seed, seeded, record, debugEffect, headless, browse,
                        dev, noAudio, help, e.getMessage());
            }
            return new Args(dataDir, userDir, scale, room, seed, seeded, record, debugEffect, headless, browse, dev,
                    noAudio, help, null);
        }

        private static String value(String[] argv, int i, String option) {
            // "--room --headless" is a forgotten value, not a room called "--headless".
            if (i >= argv.length || argv[i].startsWith("--")) {
                throw new IllegalArgumentException(option + " needs a value");
            }
            return argv[i];
        }

        private static int number(String[] argv, int i, String option) {
            String v = value(argv, i, option);
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(option + " expects a whole number, got '" + v + "'");
            }
        }

        private static long longNumber(String[] argv, int i, String option) {
            String v = value(argv, i, option);
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(option + " expects a whole number, got '" + v + "'");
            }
        }

        private static RoomAddress roomArg(String[] argv, int i) {
            String v = value(argv, i, "--room");
            try {
                return RoomAddress.parse(v);
            } catch (IllegalArgumentException e) {
                // Covers both "16,3" (off the map) and "a,b" (not numbers).
                throw new IllegalArgumentException(
                        "--room expects col,row with both from 0 to 15, got '" + v + "'");
            }
        }

        /** Prefers the working tree's {@code data/} so edits are picked up. */
        private static Path defaultDataDir() {
            Path local = Path.of("data");
            return Files.isDirectory(local) ? local : null;
        }

        static void printUsage() {
            System.out.println("""
                    Wulf Quest

                      --room C,R       start in this room (default: the start room, 8,10)
                      --seed N         run seed: the same seed gives the same creatures in every room
                      --record FILE    write the first game's input to a replay (§22.6) on exit
                      --debug-effect E start every game under an orchid's effect (§15.2)
                      --browse         the room browser instead of the game
                      --scale N        window scale (1..6)
                      --data-dir PATH  content database root (default: ./data, else the jar)
                      --user-dir PATH  player database: hi-scores, settings, stats (§21.1)
                      --no-audio       silence for this run; settings.json is not changed
                      --headless       load and validate the data, then exit
                      --dev            developer mode: K kills, M shows collision, room and
                                       position on the panel
                      --help           this message

                    Controls: arrows or WASD walk, Space or Z swing, P pause, Esc leaves the
                    jungle for the title screen, and quits from there.
                    """);
        }
    }
}
