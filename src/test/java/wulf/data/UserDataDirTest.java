package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Where the player database goes on each platform (AGENTS.md §21.1). */
class UserDataDirTest {

    @Test
    void linuxHonoursXdgAndFallsBackToLocalShare() {
        assertThat(UserDataDir.resolve("Linux", Map.of("XDG_DATA_HOME", "/data"), "/home/ranger"))
                .hasToString("/data/wulfquest");
        assertThat(UserDataDir.resolve("Linux", Map.of(), "/home/ranger"))
                .hasToString("/home/ranger/.local/share/wulfquest");
        assertThat(UserDataDir.resolve("Linux", Map.of("XDG_DATA_HOME", "  "), "/home/ranger"))
                .hasToString("/home/ranger/.local/share/wulfquest");
    }

    @Test
    void macOsUsesApplicationSupport() {
        assertThat(UserDataDir.resolve("Mac OS X", Map.of(), "/Users/ranger"))
                .hasToString("/Users/ranger/Library/Application Support/WulfQuest");
    }

    @Test
    void windowsUsesAppData() {
        assertThat(UserDataDir.resolve("Windows 11", Map.of("APPDATA", "/roaming"), "/users/ranger"))
                .hasToString("/roaming/WulfQuest");
        assertThat(UserDataDir.resolve("Windows 11", Map.of(), "/users/ranger").toString())
                .endsWith("AppData/Roaming/WulfQuest".replace('/', java.io.File.separatorChar));
    }
}
