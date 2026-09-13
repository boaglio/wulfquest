package wulf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import wulf.world.RoomAddress;

/** The command line reports mistakes; it never crashes (AGENTS.md §3.1). */
class BootArgsTest {

    private static Boot.Args parse(String... argv) {
        return Boot.Args.parse(argv);
    }

    @Test
    void parsesTheOptions() {
        Boot.Args a = parse("--room", "7,3", "--scale", "2", "--headless");
        assertThat(a.error()).isNull();
        assertThat(a.room()).isEqualTo(new RoomAddress(7, 3));
        assertThat(a.scale()).isEqualTo(2);
        assertThat(a.headless()).isTrue();
    }

    @Test
    void anOffMapRoomIsAUsageError() {
        assertThat(parse("--room", "16,3").error()).contains("--room").contains("'16,3'").contains("0 to 15");
    }

    @Test
    void aNonNumericRoomIsAUsageError() {
        assertThat(parse("--room", "a,b").error()).contains("--room").contains("'a,b'");
        assertThat(parse("--room", "nope").error()).contains("--room").contains("'nope'");
    }

    @Test
    void aNonNumericScaleIsAUsageError() {
        assertThat(parse("--scale", "big").error()).isEqualTo("--scale expects a whole number, got 'big'");
    }

    @Test
    void aMissingValueIsAUsageError() {
        assertThat(parse("--room").error()).isEqualTo("--room needs a value");
        assertThat(parse("--scale").error()).isEqualTo("--scale needs a value");
        assertThat(parse("--room", "--headless").error()).isEqualTo("--room needs a value");
    }

    @Test
    void anUnknownArgumentIsAUsageError() {
        assertThat(parse("--rooom", "7,3").error()).isEqualTo("unknown option: --rooom");
        assertThat(parse("stray").error()).isEqualTo("unknown option: stray");
    }

    @Test
    void browserStepsClampAtTheMapEdge() {
        assertThat(Boot.step(new RoomAddress(0, 0), -1, 0)).isEqualTo(new RoomAddress(0, 0));
        assertThat(Boot.step(new RoomAddress(15, 15), 0, 1)).isEqualTo(new RoomAddress(15, 15));
        assertThat(Boot.step(new RoomAddress(8, 10), 1, 0)).isEqualTo(new RoomAddress(9, 10));
    }

    @Test
    void browserLinearStepsWrapAroundAll256Rooms() {
        assertThat(Boot.linear(new RoomAddress(15, 15), 1)).isEqualTo(new RoomAddress(0, 0));
        assertThat(Boot.linear(new RoomAddress(0, 0), -1)).isEqualTo(new RoomAddress(15, 15));
        assertThat(Boot.linear(new RoomAddress(15, 3), 1)).isEqualTo(new RoomAddress(0, 4));
    }
}
