package wulf.input;

import java.util.EnumSet;
import java.util.Set;

/**
 * The gamepad side of input (AGENTS.md §19.3). Read once a tick on the game thread, like the
 * keyboard's {@code KeyboardInput.sample()} (desktop), into the same {@link InputState} — so the
 * simulation, and every replay, cannot tell a pad from the keys — and into a
 * {@link MenuInput} for the shell's own buttons.
 *
 * <p>Edges are this tick's reading against the last one: a press shorter than a tick is
 * not seen, which at 50 ticks a second no thumb can make.
 */
public final class GamepadInput {

    /** Where the readings come from: {@code SdlPad} on the desktop, a script in the tests. */
    public interface Source {

        Source NONE = () -> PadReading.NONE;

        PadReading read();

        default void close() {
        }
    }

    /** One tick of the pad: what the game reads, and what only the shell does. */
    public record Tick(InputState in, MenuInput menu) {
    }

    private final PadMap map;
    private final Source source;
    private PadReading last = PadReading.NONE;

    public GamepadInput(PadMap map, Source source) {
        this.map = map;
        this.source = source;
    }

    public Tick sample() {
        PadReading now = source.read();
        InputState in = new InputState(
                map.up(now),
                map.down(now),
                map.left(now),
                map.right(now),
                map.held(PadMap.Action.FIRE, now),
                pressed(PadMap.Action.FIRE, now),
                pressed(PadMap.Action.PAUSE, now),
                pressed(PadMap.Action.QUIT, now),
                false,
                false,
                false);
        Set<MenuInput.Pad> pads = EnumSet.noneOf(MenuInput.Pad.class);
        if (pressed(PadMap.Action.MAP, now)) {
            pads.add(MenuInput.Pad.MAP);
        }
        if (pressed(PadMap.Action.NEW_GAME, now)) {
            pads.add(MenuInput.Pad.NEW_GAME);
        }
        if (pressed(PadMap.Action.DIFFICULTY, now)) {
            pads.add(MenuInput.Pad.DIFFICULTY);
        }
        boolean any = false;
        for (String button : now.buttons()) {
            any |= !last.held(button);   // any button at all ends the attract demo, as any key does (§17.2)
        }
        last = now;
        return new Tick(in, any ? new MenuInput(java.util.List.of(), true, java.util.List.of(), pads) : MenuInput.NONE);
    }

    private boolean pressed(PadMap.Action action, PadReading now) {
        return map.held(action, now) && !map.held(action, last);
    }

    public void close() {
        source.close();
    }
}
