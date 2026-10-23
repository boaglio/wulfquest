package wulf.input;

import com.studiohartman.jamepad.Configuration;
import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The gamepads themselves, through Jamepad and SDL2's game controller API (AGENTS.md §19.3).
 * SDL knows the layout of the common pads — Xbox, PlayStation, Switch — so {@code A} is the
 * bottom face button on every one of them.
 *
 * <p>The one class that touches native code, and it never takes the game down with it: a
 * library that will not load, an SDL that will not start, or a read that throws, and the
 * pad is simply gone for the session, said once on the console, while the keys play on.
 * Called only from the game thread, which Jamepad needs.
 */
public final class SdlPad implements GamepadInput.Source {

    /** SDL's mappings file, ours and empty: SDL's own built-in mappings are the ones used. */
    private static final String MAPPINGS = "/wulf/input/pad_mappings.txt";
    private static final int MAX_PADS = 4;
    private static final String LOG = "  gamepad      : ";

    private ControllerManager manager;
    private List<String> connected = List.of();

    private SdlPad(ControllerManager manager) {
        this.manager = manager;
    }

    /** SDL started, or {@link GamepadInput.Source#NONE} with the reason on the console. */
    public static GamepadInput.Source open() {
        try {
            Configuration c = new Configuration();
            c.maxNumControllers = MAX_PADS;
            ControllerManager m = new ControllerManager(c, MAPPINGS);
            m.initSDLGamepad();
            SdlPad pad = new SdlPad(m);
            pad.read();
            if (pad.manager != null && pad.connected.isEmpty()) {
                System.out.println(LOG + "none connected (one plugged in later is found)");
            }
            return pad;
        } catch (Exception | LinkageError e) {
            System.out.println(LOG + "off (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            return GamepadInput.Source.NONE;
        }
    }

    @Override
    public PadReading read() {
        if (manager == null) {
            return PadReading.NONE;
        }
        try {
            Set<String> buttons = new HashSet<>();
            int x = 0;
            int y = 0;
            List<String> names = new ArrayList<>();
            for (int i = 0; i < MAX_PADS; i++) {
                ControllerState s = manager.getState(i);
                if (!s.isConnected) {
                    continue;
                }
                names.add(s.controllerType);
                buttons(s, buttons);
                // Every pad steers; on each axis the one pushed furthest wins.
                x = furthest(x, Math.round(s.leftStickX * 100));
                y = furthest(y, Math.round(s.leftStickY * 100));
            }
            if (!names.equals(connected)) {
                System.out.println(LOG + (names.isEmpty() ? "none connected" : String.join(", ", names)));
                connected = names;
            }
            return new PadReading(buttons, x, y);
        } catch (Exception | LinkageError e) {
            System.out.println(LOG + "off after an error (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")");
            close();
            return PadReading.NONE;
        }
    }

    private static int furthest(int a, int b) {
        return Math.abs(b) > Math.abs(a) ? b : a;
    }

    private static void buttons(ControllerState s, Set<String> into) {
        add(into, s.a, "A");
        add(into, s.b, "B");
        add(into, s.x, "X");
        add(into, s.y, "Y");
        add(into, s.back, "BACK");
        add(into, s.start, "START");
        add(into, s.lb, "LEFTBUMPER");
        add(into, s.rb, "RIGHTBUMPER");
        add(into, s.leftStickClick, "LEFTSTICK");
        add(into, s.rightStickClick, "RIGHTSTICK");
        add(into, s.dpadUp, "DPAD_UP");
        add(into, s.dpadDown, "DPAD_DOWN");
        add(into, s.dpadLeft, "DPAD_LEFT");
        add(into, s.dpadRight, "DPAD_RIGHT");
    }

    private static void add(Set<String> into, boolean held, String name) {
        if (held) {
            into.add(name);
        }
    }

    @Override
    public void close() {
        if (manager == null) {
            return;
        }
        ControllerManager m = manager;
        manager = null;
        try {
            m.quitSDLGamepad();
        } catch (Exception | LinkageError e) {
            // closing anyway: nothing left to do with it
        }
    }
}
