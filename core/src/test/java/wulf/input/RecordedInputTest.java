package wulf.input;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AGENTS.md §22.6 — what a replay keeps of each tick's input. */
class RecordedInputTest {

    @Test
    void everyRecordedBitSurvivesARoundTrip() {
        for (int bits = 0; bits <= 255; bits++) {
            assertThat(RecordedInput.encode(RecordedInput.decode(bits))).as("bits %d", bits).isEqualTo(bits);
        }
    }

    @Test
    void pauseQuitAndTheMaskNeverReachTheSimulationSoAreNotKept() {
        InputState shellOnly = new InputState(false, false, false, false, false, false, true, true, false, true, false);
        assertThat(RecordedInput.encode(shellOnly)).isZero();
    }

    @Test
    void runsOfTheSameInputCompressAndPlayBackTickForTick() {
        InputState right = InputState.of(1, 0, false, false);
        List<InputState> played = new ArrayList<>();
        RecordedInput r = new RecordedInput();
        for (int i = 0; i < 100; i++) {
            r.append(right);
            played.add(right);
        }
        for (int i = 0; i < 50; i++) {
            r.append(InputState.NONE);
            played.add(InputState.NONE);
        }
        r.append(right);
        played.add(right);
        assertThat(r.ticks()).isEqualTo(151);
        assertThat(r.runs()).containsExactly(new int[] {100, RecordedInput.RIGHT}, new int[] {50, 0}, new int[] {1, RecordedInput.RIGHT});

        RecordedInput.Cursor c = RecordedInput.of(r.runs()).cursor();
        List<InputState> back = new ArrayList<>();
        while (c.hasNext()) {
            back.add(c.next());
        }
        assertThat(back).isEqualTo(played);
    }

    @Test
    void malformedRunsAreRejected() {
        assertThatThrownBy(() -> RecordedInput.of(List.of(new int[] {0, 1}))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecordedInput.of(List.of(new int[] {1, 256}))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecordedInput.of(List.of(new int[] {3}))).isInstanceOf(IllegalArgumentException.class);
    }
}
