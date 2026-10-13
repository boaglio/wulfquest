package wulf.ui;

import wulf.data.ShellConfig;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.input.RecordedInput;
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
    private boolean mapOpen;
    private boolean showMask;
    private int piecesSeen;
    /** Ticks into the amulet reveal (§14.6); negative when none is showing. */
    private int revealTick = -1;
    /** §21.4: every input the game has played since it began — what a quick save keeps. */
    private final RecordedInput played;
    private String note;
    private int noteTicks;

    public GameSession(Simulation sim) {
        this(sim, ShellConfig.AmuletReveal.NONE);
    }

    public GameSession(Simulation sim, ShellConfig.AmuletReveal reveal) {
        this(sim, reveal, new RecordedInput());
    }

    /** A game already under way: {@code played} is the input that brought {@code sim} to where it is (§21.4). */
    public GameSession(Simulation sim, ShellConfig.AmuletReveal reveal, RecordedInput played) {
        this.sim = sim;
        this.reveal = reveal;
        this.played = played;
        this.piecesSeen = sim.quest().piecesHeld();
    }

    public void tick(InputState in, boolean dev) {
        if (noteTicks > 0 && --noteTicks == 0) {
            note = null;
        }
        if (dev && in.devMaskPressed()) {
            showMask = !showMask;
        }
        if (over() || won()) {
            return;
        }
        if (mapOpen) {
            return;   // §17.7: the game stands still under the map, as under pause; nothing is recorded
        }
        if (revealing()) {
            // The game stands still under the reveal, as the original's did: no sim tick, so
            // nothing is recorded and a replay never knows it was there (§22.6).
            // It waits for fire (§14.6), but not the press that took the quarter.
            revealTick = Math.min(revealTick + 1, reveal.skipAfterTicks());
            if (revealTick >= reveal.skipAfterTicks() && in.firePressed()) {
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
        played.append(in);
        int held = sim.quest().piecesHeld();
        if (held > piecesSeen) {
            piecesSeen = held;
            if (!reveal.verses().isEmpty()) {
                revealTick = 0;
            }
        }
    }

    /** Whether the amulet reveal of §14.6 is up. */
    public boolean revealing() {
        return revealTick >= 0;
    }

    /** Whether the reveal now takes fire to close: its prompt shows (§14.6). */
    public boolean revealWaitsForFire() {
        return revealing() && revealTick >= reveal.skipAfterTicks();
    }

    /** The reveal's "press fire" line. */
    public String revealPrompt() {
        return reveal.prompt();
    }

    /** The verse for the quarters now held; empty when no reveal is showing. */
    public java.util.List<String> verse() {
        return revealing() ? reveal.verses().get(piecesSeen - 1) : java.util.List.of();
    }

    /**
     * §17.5: the game goes on after game over — fire, played into the simulation and kept in
     * the input like any other tick, so a replay or a quick load takes the continue too.
     */
    public void carryOn(InputState fire) {
        sim.play(fire, false);
        played.append(fire);
    }

    public boolean over() {
        return sim.player().mode() == Player.Mode.GAME_OVER;
    }

    public boolean won() {
        return sim.player().mode() == Player.Mode.WON;
    }

    /** The panel line: pause, a shrine's hint, or in dev mode where you are. Null for none. */
    public String message(boolean dev) {
        if (note != null) {
            return note;
        }
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

    /** §21.4: the input played so far, as a copy. */
    public RecordedInput played() {
        return RecordedInput.of(played.runs());
    }

    /** A line on the panel for {@code ticks} ticks: "SAVED", or why a load could not be done. */
    public void note(String text, int ticks) {
        note = text;
        noteTicks = ticks;
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

    /** §17.7: opens or closes the map screen; not while the game is over or the amulet reveal is up. */
    public void toggleMap() {
        if (!over() && !won() && !revealing()) {
            mapOpen = !mapOpen;
        }
    }

    public boolean mapOpen() {
        return mapOpen;
    }

    public boolean paused() {
        return paused;
    }

    public boolean showMask() {
        return showMask;
    }
}
