package wulf.tools;

import com.studiohartman.jamepad.Configuration;
import com.studiohartman.jamepad.ControllerManager;
import com.studiohartman.jamepad.ControllerState;
import java.util.ArrayList;
import java.util.List;

/**
 * What SDL takes for a gamepad on this machine, and everything each one reports
 * (AGENTS.md §19.3): every device it finds, every change in its buttons and sticks,
 * and any read slow enough to stall a tick. For a player with a pad, or a machine
 * where something that is not one gets taken for one.
 *
 * <pre>./run.sh pad [SECONDS]</pre>
 */
public final class PadProbe {

    private static final int MAX_PADS = 4;
    private static final int TICK_MS = 20;
    /** A read this slow is most of a tick at 50 a second. */
    private static final long SLOW_MICROS = 5_000;

    private PadProbe() {
    }

    public static void main(String[] args) throws InterruptedException {
        int seconds = args.length > 0 ? Integer.parseInt(args[0]) : 20;
        Configuration c = new Configuration();
        c.maxNumControllers = MAX_PADS;
        ControllerManager m = new ControllerManager(c, "/wulf/input/pad_mappings.txt");
        try {
            m.initSDLGamepad();
        } catch (IllegalStateException | LinkageError e) {
            System.out.println("SDL did not start: " + e);
            return;
        }
        System.out.println("Watching for " + seconds + " s. Press keys, move the mouse, touch any pad.");
        String[] last = new String[MAX_PADS];
        long start = System.nanoTime();
        long slowest = 0;
        int reads = 0;
        while (System.nanoTime() - start < seconds * 1_000_000_000L) {
            long before = System.nanoTime();
            List<ControllerState> states = new ArrayList<>();
            for (int i = 0; i < MAX_PADS; i++) {
                states.add(m.getState(i));
            }
            long micros = (System.nanoTime() - before) / 1_000;
            reads++;
            slowest = Math.max(slowest, micros);
            long at = (System.nanoTime() - start) / 1_000_000;
            if (micros > SLOW_MICROS) {
                System.out.printf("%6d ms  slow read: %d us%n", at, micros);
            }
            for (int i = 0; i < MAX_PADS; i++) {
                String now = describe(states.get(i));
                if (!now.equals(last[i])) {
                    System.out.printf("%6d ms  pad %d: %s%n", at, i, now);
                    last[i] = now;
                }
            }
            Thread.sleep(TICK_MS);
        }
        System.out.println(reads + " reads, the slowest " + slowest + " us");
        m.quitSDLGamepad();
    }

    private static String describe(ControllerState s) {
        if (!s.isConnected) {
            return "none";
        }
        List<String> held = new ArrayList<>();
        add(held, s.a, "A");
        add(held, s.b, "B");
        add(held, s.x, "X");
        add(held, s.y, "Y");
        add(held, s.back, "BACK");
        add(held, s.start, "START");
        add(held, s.guide, "GUIDE");
        add(held, s.lb, "LEFTBUMPER");
        add(held, s.rb, "RIGHTBUMPER");
        add(held, s.leftStickClick, "LEFTSTICK");
        add(held, s.rightStickClick, "RIGHTSTICK");
        add(held, s.dpadUp, "DPAD_UP");
        add(held, s.dpadDown, "DPAD_DOWN");
        add(held, s.dpadLeft, "DPAD_LEFT");
        add(held, s.dpadRight, "DPAD_RIGHT");
        return "\"" + s.controllerType + "\"  buttons " + held
                + "  left stick " + percent(s.leftStickX) + "," + percent(s.leftStickY)
                + "  right stick " + percent(s.rightStickX) + "," + percent(s.rightStickY)
                + "  triggers " + percent(s.leftTrigger) + "," + percent(s.rightTrigger);
    }

    /** Whole percent, in steps of 10, so a resting stick's jitter does not print a line a tick. */
    private static int percent(float v) {
        return Math.round(v * 10) * 10;
    }

    private static void add(List<String> into, boolean held, String name) {
        if (held) {
            into.add(name);
        }
    }
}
