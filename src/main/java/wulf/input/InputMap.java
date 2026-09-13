package wulf.input;

import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import wulf.data.DataException;
import wulf.data.InputConfig;

/** Key names from {@code input.json} resolved to key codes, per action (AGENTS.md §19.1). */
public final class InputMap {

    public enum Action { UP, DOWN, LEFT, RIGHT, FIRE, PAUSE, QUIT, DEV_KILL, DEV_MASK, DEV_WULF }

    private static final String SOURCE = "data/config/input.json";

    private final int[][] codes = new int[Action.values().length][];

    public InputMap(InputConfig config) {
        InputConfig.Profile p = config.profiles().get(config.active());
        if (p == null) {
            throw new DataException(SOURCE, "/active",
                    "names profile '" + config.active() + "', which is not one of " + config.profiles().keySet());
        }
        String at = "/profiles/" + config.active();
        bind(Action.UP, p.up(), at + "/up");
        bind(Action.DOWN, p.down(), at + "/down");
        bind(Action.LEFT, p.left(), at + "/left");
        bind(Action.RIGHT, p.right(), at + "/right");
        bind(Action.FIRE, p.fire(), at + "/fire");
        bind(Action.PAUSE, p.pause(), at + "/pause");
        bind(Action.QUIT, p.quit(), at + "/quit");
        bind(Action.DEV_KILL, p.devKill(), at + "/devKill");
        bind(Action.DEV_MASK, p.devMask(), at + "/devMask");
        bind(Action.DEV_WULF, p.devWulf(), at + "/devWulf");
        rejectConflicts(at);
    }

    private void bind(Action action, List<String> names, String at) {
        int[] c = new int[names.size()];
        for (int i = 0; i < names.size(); i++) {
            c[i] = codeOf(names.get(i), at + "/" + i);
        }
        codes[action.ordinal()] = c;
    }

    /** One key doing two jobs would make the game unplayable in confusing ways; fail at load instead. */
    private void rejectConflicts(String at) {
        Map<Integer, Action> owner = new HashMap<>();
        for (Action a : Action.values()) {
            for (int code : codes[a.ordinal()]) {
                Action previous = owner.putIfAbsent(code, a);
                if (previous != null && previous != a) {
                    throw new DataException(SOURCE, at,
                            "binds " + KeyEvent.getKeyText(code) + " to both " + previous + " and " + a);
                }
            }
        }
    }

    public boolean held(Action action, Set<Integer> keysDown) {
        for (int code : codes[action.ordinal()]) {
            if (keysDown.contains(code)) {
                return true;
            }
        }
        return false;
    }

    static int codeOf(String name, String pointer) {
        return switch (name) {
            case "UP" -> KeyEvent.VK_UP;
            case "DOWN" -> KeyEvent.VK_DOWN;
            case "LEFT" -> KeyEvent.VK_LEFT;
            case "RIGHT" -> KeyEvent.VK_RIGHT;
            case "SPACE" -> KeyEvent.VK_SPACE;
            case "ESCAPE" -> KeyEvent.VK_ESCAPE;
            case "ENTER" -> KeyEvent.VK_ENTER;
            case "TAB" -> KeyEvent.VK_TAB;
            case "BACKSPACE" -> KeyEvent.VK_BACK_SPACE;
            default -> {
                if (name.length() == 1 && name.charAt(0) >= 'A' && name.charAt(0) <= 'Z') {
                    yield KeyEvent.VK_A + (name.charAt(0) - 'A');
                }
                if (name.length() == 1 && name.charAt(0) >= '0' && name.charAt(0) <= '9') {
                    yield KeyEvent.VK_0 + (name.charAt(0) - '0');
                }
                throw new DataException(SOURCE, pointer, "is '" + name + "', which is not a key name this build knows");
            }
        };
    }
}
