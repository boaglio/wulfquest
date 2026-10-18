package wulf.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wulf.data.DataException;
import wulf.data.InputConfig;
import wulf.data.JsonDb;

/** AGENTS.md §19.3 — the gamepad, read from scripted readings: no pad and no native code. */
class GamepadInputTest {

    private static final InputConfig.Gamepad SHIPPED =
            new JsonDb(Path.of("data")).load("config/input", InputConfig.class).gamepad();

    /** A pad that reads as told, one reading a tick, and then as let go. */
    private static GamepadInput pad(InputConfig.Gamepad config, PadReading... readings) {
        Deque<PadReading> script = new ArrayDeque<>(List.of(readings));
        return new GamepadInput(new PadMap(config), () -> script.isEmpty() ? PadReading.NONE : script.poll());
    }

    private static PadReading buttons(String... names) {
        return new PadReading(Set.of(names), 0, 0);
    }

    private static PadReading stick(int x, int y) {
        return new PadReading(Set.of(), x, y);
    }

    @Test
    void theShippedButtonsFireOnAPauseOnStartAndLeaveOnBack() {
        GamepadInput p = pad(SHIPPED, buttons("A"), buttons("START"), buttons("BACK"), buttons("X"));
        InputState a = p.sample().in();
        assertThat(a.fire()).isTrue();
        assertThat(a.firePressed()).isTrue();
        assertThat(p.sample().in().pausePressed()).isTrue();
        assertThat(p.sample().in().quitPressed()).isTrue();
        assertThat(p.sample().in().fire()).as("X fires too").isTrue();
    }

    @Test
    void aPressIsOneEdgeAndAHoldStaysHeld() {
        GamepadInput p = pad(SHIPPED, buttons("A"), buttons("A"), buttons("A"), PadReading.NONE, buttons("A"));
        InputState first = p.sample().in();
        InputState held = p.sample().in();
        assertThat(first.firePressed()).isTrue();
        assertThat(held.fire()).isTrue();
        assertThat(held.firePressed()).as("held is not pressed again").isFalse();
        p.sample();
        assertThat(p.sample().in().fire()).isFalse();
        assertThat(p.sample().in().firePressed()).as("let go, then pressed again").isTrue();
    }

    @Test
    void theStickPastTheDeadzoneWalksAndInsideItDoesNot() {
        int dead = SHIPPED.deadzonePercent();
        GamepadInput p = pad(SHIPPED, stick(dead, -dead), stick(dead + 1, 0), stick(0, -(dead + 1)),
                stick(-100, 100), stick(90, 30));
        assertThat(p.sample().in()).as("a resting stick that drifts").isEqualTo(InputState.NONE);
        InputState right = p.sample().in();
        assertThat(right.dx()).isEqualTo(1);
        assertThat(right.dy()).isZero();
        assertThat(p.sample().in().dy()).as("y is negative upwards").isEqualTo(-1);
        InputState downLeft = p.sample().in();
        assertThat(downLeft.dx()).isEqualTo(-1);
        assertThat(downLeft.dy()).as("eight directions: the diagonal").isEqualTo(1);
        InputState mostlyRight = p.sample().in();
        assertThat(mostlyRight.dx()).isEqualTo(1);
        assertThat(mostlyRight.dy()).as("a push mostly to the side is to the side").isZero();
    }

    @Test
    void theDpadWalksUnlessInputJsonSaysNot() {
        PadReading dpad = buttons("DPAD_UP", "DPAD_LEFT");
        InputState on = pad(SHIPPED, dpad).sample().in();
        assertThat(on.dx()).isEqualTo(-1);
        assertThat(on.dy()).isEqualTo(-1);
        InputConfig.Gamepad off = new InputConfig.Gamepad(true, SHIPPED.deadzonePercent(), false, SHIPPED.buttons());
        assertThat(pad(off, dpad).sample().in()).isEqualTo(InputState.NONE);
    }

    @Test
    void theShellsButtonsAreMenuInputAndNeverReachTheGame() {
        GamepadInput p = pad(SHIPPED, buttons("Y"), buttons("Y"), buttons("B"));
        GamepadInput.Tick y = p.sample();
        assertThat(y.menu().pad(MenuInput.Pad.MAP)).isTrue();
        assertThat(y.menu().pad(MenuInput.Pad.NEW_GAME)).isTrue();
        assertThat(y.in()).isEqualTo(InputState.NONE);
        assertThat(p.sample().menu()).as("held, not pressed again").isEqualTo(MenuInput.NONE);
        GamepadInput.Tick b = p.sample();
        assertThat(b.menu().pad(MenuInput.Pad.DIFFICULTY)).isTrue();
        assertThat(b.menu().anyKey()).isTrue();
    }

    @Test
    void anyButtonPressedEndsTheDemoAsAnyKeyDoes() {
        GamepadInput p = pad(SHIPPED, buttons("RIGHTBUMPER"), buttons("RIGHTBUMPER"));
        assertThat(p.sample().menu().anyKey()).as("a button bound to nothing").isTrue();
        assertThat(p.sample().menu().anyKey()).isFalse();
    }

    @Test
    void noPadIsNoInput() {
        GamepadInput p = new GamepadInput(new PadMap(SHIPPED), GamepadInput.Source.NONE);
        GamepadInput.Tick t = p.sample();
        assertThat(t.in()).isEqualTo(InputState.NONE);
        assertThat(t.menu()).isEqualTo(MenuInput.NONE);
    }

    @Test
    void oneButtonForTwoThingsOnOneScreenIsADataError() {
        InputConfig.Buttons b = SHIPPED.buttons();
        InputConfig.Buttons clash = new InputConfig.Buttons(List.of("A"), List.of("A"), b.quit(), b.map(),
                b.newGame(), b.difficulty());
        DataException e = catchThrowableOfType(DataException.class, () -> new PadMap(
                new InputConfig.Gamepad(true, SHIPPED.deadzonePercent(), true, clash)));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("FIRE").contains("PAUSE");
        InputConfig.Buttons titleClash = new InputConfig.Buttons(b.fire(), b.pause(), b.quit(), b.map(),
                b.fire(), b.difficulty());
        assertThat(catchThrowableOfType(DataException.class, () -> new PadMap(
                new InputConfig.Gamepad(true, SHIPPED.deadzonePercent(), true, titleClash))).detail())
                .as("fire on the title starts a game; it cannot start a new one too").contains("NEW_GAME");
    }

    @Test
    void theKeysAndThePadTogether() {
        InputState keys = InputState.of(1, 0, false, false);
        InputState pad = new InputState(false, false, false, false, true, true, false, false, false, false, false);
        InputState both = keys.or(pad);
        assertThat(both.dx()).isEqualTo(1);
        assertThat(both.fire()).isTrue();
        assertThat(both.firePressed()).isTrue();
        MenuInput menu = MenuInput.of("D").and(
                new MenuInput(List.of(), true, List.of(), Set.of(MenuInput.Pad.MAP)));
        assertThat(menu.typed("D")).isTrue();
        assertThat(menu.pad(MenuInput.Pad.MAP)).isTrue();
        assertThat(MenuInput.of("D").and(MenuInput.NONE)).isEqualTo(MenuInput.of("D"));
    }
}
