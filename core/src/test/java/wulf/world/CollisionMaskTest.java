package wulf.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CollisionMaskTest {

    @Test
    void startsFullyWalkable() {
        CollisionMask m = new CollisionMask();
        assertThat(m.solidCount()).isZero();
        assertThat(m.walkablePercent()).isEqualTo(100);
    }

    @Test
    void setsAndClipsCells() {
        CollisionMask m = new CollisionMask();
        m.setSolid(0, 0);
        m.setSolid(31, 21);
        m.setSolid(32, 0);
        m.setSolid(0, 22);   // below the 22nd row: the banner rows are not the room's (§5.1)
        m.setSolid(-1, 5);
        assertThat(m.isSolid(0, 0)).isTrue();
        assertThat(m.isSolid(31, 21)).isTrue();
        assertThat(m.isSolid(0, 22)).isFalse();
        assertThat(m.isSolid(32, 0)).isFalse();
        assertThat(m.solidCount()).isEqualTo(2);
    }

    @Test
    void walkablePercentRoundsDown() {
        CollisionMask m = new CollisionMask();
        for (int x = 0; x < 8; x++) {
            m.setSolid(x, 0);   // 8 of 704 cells
        }
        assertThat(m.walkablePercent()).isEqualTo(98);   // 696*100/704 = 98.86
    }

    @Test
    void rendersAsAscii() {
        CollisionMask m = new CollisionMask();
        m.setSolid(1, 0);
        String[] lines = m.toAscii().split("\n");
        assertThat(lines).hasSize(22);
        assertThat(lines[0]).startsWith(".#.").hasSize(32);
    }
}
