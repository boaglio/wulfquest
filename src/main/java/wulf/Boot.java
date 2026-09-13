package wulf;

import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import wulf.data.DataException;
import wulf.data.DisplayConfig;
import wulf.data.FontData;
import wulf.data.JsonDb;
import wulf.data.Palette;
import wulf.engine.GameLoop;
import wulf.render.Fonts;
import wulf.render.Framebuffer;
import wulf.render.PanelPainter;
import wulf.render.RoomPainter;
import wulf.render.Scaler;
import wulf.render.SpriteBank;
import wulf.render.Window;
import wulf.ui.DataErrorScreen;
import wulf.world.Room;
import wulf.world.RoomAddress;

/**
 * Entry point. See AGENTS.md §3 for the argument list and §24 for what each
 * milestone adds here.
 *
 * <p>M2 boots the whole content database — map, scenery, sprites, collision —
 * and opens a room browser over all 256 rooms. The player arrives in M3. A data
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
            new Boot().run(Content.load(db), parsed);
        } catch (DataException e) {
            reportDataError(db, parsed, e);
        }
    }

    private void run(Content c, Args parsed) {
        System.out.println("Wulf Quest — M2");
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

        if (parsed.headless()) {
            System.out.println("  --headless: skipping the window");
            return;
        }

        DisplayConfig display = c.display();
        Palette palette = c.palette();
        int scale = display.scale().clamp(parsed.scale() > 0 ? parsed.scale() : display.scale().defaultScale());
        Framebuffer fb = new Framebuffer(display.canvas().w(), display.canvas().h());
        Scaler scaler = new Scaler(palette.toArgb(), fb.width(), fb.height(), scale);
        Fonts fonts = new Fonts("art/font/font.json", c.font());
        PanelPainter panel = new PanelPainter(display, fonts, palette);
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

        Window window = new Window("Wulf Quest", fb.width() * scale, fb.height() * scale,
                new Color(palette.toArgb()[border]));
        window.canvas().addKeyListener(new KeyAdapter() {
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
                // M3 onwards: input sample and simulation step.
            }

            @Override
            public void render() {
                Room room = c.rooms().room(current.get());
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
                window.present(scaler.render(fb));
            }

            @Override
            public boolean running() {
                return !quit.get() && window.open();
            }
        });
        window.close();
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

    /**
     * Command-line arguments (AGENTS.md §3.1). A malformed argument never throws:
     * it comes back as {@link #error()}, which {@link #main} reports and exits 64.
     */
    record Args(Path dataDir, int scale, RoomAddress room, boolean headless, boolean dev, boolean help,
                String error) {

        static Args parse(String[] argv) {
            Path dataDir = defaultDataDir();
            int scale = 0;
            RoomAddress room = null;
            boolean headless = false;
            boolean dev = false;
            boolean help = false;
            try {
                for (int i = 0; i < argv.length; i++) {
                    switch (argv[i]) {
                        case "--dev" -> dev = true;
                        case "--headless", "--no-window" -> headless = true;
                        case "--help", "-h" -> help = true;
                        case "--scale" -> scale = number(argv, ++i, "--scale");
                        case "--room" -> room = roomArg(argv, ++i);
                        case "--data-dir" -> dataDir = Path.of(value(argv, ++i, "--data-dir"));
                        default -> throw new IllegalArgumentException("unknown option: " + argv[i]);
                    }
                }
            } catch (IllegalArgumentException e) {
                return new Args(dataDir, scale, room, headless, dev, help, e.getMessage());
            }
            return new Args(dataDir, scale, room, headless, dev, help, null);
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

                      --scale N        window scale (1..6)
                      --room C,R       open the room browser at this room (default: the start room)
                      --data-dir PATH  content database root (default: ./data, else the jar)
                      --headless       load and validate the data, then exit
                      --dev            developer mode
                      --help           this message
                    """);
        }
    }
}
