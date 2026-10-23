package wulf.input;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import wulf.data.DataException;
import wulf.data.InputConfig;

/**
 * The gamepad's buttons from {@code input.json} resolved per action, and the stick and
 * d-pad turned into the eight directions (AGENTS.md §19.3). Pure: it reads a
 * {@link PadReading}, so it is tested with no gamepad and no native code.
 */
public final class PadMap {

    public enum Action { FIRE, PAUSE, QUIT, MAP, NEW_GAME, DIFFICULTY }

    private static final String SOURCE = "data/config/input.json";
    private static final String AT = "/gamepad/buttons";

    /** What one screen reads, so a button may do one thing on each: in the jungle and on the title. */
    private static final List<List<Action>> SCREENS = List.of(
            List.of(Action.FIRE, Action.PAUSE, Action.QUIT, Action.MAP),
            List.of(Action.FIRE, Action.QUIT, Action.NEW_GAME, Action.DIFFICULTY));

    private final Map<Action, List<String>> buttons = new EnumMap<>(Action.class);
    private final int deadzone;
    private final boolean dpad;

    public PadMap(InputConfig.Gamepad config) {
        InputConfig.Buttons b = config.buttons();
        buttons.put(Action.FIRE, b.fire());
        buttons.put(Action.PAUSE, b.pause());
        buttons.put(Action.QUIT, b.quit());
        buttons.put(Action.MAP, b.map());
        buttons.put(Action.NEW_GAME, b.newGame());
        buttons.put(Action.DIFFICULTY, b.difficulty());
        deadzone = config.deadzonePercent();
        dpad = config.dpadAsDirections();
        rejectConflicts();
    }

    /** One button doing two things on one screen would do one of them by surprise; fail at load instead. */
    private void rejectConflicts() {
        for (List<Action> screen : SCREENS) {
            Map<String, Action> owner = new HashMap<>();
            for (Action a : screen) {
                for (String button : buttons.get(a)) {
                    Action previous = owner.putIfAbsent(button, a);
                    if (previous != null) {
                        throw new DataException(SOURCE, AT, "binds " + button + " to both " + previous + " and " + a);
                    }
                }
            }
        }
    }

    public boolean held(Action action, PadReading pad) {
        for (String button : buttons.get(action)) {
            if (pad.held(button)) {
                return true;
            }
        }
        return false;
    }

    public boolean up(PadReading pad) {
        return pad.stickYPercent() < -deadzone || dpad && pad.held("DPAD_UP");
    }

    public boolean down(PadReading pad) {
        return pad.stickYPercent() > deadzone || dpad && pad.held("DPAD_DOWN");
    }

    public boolean left(PadReading pad) {
        return pad.stickXPercent() < -deadzone || dpad && pad.held("DPAD_LEFT");
    }

    public boolean right(PadReading pad) {
        return pad.stickXPercent() > deadzone || dpad && pad.held("DPAD_RIGHT");
    }
}
