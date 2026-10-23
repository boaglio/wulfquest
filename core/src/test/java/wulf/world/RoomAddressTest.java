package wulf.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** AGENTS.md §7.1: room addressing, row 0 is north. */
class RoomAddressTest {

    @Test
    void parsesTheCanonicalForm() {
        assertThat(RoomAddress.parse("8,10")).isEqualTo(new RoomAddress(8, 10));
        assertThat(RoomAddress.parse(" 0 , 15 ")).isEqualTo(new RoomAddress(0, 15));
    }

    @Test
    void indexIsRowMajor() {
        assertThat(new RoomAddress(0, 0).index()).isZero();
        assertThat(new RoomAddress(15, 0).index()).isEqualTo(15);
        assertThat(new RoomAddress(0, 1).index()).isEqualTo(16);
        assertThat(new RoomAddress(8, 10).index()).isEqualTo(168);
        assertThat(new RoomAddress(15, 15).index()).isEqualTo(255);
    }

    @Test
    void identifiesTheBoundaryRing() {
        assertThat(new RoomAddress(0, 5).isBoundary()).isTrue();
        assertThat(new RoomAddress(15, 5).isBoundary()).isTrue();
        assertThat(new RoomAddress(5, 0).isBoundary()).isTrue();
        assertThat(new RoomAddress(5, 15).isBoundary()).isTrue();
        assertThat(new RoomAddress(1, 1).isBoundary()).isFalse();
        assertThat(new RoomAddress(14, 14).isBoundary()).isFalse();
    }

    @Test
    void rejectsOffMapAddresses() {
        assertThatThrownBy(() -> new RoomAddress(16, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoomAddress(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RoomAddress(0, 16)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RoomAddress.parse("nope")).isInstanceOf(IllegalArgumentException.class);
    }
}
