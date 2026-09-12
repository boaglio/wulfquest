package wulf.data;

import java.util.Map;
import java.util.Set;

/**
 * The schema version this build of the game expects from each content file
 * (AGENTS.md §20.7). Bumping one of these without adding a migration step is
 * a build failure.
 */
public final class SchemaVersions {

    /** logical name -> expected {@code schemaVersion}. */
    private static final Map<String, Integer> EXPECTED = Map.of(
            "config/game", 1,
            "config/display", 1,
            "art/palette", 1,
            "art/font/font", 1);

    /**
     * Files that carry no {@code schemaVersion}. The sole member is the
     * extracted map data: it is imported canon (AGENTS.md §8) and its bytes
     * are locked by md5, so we do not edit a version field into it.
     */
    private static final Set<String> UNVERSIONED = Set.of("world/original_map");

    private SchemaVersions() {
    }

    static boolean isVersioned(String logicalName) {
        return !UNVERSIONED.contains(logicalName);
    }

    static int expected(String logicalName) {
        Integer v = EXPECTED.get(logicalName);
        if (v == null) {
            throw new IllegalStateException(
                    "no expected schemaVersion registered for '" + logicalName
                            + "'; add it to SchemaVersions (AGENTS.md §20.7)");
        }
        return v;
    }
}
