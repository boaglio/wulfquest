package wulf.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Canvas;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.data.InputConfig;
import wulf.data.JsonDb;

/**
 * AGENTS.md §19.2 and §11.6: key edges, levels, and X11 auto-repeat. Events are
 * handed straight to the listener, so no OS-level input is ever injected.
 */
class KeyboardInputTest {

    private static final Canvas SOURCE = new Canvas();

    private final KeyboardInput keys = new KeyboardInput(
            new InputMap(new JsonDb(Path.of("data")).load("config/input", InputConfig.class)));

    private static KeyEvent press(int code, long when) {
        return new KeyEvent(SOURCE, KeyEvent.KEY_PRESSED, when, 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    private static KeyEvent release(int code, long when) {
        return new KeyEvent(SOURCE, KeyEvent.KEY_RELEASED, when, 0, code, KeyEvent.CHAR_UNDEFINED);
    }

    @Test
    void aPressIsAnEdgeOnceThenALevel() {
        keys.keyPressed(press(KeyEvent.VK_SPACE, 100));
        InputState first = keys.sample();
        assertThat(first.fire()).isTrue();
        assertThat(first.firePressed()).isTrue();
        InputState second = keys.sample();
        assertThat(second.fire()).isTrue();
        assertThat(second.firePressed()).as("holding is not pressing again").isFalse();
    }

    @Test
    void x11AutoRepeatIsNotANewPress() {
        keys.keyPressed(press(KeyEvent.VK_SPACE, 100));
        keys.sample();
        // What X11 sends while a key is simply held: a release and a press, same timestamp.
        keys.keyReleased(release(KeyEvent.VK_SPACE, 180));
        keys.keyPressed(press(KeyEvent.VK_SPACE, 180));
        InputState s = keys.sample();
        assertThat(s.fire()).as("still held").isTrue();
        assertThat(s.firePressed()).as("no phantom swing").isFalse();
    }

    @Test
    void aRealReleaseThenPressIsANewPress() {
        keys.keyPressed(press(KeyEvent.VK_SPACE, 100));
        keys.sample();
        keys.keyReleased(release(KeyEvent.VK_SPACE, 180));
        assertThat(keys.sample().fire()).isFalse();
        keys.keyPressed(press(KeyEvent.VK_SPACE, 240));
        assertThat(keys.sample().firePressed()).isTrue();
    }

    @Test
    void aTapShorterThanATickIsStillSeen() {
        keys.keyPressed(press(KeyEvent.VK_SPACE, 100));
        keys.keyReleased(release(KeyEvent.VK_SPACE, 105));
        InputState s = keys.sample();
        assertThat(s.firePressed()).isTrue();
        assertThat(s.fire()).isFalse();
    }

    @Test
    void directionsAreLevelsAndOpposingKeysCancel() {
        keys.keyPressed(press(KeyEvent.VK_A, 1));
        keys.keyPressed(press(KeyEvent.VK_RIGHT, 2));
        assertThat(keys.sample().dx()).isZero();
        keys.keyReleased(release(KeyEvent.VK_A, 3));
        assertThat(keys.sample().dx()).isEqualTo(1);
    }

    @Test
    void losingFocusReleasesEverything() {
        keys.keyPressed(press(KeyEvent.VK_UP, 1));
        keys.focusLost(new FocusEvent(SOURCE, FocusEvent.FOCUS_LOST));
        assertThat(keys.sample().up()).isFalse();
    }

    @Test
    void pauseIsAnEdge() {
        keys.keyPressed(press(KeyEvent.VK_P, 1));
        assertThat(keys.sample().pausePressed()).isTrue();
        assertThat(keys.sample().pausePressed()).isFalse();
    }
}
