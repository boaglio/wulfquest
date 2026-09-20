package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import wulf.Content;

/**
 * The writable half of the database (AGENTS.md §20.8, §21). Every test here
 * writes to a {@code @TempDir}: nothing may ever touch the real user directory.
 */
class PlayerDbTest {

    private static final JsonDb CONTENT_DB = new JsonDb(Path.of("data"));
    private static final Content CONTENT = Content.load(CONTENT_DB);

    private PlayerDb open(Path dir) {
        return new PlayerDb(dir, CONTENT_DB, CONTENT.defaultScores(), CONTENT.defaultSettings());
    }

    @Test
    void aFirstRunGetsTheShippedDefaults(@TempDir Path dir) {
        try (PlayerDb db = open(dir)) {
            assertThat(db.scores().entries()).hasSize(CONTENT.shell().hiScores().size());
            assertThat(db.settings().inputProfile()).isEqualTo(CONTENT.input().active());
            assertThat(db.stats().blank()).isTrue();
            assertThat(db.notices()).isEmpty();
        }
    }

    @Test
    void whatIsWrittenComesBack(@TempDir Path dir) {
        try (PlayerDb db = open(dir)) {
            db.putSettings(db.settings().withAudio(false, 25));
            db.putStats(db.stats().afterGame(1234, true, 5000, 9, 4, 1, 2, 1,
                    Map.of("bat", 3L), Map.of("wulf", 1L), Map.of("yellow", 2L)));
            db.flush();
        }
        try (PlayerDb again = open(dir)) {
            assertThat(again.settings().audio().enabled()).isFalse();
            assertThat(again.settings().audio().volumePercent()).isEqualTo(25);
            assertThat(again.stats().gamesPlayed()).isEqualTo(1);
            assertThat(again.stats().bestEscapeTicks()).isEqualTo(5000);
            assertThat(again.stats().killsBySpecies()).containsEntry("bat", 3L);
        }
    }

    @Test
    void everyWriteLeavesARollingBackupAndNoTempFile(@TempDir Path dir) throws Exception {
        try (PlayerDb db = open(dir)) {
            db.putSettings(db.settings().withScale(2));
            db.flush();
            db.putSettings(db.settings().withScale(3));
            db.flush();
        }
        assertThat(dir.resolve("settings.json")).exists();
        assertThat(dir.resolve("settings.json.bak")).exists();
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".tmp"))).isEmpty();
        }
    }

    @Test
    void aCorruptFileIsKeptAsideAndTheDefaultsComeBack(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("highscores.json"), "{ not json at all", StandardCharsets.UTF_8);
        try (PlayerDb db = open(dir)) {
            assertThat(db.scores().entries()).hasSize(CONTENT.shell().hiScores().size());
            assertThat(db.notices()).hasSize(1);
            assertThat(db.notices().get(0)).contains("HIGHSCORES.JSON".toLowerCase(java.util.Locale.ROOT));
        }
        try (Stream<Path> files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString()).filter(n -> n.contains(".corrupt-")))
                    .as("the player's own file was moved aside, never deleted").isNotEmpty();
        }
    }

    @Test
    void aFileThatParsesButBreaksItsSchemaIsAlsoKeptAside(@TempDir Path dir) throws Exception {
        Files.createDirectories(dir);
        // A name of four letters: the schema allows up to eight, but the score must be a number.
        Files.writeString(dir.resolve("highscores.json"),
                "{\"schemaVersion\":1,\"entries\":[{\"name\":\"ABC\",\"score\":\"lots\",\"pieces\":0,"
                        + "\"ticks\":0,\"date\":\"2026-01-01\"}]}", StandardCharsets.UTF_8);
        try (PlayerDb db = open(dir)) {
            assertThat(db.notices()).isNotEmpty();
            assertThat(db.scores().entries()).hasSize(CONTENT.shell().hiScores().size());
        }
    }

    @Test
    void writtenJsonIsPrettyAndEndsInANewline(@TempDir Path dir) throws Exception {
        try (PlayerDb db = open(dir)) {
            db.putStats(Stats.empty());
            db.flush();
        }
        String text = Files.readString(dir.resolve("stats.json"), StandardCharsets.UTF_8);
        assertThat(text).endsWith("\n").contains("\n  ");
        assertThat(List.of(text.split("\n"))).hasSizeGreaterThan(5);
    }
}
