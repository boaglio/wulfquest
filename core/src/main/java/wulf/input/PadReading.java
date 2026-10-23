package wulf.input;

import java.util.Set;

/**
 * One look at the gamepads (AGENTS.md §19.3): the buttons held, by their names in
 * {@code input.json} ({@code "A"}, {@code "START"}, the d-pad as {@code "DPAD_UP"}…), and the
 * left stick in whole percent of its travel, x to the right and y down, as the screen's.
 */
public record PadReading(Set<String> buttons, int stickXPercent, int stickYPercent) {

    public static final PadReading NONE = new PadReading(Set.of(), 0, 0);

    public PadReading {
        buttons = Set.copyOf(buttons);
    }

    public boolean held(String button) {
        return buttons.contains(button);
    }
}
