package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.data.JsonDb;
import wulf.data.ShellConfig;
import wulf.input.InputState;

/** The three-letter selector (AGENTS.md §21.2): up, down, and fire to set. */
class HiScoreEntryTest {

    private static final ShellConfig SHELL = new JsonDb(Path.of("data")).load("config/shell", ShellConfig.class);
    private static final ShellConfig.HiScores CONFIG = SHELL.hiScores();

    private static final InputState NONE = InputState.NONE;
    private static final InputState DOWN = InputState.of(0, 1, false, false);
    private static final InputState UP = InputState.of(0, -1, false, false);
    private static final InputState FIRE = new InputState(false, false, false, false, true, true, false, false,
            false, false, false);

    @Test
    void itStartsOnTheFirstLetterOfTheAlphabet() {
        HiScoreEntry entry = new HiScoreEntry(CONFIG);
        assertThat(entry.name()).isEqualTo(String.valueOf(CONFIG.alphabet().charAt(0)).repeat(CONFIG.nameLength()));
    }

    @Test
    void oneTapIsOneLetter() {
        HiScoreEntry entry = new HiScoreEntry(CONFIG);
        entry.tick(DOWN);
        entry.tick(NONE);
        entry.tick(DOWN);
        assertThat(entry.name().charAt(0)).isEqualTo(CONFIG.alphabet().charAt(2));
    }

    @Test
    void holdingRepeatsOnlyAfterTheDelay() {
        HiScoreEntry entry = new HiScoreEntry(CONFIG);
        for (int i = 0; i < CONFIG.repeatDelayTicks(); i++) {
            entry.tick(DOWN);
        }
        assertThat(entry.name().charAt(0)).isEqualTo(CONFIG.alphabet().charAt(1));
        for (int i = 0; i < CONFIG.repeatEveryTicks(); i++) {
            entry.tick(DOWN);
        }
        assertThat(entry.name().charAt(0)).isEqualTo(CONFIG.alphabet().charAt(2));
    }

    @Test
    void upWrapsRoundToTheEndOfTheAlphabet() {
        HiScoreEntry entry = new HiScoreEntry(CONFIG);
        entry.tick(UP);
        assertThat(entry.name().charAt(0)).isEqualTo(CONFIG.alphabet().charAt(CONFIG.alphabet().length() - 1));
    }

    @Test
    void fireWalksTheLettersAndTheLastOneCommits() {
        HiScoreEntry entry = new HiScoreEntry(CONFIG);
        for (int i = 0; i < CONFIG.nameLength() - 1; i++) {
            assertThat(entry.tick(FIRE)).isFalse();
            assertThat(entry.cursor()).isEqualTo(i + 1);
        }
        assertThat(entry.tick(FIRE)).isTrue();
        assertThat(entry.done()).isTrue();
    }

    @Test
    void leftAndRightMoveWithoutSettingALetter() {
        HiScoreEntry entry = new HiScoreEntry(CONFIG);
        entry.tick(InputState.of(1, 0, false, false));
        assertThat(entry.cursor()).isEqualTo(1);
        entry.tick(InputState.of(1, 0, false, false));
        assertThat(entry.cursor()).as("held, not tapped").isEqualTo(1);
        entry.tick(NONE);
        entry.tick(InputState.of(-1, 0, false, false));
        assertThat(entry.cursor()).isZero();
        assertThat(entry.done()).isFalse();
    }

    @Test
    void theCursorNeverLeavesTheName() {
        HiScoreEntry entry = new HiScoreEntry(CONFIG);
        for (int i = 0; i < 10; i++) {
            entry.tick(InputState.of(-1, 0, false, false));
            entry.tick(NONE);
        }
        assertThat(entry.cursor()).isZero();
    }
}
