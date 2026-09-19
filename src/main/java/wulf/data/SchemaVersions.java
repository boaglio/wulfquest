package wulf.data;

import java.util.Map;
import java.util.Set;

/**
 * The schema version this build of the game expects from each content file
 * (AGENTS.md §20.7). Bumping one of these without adding a migration step is
 * a build failure.
 */
public final class SchemaVersions {

    /** schema name -> expected {@code schemaVersion}. Keyed by family, not file. */
    private static final Map<String, Integer> EXPECTED = Map.ofEntries(
            Map.entry("game", 1),
            Map.entry("display", 1),
            Map.entry("palette", 1),
            Map.entry("font", 1),
            Map.entry("sprite", 1),
            Map.entry("sprite_index", 1),
            Map.entry("scenery", 1),
            Map.entry("player", 1),
            Map.entry("input", 1),
            Map.entry("creatures", 1),
            Map.entry("room_entities", 1),
            Map.entry("loot", 1),
            Map.entry("wulf", 1),
            Map.entry("guardians", 1),
            Map.entry("landmarks", 1));

    /**
     * Files that carry no {@code schemaVersion}. The sole member is the
     * extracted map data: it is imported canon (AGENTS.md §8) and its bytes
     * are locked by md5, so we do not edit a version field into it.
     */
    private static final Set<String> UNVERSIONED = Set.of("original_map");

    private SchemaVersions() {
    }

    static boolean isVersioned(String schemaName) {
        return !UNVERSIONED.contains(schemaName);
    }

    static int expected(String schemaName) {
        Integer v = EXPECTED.get(schemaName);
        if (v == null) {
            throw new IllegalStateException(
                    "no expected schemaVersion registered for schema '" + schemaName
                            + "'; add it to SchemaVersions (AGENTS.md §20.7)");
        }
        return v;
    }
}
