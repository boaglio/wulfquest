package wulf.ui;

import wulf.data.ShellConfig;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.Player;
import wulf.sim.Quest;
import wulf.sim.Simulation;

/**
 * The loop thread's state around one {@link Simulation} (AGENTS.md §17.4): pause,
 * the dev toggles and the panel line. Headless and testable; the AWT thread never
 * touches it.
 *
 * <p>It drives one game and no more. What happens when that game ends — the tally,
 * the hi-score table, the way back to the title — belongs to {@link Shell}.
 */
public final class GameSession {

    private final Simulation sim;
    private final ShellConfig.AmuletReveal reveal;
    private boolean paused;
    private boolean showMask;
    private int piecesSeen;
    /** Ticks into the amulet reveal (§14.6); negative when none is showing. */
    private int revealTick = -1;

    public GameSession(Simulation sim) {
        this(sim, ShellConfig.AmuletReveal.NONE);
    }

    public GameSession(Simulation sim, ShellConfig.AmuletReveal reveal) {
        this.sim = sim;
        this.reveal = reveal;
        this.piecesSeen = sim.quest().piecesHeld();
    }

    public void tick(InputState in, boolean dev) {
        if (dev && in.devMaskPressed()) {
            showMask = !showMask;
        }
        if (over() || won()) {
            return;
        }
        if (revealing()) {
            // The game stands still under the reveal, as the original's did: no sim tick, so
            // nothing is recorded and a replay never knows it was there (§22.6).
            revealTick++;
            boolean skipped = revealTick >= reveal.skipAfterTicks() && in.firePressed();
            if (skipped || revealTick >= reveal.holdTicks()) {
                revealTick = -1;
            }
            return;
        }
        if (in.pausePressed()) {
            paused = !paused;
        }
        if (paused) {
            return;
        }
        sim.play(in, dev);
        int held = sim.quest().piecesHeld();
        if (held > piecesSeen) {
            piecesSeen = held;
            if (reveal.holdTicks() > 0) {
                revealTick = 0;
            }
        }
    }

    /** Whether the amulet reveal of §14.6 is up. */
    public boolean revealing() {
        return revealTick >= 0;
    }

    /** The verse for the quarters now held; empty when no reveal is showing. */
    public java.util.List<String> verse() {
        return revealing() ? reveal.verses().get(piecesSeen - 1) : java.util.List.of();
    }

    public boolean over() {
        return sim.player().mode() == Player.Mode.GAME_OVER;
    }

    public boolean won() {
        return sim.player().mode() == Player.Mode.WON;
    }

    /** The panel line: pause, a shrine's hint, or in dev mode where you are. Null for none. */
    public String message(boolean dev) {
        if (paused) {
            return "PAUSED";
        }
        if (sim.quest().hintTicks() > 0) {
            return hint(sim.quest());
        }
        if (dev) {
            Player p = sim.player();
            int x = Fixed.px(p.xFp()) - sim.room().col() * Simulation.ROOM_W_PX;
            int y = Fixed.px(p.yFp()) - sim.room().row() * Simulation.ROOM_H_PX;
            return "ROOM " + sim.room() + "  X" + x + " Y" + y + (showMask ? "  MASK" : "");
        }
        return null;
    }

    public Simulation sim() {
        return sim;
    }

    /** §14.5: {@code AMULET STIRS TO THE NORTH-WEST}, or the way to the arch once it is whole. */
    public static String hint(Quest quest) {
        String way = switch (quest.hintDirection()) {
            case N -> "NORTH";
            case NE -> "NORTH-EAST";
            case E -> "EAST";
            case SE -> "SOUTH-EAST";
            case S -> "SOUTH";
            case SW -> "SOUTH-WEST";
            case W -> "WEST";
            case NW -> "NORTH-WEST";
        };
        return (quest.hintToExit() ? "THE ARCH CALLS FROM THE " : "AMULET STIRS TO THE ") + way;
    }

    public boolean paused() {
        return paused;
    }

    public boolean showMask() {
        return showMask;
    }
}
