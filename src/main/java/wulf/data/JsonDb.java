package wulf.data;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.path.PathType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The content database (AGENTS.md §20). The only class in the game that
 * reads content files.
 *
 * <p>A logical name is a path under {@code data/} without the extension, e.g.
 * {@code "config/game"} or {@code "world/original_map"}. Files are located on
 * the filesystem first (so {@code --dev} edits are picked up) and on the
 * classpath second (so the packaged jar works).
 *
 * <p>Load order per file: read, parse, schema-validate, version-check, bind,
 * cache. Every failure becomes a {@link DataException} naming the file and a
 * JSON pointer.
 */
public final class JsonDb {

    private static final String ROOT = "data";

    private final Path contentRoot;
    private final ObjectMapper mapper;
    private final SchemaRegistry schemas;

    private final Map<String, Object> cache = new LinkedHashMap<>();
    private final Map<String, String> rawText = new LinkedHashMap<>();
    private final Map<String, Schema> schemaCache = new LinkedHashMap<>();

    public JsonDb(Path contentRoot) {
        this.contentRoot = contentRoot;
        this.mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
        // English messages and JSON-pointer instance locations, so a DATA ERROR
        // screen reads the same on every machine and our accent-free font can
        // render it (AGENTS.md §20.2).
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .locale(Locale.ENGLISH)
                .pathType(PathType.JSON_POINTER)
                .build();
        this.schemas = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaRegistryConfig(config));
    }

    /**
     * Loads, validates and binds a content file whose schema is named after the
     * file itself ({@code config/game} uses {@code schema/game.schema.json}).
     */
    public <T> T load(String logicalName, Class<T> type) {
        return load(logicalName, type, baseName(logicalName));
    }

    /**
     * Loads a file against an explicitly named schema — for families of files
     * that share one, such as the sprites ({@code "sprite"}).
     */
    public <T> T load(String logicalName, Class<T> type, String schemaName) {
        Object hit = cache.get(logicalName);
        if (hit != null) {
            return type.cast(hit);
        }
        String source = sourceName(logicalName);
        String text = readText(logicalName, source);
        JsonNode node = parse(text, source);
        validateAgainstSchema(schemaName, node, source);
        checkSchemaVersion(schemaName, node, source);

        T value;
        try {
            value = mapper.treeToValue(node, type);
        } catch (JacksonException e) {
            throw new DataException(source, "/",
                    "does not match " + type.getSimpleName() + " — " + rootCause(e), e);
        }
        cache.put(logicalName, value);
        rawText.put(logicalName, text);
        return value;
    }

    /**
     * SHA-256 over every file loaded so far, in load order. Part of the
     * determinism contract (AGENTS.md §6.4): a replay is only comparable
     * against the same content hash.
     */
    public String contentHash() {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        for (Map.Entry<String, String> e : rawText.entrySet()) {
            md.update(e.getKey().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(e.getValue().getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder(64);
        for (byte b : md.digest()) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** Logical names loaded so far, in load order. */
    public List<String> loaded() {
        return List.copyOf(rawText.keySet());
    }

    // ---------------------------------------------------------------- internals

    private String sourceName(String logicalName) {
        if (contentRoot != null) {
            Path p = contentRoot.resolve(logicalName + ".json");
            if (Files.isRegularFile(p)) {
                return p.toAbsolutePath().toString();
            }
        }
        return "classpath:" + ROOT + "/" + logicalName + ".json";
    }

    private String readText(String logicalName, String source) {
        if (!source.startsWith("classpath:")) {
            try {
                return Files.readString(Path.of(source), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new DataException(source, "/", "cannot be read — " + e.getMessage(), e);
            }
        }
        String resource = ROOT + "/" + logicalName + ".json";
        try (InputStream in = JsonDb.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new DataException(source, "/",
                        "not found on the filesystem or the classpath"
                                + (contentRoot == null ? "" : " (looked under " + contentRoot + ")"));
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataException(source, "/", "cannot be read — " + e.getMessage(), e);
        }
    }

    private JsonNode parse(String text, String source) {
        try {
            return mapper.readTree(text);
        } catch (JacksonException e) {
            throw new DataException(source, "/", "is not valid JSON — " + rootCause(e), e);
        }
    }

    private void validateAgainstSchema(String schemaName, JsonNode node, String source) {
        Schema schema = schemaFor(schemaName);
        List<Error> errors = schema.validate(node);
        if (errors.isEmpty()) {
            return;
        }
        Error first = errors.stream()
                .min((a, b) -> String.valueOf(a.getInstanceLocation())
                        .compareTo(String.valueOf(b.getInstanceLocation())))
                .orElseThrow();
        String pointer = pointerOf(first);
        String extra = errors.size() == 1 ? "" : " (and " + (errors.size() - 1) + " more)";
        throw new DataException(source, pointer, "fails its schema: " + first.getMessage() + extra);
    }

    private static String baseName(String logicalName) {
        return logicalName.substring(logicalName.lastIndexOf('/') + 1);
    }

    private Schema schemaFor(String schemaName) {
        String schemaLogical = "schema/" + schemaName + ".schema";
        return schemaCache.computeIfAbsent(schemaLogical, key -> {
            String source = sourceName(key);
            JsonNode node = parse(readText(key, source), source);
            return schemas.getSchema(node);
        });
    }

    private void checkSchemaVersion(String schemaName, JsonNode node, String source) {
        if (!SchemaVersions.isVersioned(schemaName)) {
            return;
        }
        int expected = SchemaVersions.expected(schemaName);
        JsonNode v = node.path("schemaVersion");
        if (!v.isInt()) {
            throw new DataException(source, "/schemaVersion",
                    "is missing or not an integer; expected " + expected);
        }
        if (v.asInt() != expected) {
            throw new DataException(source, "/schemaVersion",
                    "is " + v.asInt() + " but this build expects " + expected
                            + "; add a migration step (AGENTS.md §20.7)");
        }
    }

    /** The instance location, already a JSON pointer thanks to PathType.JSON_POINTER. */
    private static String pointerOf(Error error) {
        String loc = String.valueOf(error.getInstanceLocation());
        return loc.isEmpty() ? "/" : loc;
    }

    private static String rootCause(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String m = c.getMessage();
        if (m == null) {
            return c.getClass().getSimpleName();
        }
        int nl = m.indexOf('\n');
        return nl < 0 ? m : m.substring(0, nl);
    }
}
