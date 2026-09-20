package wulf.input;

import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The AWT side of input (AGENTS.md §19.2). Listeners record keys on the event
 * thread; {@link #sample()} turns that into one immutable {@link InputState} per
 * tick on the game thread.
 */
public final class KeyboardInput implements KeyListener, FocusListener {

    private final InputMap map;
    private final Set<Integer> held = ConcurrentHashMap.newKeySet();
    private final Set<Integer> pressedSinceSample = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Long> lastRelease = new ConcurrentHashMap<>();
    private final java.util.Queue<Character> typedSinceDrain = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final java.util.concurrent.atomic.AtomicBoolean anyKeySinceDrain =
            new java.util.concurrent.atomic.AtomicBoolean();

    public KeyboardInput(InputMap map) {
        this.map = map;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        Long released = lastRelease.remove(code);
        if (released != null && released == e.getWhen()) {
            // X11 auto-repeat delivers a release and a press with the same timestamp
            // while a key is simply held. Treat it as still held: no new edge, or
            // holding fire would swing on its own (§11.6).
            held.add(code);
            return;
        }
        if (held.add(code)) {
            pressedSinceSample.add(code);
            anyKeySinceDrain.set(true);
            char ch = menuChar(code);
            if (ch != 0) {
                typedSinceDrain.add(ch);
            }
        }
    }

    /** Letters and digits only: what a title-screen hotkey can be (§17.2). */
    private static char menuChar(int code) {
        if (code >= KeyEvent.VK_A && code <= KeyEvent.VK_Z) {
            return (char) ('A' + code - KeyEvent.VK_A);
        }
        if (code >= KeyEvent.VK_0 && code <= KeyEvent.VK_9) {
            return (char) ('0' + code - KeyEvent.VK_0);
        }
        return 0;
    }

    @Override
    public void keyReleased(KeyEvent e) {
        held.remove(e.getKeyCode());
        lastRelease.put(e.getKeyCode(), e.getWhen());
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // not used
    }

    @Override
    public void focusLost(FocusEvent e) {
        // No stuck keys when the window loses focus mid-press.
        held.clear();
        pressedSinceSample.clear();
        lastRelease.clear();
        typedSinceDrain.clear();
        anyKeySinceDrain.set(false);
    }

    @Override
    public void focusGained(FocusEvent e) {
        // nothing to restore
    }

    /**
     * The shell's own keys since the last call (§17.2), drained so each press is
     * acted on once. Never reaches the simulation.
     */
    public MenuInput drainMenu() {
        boolean any = anyKeySinceDrain.getAndSet(false);
        if (typedSinceDrain.isEmpty()) {
            return any ? new MenuInput(java.util.List.of(), true) : MenuInput.NONE;
        }
        java.util.List<Character> chars = new java.util.ArrayList<>();
        for (Character c = typedSinceDrain.poll(); c != null; c = typedSinceDrain.poll()) {
            chars.add(c);
        }
        return new MenuInput(chars, true);
    }

    /** One snapshot per tick. Never call from the simulation itself. */
    public InputState sample() {
        Set<Integer> pressed = new HashSet<>(pressedSinceSample);
        pressedSinceSample.removeAll(pressed);
        Set<Integer> down = new HashSet<>(held);
        return new InputState(
                map.held(InputMap.Action.UP, down),
                map.held(InputMap.Action.DOWN, down),
                map.held(InputMap.Action.LEFT, down),
                map.held(InputMap.Action.RIGHT, down),
                map.held(InputMap.Action.FIRE, down),
                map.held(InputMap.Action.FIRE, pressed),
                map.held(InputMap.Action.PAUSE, pressed),
                map.held(InputMap.Action.QUIT, pressed),
                map.held(InputMap.Action.DEV_KILL, pressed),
                map.held(InputMap.Action.DEV_MASK, pressed),
                map.held(InputMap.Action.DEV_WULF, pressed));
    }
}
