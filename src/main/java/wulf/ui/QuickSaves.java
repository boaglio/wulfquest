package wulf.ui;

import java.util.Optional;
import java.util.function.Consumer;
import wulf.data.PlayerDb;
import wulf.data.QuickSaveData;
import wulf.engine.Replay;
import wulf.input.RecordedInput;
import wulf.sim.Simulation;
import wulf.world.RoomAddress;

/**
 * Quick save and quick load (AGENTS.md §21.4, [NEW] {@code features.quickSave}). A save is
 * the game as it was made — its seed, its start room and every tick's input — so loading
 * replays it, silently and at full speed, from a fresh start. Nothing is snapshotted by
 * hand, so nothing can be left out: the state hash at the end must match the one saved,
 * or the save is refused (another version's rules, other practice options).
 *
 * <p>A load makes the game a practice run (§17.6): dying can now be undone, so it never
 * reaches the hi-score table or the ledger.
 */
public final class QuickSaves {

    /** A new game from a seed, as job {@code level} (§14.9). */
    @FunctionalInterface
    public interface GameMaker {
        Simulation make(long seed, int level);
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

    /**
     * @param silentGame a new game from a seed, exactly as a new game starts this session (its
     *                   practice options and all) but with no sound — replaying it must not be heard
     * @param voice      gives a loaded game its sound
     */
    public QuickSaves(PlayerDb db, String contentHash, RoomAddress start, boolean dev,
                      GameMaker silentGame, Consumer<Simulation> voice) {
        this.db = db;
        this.contentHash = contentHash;
        this.start = start;
        this.dev = dev;
        this.silentGame = silentGame;
        this.voice = voice;
    }

    public void save(GameSession session) {
        Simulation sim = session.sim();
        RecordedInput played = session.played();
        db.putQuickSave(new QuickSaveData(QuickSaveData.SCHEMA_VERSION, contentHash, sim.runSeed(), start.toString(),
                dev, sim.practice(), played.ticks(), played.runs(), Replay.hex(sim.stateHash()), sim.level()));
    }

    public boolean exists() {
        return db.quickSave().isPresent();
    }

    public Loaded load() {
        Optional<QuickSaveData> found = db.quickSave();
        if (found.isEmpty()) {
            return Loaded.refused("NO QUICK SAVE YET");
        }
        QuickSaveData save = found.get();
        if (!save.contentHash().equals(contentHash)) {
            return Loaded.refused("SAVE IS FROM ANOTHER VERSION");
        }
        if (save.dev() != dev || !save.startRoom().equals(start.toString())) {
            return Loaded.refused("SAVE WAS MADE WITH OTHER OPTIONS");
        }
        RecordedInput played;
        try {
            played = RecordedInput.of(save.input());
        } catch (IllegalArgumentException e) {
            return Loaded.refused("SAVE IS DAMAGED");
        }
        Simulation sim = silentGame.make(save.seed(), save.newGamePlus());
        RecordedInput.Cursor in = played.cursor();
        while (in.hasNext()) {
            sim.play(in.next(), dev);
        }
        if (save.practiced()) {
            sim.markPractice();
        }
        if (!Replay.hex(sim.stateHash()).equals(save.stateHash())) {
            return Loaded.refused("SAVE DOES NOT FIT THIS GAME");
        }
        sim.markPractice();
        voice.accept(sim);
        return new Loaded(sim, played, null);
    }
}
