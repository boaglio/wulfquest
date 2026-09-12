package wulf;

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import wulf.data.DataException;
import wulf.data.DisplayConfig;
import wulf.data.FontData;
import wulf.data.GameConfig;
import wulf.data.JsonDb;
import wulf.data.OriginalMap;
import wulf.data.OriginalMapRepository;
import wulf.data.Palette;
import wulf.engine.GameLoop;
import wulf.render.Fonts;
import wulf.render.Framebuffer;
import wulf.render.PanelPainter;
import wulf.render.Scaler;
import wulf.render.Window;
import wulf.ui.DataErrorScreen;

/**
 * Entry point. See AGENTS.md §3 for the argument list and §24 for what each
 * milestone adds here.
 *
 * <p>M1 boots the content database, validates it, and puts the border and the
 * status panel on screen. The playfield is still empty: rooms arrive in M2 and
 * the player in M3. A data error anywhere shows {@link DataErrorScreen}
 * instead of throwing.
 *
 * <p>No game value belongs in this file (AGENTS.md §27 rule 2).
 */
public final class Boot {

    private Boot() {
    }

    public static void main(String[] args) {
        Args parsed = Args.parse(args);
        if (parsed.help) {
            Args.printUsage();
            return;
        }
        JsonDb db = new JsonDb(parsed.dataDir);
        try {
            new Boot().run(db, parsed);
        } catch (DataException e) {
            reportDataError(db, parsed, e);
        }
    }

    private void run(JsonDb db, Args parsed) {
        GameConfig game = db.load("config/game", GameConfig.class);
        DisplayConfig display = db.load("config/display", DisplayConfig.class);
        Palette palette = db.load("art/palette", Palette.class);
        Fonts fonts = new Fonts("art/font/font.json", db.load("art/font/font", FontData.class));
        OriginalMapRepository map =
                new OriginalMapRepository(db.load("world/original_map", OriginalMap.class));

        System.out.println("Wulf Quest — M1");
        System.out.println("  content hash : " + db.contentHash());
        System.out.println("  files loaded : " + db.loaded());
        System.out.println("  map          : " + map.gridW() + "x" + map.gridH()
                + " rooms, start " + map.startRoom()
                + ", " + map.templateCount() + " templates ("
                + map.usedTemplateIds().size() + " used), "
                + map.sceneryIds().size() + " scenery objects, "
                + map.totalRoomPlacements() + " placements");

        if (parsed.headless) {
            System.out.println("  --headless: skipping the window");
            return;
        }

        int scale = display.scale().clamp(parsed.scale > 0 ? parsed.scale : display.scale().defaultScale());
        Framebuffer fb = new Framebuffer(display.canvas().w(), display.canvas().h());
        Scaler scaler = new Scaler(palette.toArgb(), fb.width(), fb.height(), scale);
        PanelPainter panelPainter = new PanelPainter(display, fonts, palette);
        int borderIndex = palette.indexOf(display.border().idleColour());
        int[] argb = palette.toArgb();

        Window window = new Window("Wulf Quest", scaler.render(fb).getWidth(),
                scaler.render(fb).getHeight(), new Color(argb[borderIndex]));
        AtomicBoolean quit = new AtomicBoolean(false);
        window.canvas().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    quit.set(true);
                }
            }
        });

        GameLoop loop = new GameLoop(game.tickHz(), game.maxCatchupTicks());
        loop.run(new GameLoop.Stepper() {
            @Override
            public void tick() {
                // M3 onwards: input sample and simulation step.
            }

            @Override
            public void render() {
                fb.clear(borderIndex);
                // The playfield is drawn from M2; outline it so the geometry is visible.
                fb.drawRect(display.playfield().x() - 1, display.playfield().y() - 1,
                        display.playfield().w() + 2, display.playfield().h() + 2,
                        palette.indexOf("blue"));
                panelPainter.paint(fb, 0L, 0L, 5, 0, -1, 0, null);
                window.present(scaler.render(fb));
            }

            @Override
            public boolean running() {
                return !quit.get() && window.open();
            }
        });
        window.close();
    }

    private static void reportDataError(JsonDb db, Args parsed, DataException e) {
        System.err.println();
        System.err.println("DATA ERROR");
        System.err.println("  file    : " + e.file());
        System.err.println("  pointer : " + e.pointer());
        System.err.println("  problem : " + e.detail());
        System.err.println();
        if (parsed.headless) {
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
        int scale = display.scale().clamp(parsed.scale > 0 ? parsed.scale : display.scale().defaultScale());
        Scaler scaler = new Scaler(palette.toArgb(), fb.width(), fb.height(), scale);
        new DataErrorScreen(fonts.small(), palette).paint(fb, error);

        Window window = new Window("Wulf Quest — DATA ERROR",
                fb.width() * scale, fb.height() * scale, Color.BLACK);
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

    /** Command-line arguments (AGENTS.md §3.1). */
    record Args(Path dataDir, int scale, boolean headless, boolean dev, boolean help) {

        static Args parse(String[] argv) {
            Path dataDir = defaultDataDir();
            int scale = 0;
            boolean headless = false;
            boolean dev = false;
            boolean help = false;
            for (int i = 0; i < argv.length; i++) {
                switch (argv[i]) {
                    case "--dev" -> dev = true;
                    case "--headless", "--no-window" -> headless = true;
                    case "--help", "-h" -> help = true;
                    case "--scale" -> scale = Integer.parseInt(argv[++i]);
                    case "--data-dir" -> dataDir = Path.of(argv[++i]);
                    default -> {
                        if (argv[i].startsWith("--")) {
                            System.err.println("unknown option: " + argv[i]);
                            help = true;
                        }
                    }
                }
            }
            return new Args(dataDir, scale, headless, dev, help);
        }

        /** Prefers the working tree's {@code data/} so edits are picked up. */
        private static Path defaultDataDir() {
            Path local = Path.of("data");
            return Files.isDirectory(local) ? local : null;
        }

        static void printUsage() {
            System.out.println("""
                    Wulf Quest

                      --scale N        window scale (1..6)
                      --data-dir PATH  content database root (default: ./data, else the jar)
                      --headless       load and validate the data, then exit
                      --dev            developer mode
                      --help           this message
                    """);
        }
    }

    // Kept so the M0 smoke behaviour still has a home in tests.
    static String version() {
        String v = Boot.class.getPackage().getImplementationVersion();
        return v == null ? "dev" : v;
    }
}
