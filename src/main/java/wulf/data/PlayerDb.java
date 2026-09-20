package wulf.data;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The writable half of the database (AGENTS.md §20.8, §21): hi-scores, settings
 * and lifetime stats, in the player's own directory.
 *
 * <p>Every write is atomic — temp file, fsync, rolling {@code .bak}, atomic move
 * — and happens on a single-threaded executor, never on the game loop. Writes
 * coalesce: asking twice inside {@link #COALESCE_MS} writes once.
 *
 * <p>A file that will not parse or will not validate is <b>renamed</b>, never
 * deleted, and replaced with the shipped defaults. {@link #notices()} carries
 * what happened so the title screen can say so once.
 */
public final class PlayerDb implements AutoCloseable {

    /** Plumbing, not a game number (§20.8): the shortest gap between two writes of one file. */
    public static final long COALESCE_MS = 2000;

    private final Path dir;
    private final JsonDb content;
    private final List<String> notices = new ArrayList<>();
    private final ScheduledExecutorService writer =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "wulf-playerdb");
                t.setDaemon(true);
                return t;
            });

    private HighScores scores;
    private Settings settings;
    private Stats stats;

    private ScheduledFuture<?> pendingScores;
    private ScheduledFuture<?> pendingSettings;
    private ScheduledFuture<?> pendingStats;

    /**
     * @param content    the content database, used only to validate what is read back
     * @param defaultScores the shipped table a first run starts from (§21.2)
     * @param defaultSettings the shipped settings a first run starts from (§21.3)
     */
    public PlayerDb(Path dir, JsonDb content, HighScores defaultScores, Settings defaultSettings) {
        this.dir = dir;
        this.content = content;
        this.scores = read("highscores", "highscores", HighScores.class, () -> defaultScores);
        this.settings = read("settings", "settings", Settings.class, () -> defaultSettings);
        this.stats = read("stats", "stats", Stats.class, Stats::empty);
    }

    public Path dir() {
        return dir;
    }

    public HighScores scores() {
        return scores;
    }

    public Settings settings() {
        return settings;
    }

    public Stats stats() {
        return stats;
    }

    /** What went wrong while loading, if anything: shown once, on the title screen (§20.8). */
    public List<String> notices() {
        return List.copyOf(notices);
    }

    public void putScores(HighScores value) {
        scores = value;
        pendingScores = schedule(pendingScores, "highscores", value);
    }

    public void putSettings(Settings value) {
        settings = value;
        pendingSettings = schedule(pendingSettings, "settings", value);
    }

    public void putStats(Stats value) {
        stats = value;
        pendingStats = schedule(pendingStats, "stats", value);
    }

    /**
     * Writes everything outstanding and waits for it. Called on {@code GAME_OVER},
     * on {@code WON}, and from the shutdown hook (§20.8).
     */
    public void flush() {
        cancel(pendingScores);
        cancel(pendingSettings);
        cancel(pendingStats);
        pendingScores = null;
        pendingSettings = null;
        pendingStats = null;
        HighScores s = scores;
        Settings c = settings;
        Stats t = stats;
        run(() -> {
            write("highscores", s);
            write("settings", c);
            write("stats", t);
        });
    }

    @Override
    public void close() {
        flush();
        writer.shutdown();
    }

    // ---------------------------------------------------------------- internals

    private ScheduledFuture<?> schedule(ScheduledFuture<?> existing, String name, Object value) {
        // Coalescing: a write already waiting is simply left to pick up the newer value.
        if (existing != null && !existing.isDone()) {
            return existing;
        }
        return writer.schedule(() -> write(name, current(name)), COALESCE_MS, TimeUnit.MILLISECONDS);
    }

    private Object current(String name) {
        return switch (name) {
            case "highscores" -> scores;
            case "settings" -> settings;
            case "stats" -> stats;
            default -> throw new IllegalArgumentException("no player-DB file called '" + name + "'");
        };
    }

    private void run(Runnable task) {
        try {
            writer.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException e) {
            throw new IllegalStateException("player database write failed", e.getCause());
        }
    }

    private static void cancel(ScheduledFuture<?> f) {
        if (f != null) {
            f.cancel(false);
        }
    }

    private <T> T read(String name, String schema, Class<T> type, java.util.function.Supplier<T> fallback) {
        Path file = dir.resolve(name + ".json");
        if (!Files.isRegularFile(file)) {
            return fallback.get();
        }
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return content.bindExternal(file.toString(), text, schema, type);
        } catch (IOException | DataException e) {
            quarantine(file, e);
            return fallback.get();
        }
    }

    /** Never delete player data silently (§20.8): move it aside and say so. */
    private void quarantine(Path file, Exception why) {
        Path aside = file.resolveSibling(file.getFileName() + ".corrupt-" + System.currentTimeMillis() / 1000L);
        try {
            Files.move(file, aside, StandardCopyOption.REPLACE_EXISTING);
            notices.add(file.getFileName() + " WAS UNREADABLE - KEPT AS " + aside.getFileName());
        } catch (IOException moveFailed) {
            notices.add(file.getFileName() + " WAS UNREADABLE");
        }
        System.err.println("wulfquest: " + file + " could not be read (" + why.getMessage() + "); using defaults");
    }

    /** Temp file, fsync, rolling backup, atomic move (§20.8). */
    private void write(String name, Object value) {
        Path target = dir.resolve(name + ".json");
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(dir);
            Files.writeString(tmp, content.toPrettyJson(value), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.WRITE)) {
                ch.force(true);
            }
            if (Files.isRegularFile(target)) {
                Files.copy(target, target.resolveSibling(target.getFileName() + ".bak"),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            move(tmp, target);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + target, e);
        }
    }

    private static void move(Path tmp, Path target) throws IOException {
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            // Some filesystems (and some network mounts) cannot; a plain replace is still better than nothing.
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
