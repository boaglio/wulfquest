package wulf.data;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Where the player database lives (AGENTS.md §21.1). The content database is
 * read-only and ships with the game; everything the player accumulates —
 * hi-scores, settings, lifetime counters — is written here instead.
 *
 * <p>Tests must never write to the real directory: they pass an explicit path
 * ({@code --user-dir}, or a JUnit {@code @TempDir}).
 */
public final class UserDataDir {

    private static final String UNIX_NAME = "wulfquest";
    private static final String PRETTY_NAME = "WulfQuest";

    private UserDataDir() {
    }

    /** The per-platform directory, from this process's environment. */
    public static Path resolve() {
        return resolve(System.getProperty("os.name", ""), System.getenv(), System.getProperty("user.home", "."));
    }

    /** The same rule, with the environment handed in, so it can be tested on any machine. */
    static Path resolve(String osName, java.util.Map<String, String> env, String home) {
        String os = osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            String appData = env.get("APPDATA");
            return (appData != null && !appData.isBlank() ? Path.of(appData) : Path.of(home, "AppData", "Roaming"))
                    .resolve(PRETTY_NAME);
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return Path.of(home, "Library", "Application Support", PRETTY_NAME);
        }
        String xdg = env.get("XDG_DATA_HOME");
        return (xdg != null && !xdg.isBlank() ? Path.of(xdg) : Path.of(home, ".local", "share"))
                .resolve(UNIX_NAME);
    }
}
