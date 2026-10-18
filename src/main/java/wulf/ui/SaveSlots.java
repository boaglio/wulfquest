package wulf.ui;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import wulf.data.PlayerDb;
import wulf.data.QuickSaveData;
import wulf.engine.Replay;
import wulf.input.RecordedInput;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;

/**
 * The save slots (AGENTS.md §21.4, {@code [NEW]}), offered from the pause menu and the
 * title. A save is not a snapshot of the simulation: it is the game's seed and every tick of
 * input played into it, and loading plays that input into a fresh game of the same seed —
 * the simulation is deterministic (§6.4), so it arrives exactly where it was saved. The state
 * hash the save recorded is checked on arrival, and that is the whole test: a save from another
 * version of the content loads when this version's rules replay it to the same place (most new
 * versions change words, menus and pictures, not the rules), and a save the rules have moved
 * under, or made with other options, is refused rather than loaded wrong.
 *
 * <p>A loaded game ranks like any other, as the user asked: saving and loading are part of
 * play. A game that was already practice when it was saved stays practice.
 */
public final class SaveSlots {

    /** A new game from a seed, as job {@code level} (§14.9), in Easy mode or Hard (§17.8). */
    @FunctionalInterface
    public interface GameMaker {
        Simulation make(long seed, int level, boolean easy);
    }

    /** What a load gave: the game and the input that made it, or why there is none. */
    public record Loaded(Simulation sim, RecordedInput played, String refusal) {

        static Loaded refused(String why) {
            return new Loaded(null, null, why);
        }

        public boolean ok() {
            return refusal == null;
        }
    }

    private final PlayerDb db;
    private final String contentHash;
    private final RoomAddress start;
    private final boolean dev;
    private final GameMaker silentGame;
    private final Consumer<Simulation> voice;
    private final Supplier<String> today;

    /**
     * @param silentGame a new game from a seed, exactly as a new game starts this session (its
     *                   practice options and all) but with no sound — replaying it must not be heard
     * @param voice      gives a loaded game its sound
     */
    public SaveSlots(PlayerDb db, String contentHash, RoomAddress start, boolean dev, GameMaker silentGame,
                     Consumer<Simulation> voice, Supplier<String> today) {
        this.db = db;
        this.contentHash = contentHash;
        this.start = start;
        this.dev = dev;
        this.silentGame = silentGame;
        this.voice = voice;
        this.today = today;
    }

    /** Keeps the game in slot {@code n} (from 1), over whatever was there. */
    public void save(GameSession session, int n) {
        Simulation sim = session.sim();
        RecordedInput played = session.played();
        db.putSaveSlot(n, new QuickSaveData(QuickSaveData.SCHEMA_VERSION, contentHash, sim.runSeed(), start.toString(),
                dev, sim.practice(), played.ticks(), played.runs(), Replay.hex(sim.stateHash()), sim.level(),
                sim.easy(), today.get(), sim.quest().quarters(), sim.score(),
                sim.room().col() + "," + sim.room().row()));
    }

    /** What is in slot {@code n}, for the list: empty when nothing is, or it cannot be read. */
    public Optional<QuickSaveData> peek(int n) {
        return db.saveSlot(n);
    }

    public Loaded load(int n) {
        Optional<QuickSaveData> found = db.saveSlot(n);
        if (found.isEmpty()) {
            return Loaded.refused("EMPTY");
        }
        QuickSaveData save = found.get();
        if (save.dev() != dev || !save.startRoom().equals(start.toString())) {
            return Loaded.refused("SAVE WAS MADE WITH OTHER OPTIONS");
        }
        RecordedInput played;
        try {
            played = RecordedInput.of(save.input());
        } catch (IllegalArgumentException e) {
            return Loaded.refused("SAVE IS DAMAGED");
        }
        Simulation sim = silentGame.make(save.seed(), save.newGamePlus(), save.easy());
        RecordedInput.Cursor in = played.cursor();
        while (in.hasNext()) {
            sim.play(in.next(), dev);
        }
        if (save.practiced()) {
            sim.markPractice();
        }
        if (!Replay.hex(sim.stateHash()).equals(save.stateHash())) {
            // Another version's rules took it somewhere else; this version's cannot have.
            return Loaded.refused(save.contentHash().equals(contentHash) ? "SAVE DOES NOT FIT THIS GAME"
                    : "SAVE IS FROM ANOTHER VERSION");
        }
        voice.accept(sim);
        return new Loaded(sim, played, null);
    }
}
