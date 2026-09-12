package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The content database contract (AGENTS.md §20.2): a bad file must fail with
 * the file name, a JSON pointer, and a readable reason — never a bare stack
 * trace.
 */
class JsonDbTest {

    private static final Path DATA = Path.of("data");

    @Test
    void loadsAndCaches() {
        JsonDb db = new JsonDb(DATA);
        GameConfig first = db.load("config/game", GameConfig.class);
        GameConfig second = db.load("config/game", GameConfig.class);
        assertThat(first).isSameAs(second);
        assertThat(first.tickHz()).isEqualTo(50);
        assertThat(first.tickNanos()).isEqualTo(20_000_000L);
    }

    @Test
    void contentHashIsStableAndOrderDependent() {
        JsonDb a = new JsonDb(DATA);
        a.load("config/game", GameConfig.class);
        a.load("art/palette", Palette.class);

        JsonDb b = new JsonDb(DATA);
        b.load("config/game", GameConfig.class);
        b.load("art/palette", Palette.class);

        assertThat(a.contentHash()).isEqualTo(b.contentHash()).hasSize(64);
        assertThat(a.loaded()).containsExactly("config/game", "art/palette");

        JsonDb c = new JsonDb(DATA);
        c.load("art/palette", Palette.class);
        assertThat(c.contentHash()).isNotEqualTo(a.contentHash());
    }

    @Test
    void featureFlagsAreKnownOrRejected() {
        GameConfig game = new JsonDb(DATA).load("config/game", GameConfig.class);
        assertThat(game.feature("caveHints")).isTrue();
        assertThat(game.feature("treasures")).isFalse();
        assertThatThrownBy(() -> game.feature("nope"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown feature flag");
    }

    @Test
    void malformedJsonNamesTheFile(@TempDir Path tmp) throws IOException {
        write(tmp, "config/game.json", "{ \"schemaVersion\": 1, ");
        DataException e = catchThrowableOfType(DataException.class,
                () -> new JsonDb(tmp).load("config/game", GameConfig.class));
        assertThat(e).isNotNull();
        assertThat(e.file()).endsWith("game.json");
        assertThat(e.pointer()).isEqualTo("/");
        assertThat(e.detail()).contains("not valid JSON");
    }

    @Test
    void schemaViolationReportsAJsonPointer(@TempDir Path tmp) throws IOException {
        // entries[3].rgb is not a colour.
        String palette = Files.readString(DATA.resolve("art/palette.json"))
                .replace("\"rgb\": \"#A826A8\"", "\"rgb\": \"puce\"");
        write(tmp, "art/palette.json", palette);

        DataException e = catchThrowableOfType(DataException.class,
                () -> new JsonDb(tmp).load("art/palette", Palette.class));
        assertThat(e).isNotNull();
        assertThat(e.file()).endsWith("palette.json");
        assertThat(e.pointer()).isEqualTo("/entries/3/rgb");
        assertThat(e.detail()).contains("fails its schema");
    }

    @Test
    void missingRequiredFieldIsReported(@TempDir Path tmp) throws IOException {
        String game = Files.readString(DATA.resolve("config/game.json"))
                .replace("\"tickHz\": 50,", "");
        write(tmp, "config/game.json", game);

        DataException e = catchThrowableOfType(DataException.class,
                () -> new JsonDb(tmp).load("config/game", GameConfig.class));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("tickHz");
    }

    @Test
    void unexpectedSchemaVersionIsReported(@TempDir Path tmp) throws IOException {
        String game = Files.readString(DATA.resolve("config/game.json"))
                .replace("\"schemaVersion\": 1", "\"schemaVersion\": 99");
        write(tmp, "config/game.json", game);

        DataException e = catchThrowableOfType(DataException.class,
                () -> new JsonDb(tmp).load("config/game", GameConfig.class));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/schemaVersion");
        assertThat(e.detail()).contains("99").contains("expects 1").contains("migration");
    }

    @Test
    void unknownPropertyIsRejected(@TempDir Path tmp) throws IOException {
        String game = Files.readString(DATA.resolve("config/game.json"))
                .replace("\"tickHz\": 50,", "\"tickHz\": 50, \"tickHertz\": 50,");
        write(tmp, "config/game.json", game);

        DataException e = catchThrowableOfType(DataException.class,
                () -> new JsonDb(tmp).load("config/game", GameConfig.class));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("tickHertz");
    }

    @Test
    void missingFileIsReported(@TempDir Path tmp) {
        DataException e = catchThrowableOfType(DataException.class,
                () -> new JsonDb(tmp).load("config/nonexistent", GameConfig.class));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("not found");
    }

    @Test
    void errorMessagesAreAsciiSoThePanelFontCanRenderThem(@TempDir Path tmp) throws IOException {
        // The validator localises by default; we pin English (§20.2) because the
        // 4x6 font has no accented glyphs.
        String palette = Files.readString(DATA.resolve("art/palette.json"))
                .replace("\"i\": 0,", "\"i\": \"zero\",");
        write(tmp, "art/palette.json", palette);

        DataException e = catchThrowableOfType(DataException.class,
                () -> new JsonDb(tmp).load("art/palette", Palette.class));
        assertThat(e).isNotNull();
        assertThat(e.detail()).matches("\\p{ASCII}+");
    }

    private static void write(Path root, String relative, String content) throws IOException {
        Path p = root.resolve(relative);
        Files.createDirectories(p.getParent());
        Files.writeString(p, content);
    }
}
