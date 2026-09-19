package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.data.JsonDb;
import wulf.data.OrchidData;
import wulf.sim.OrchidField;

/** AGENTS.md §15.1 — which frame a flower shows at each stage, and that it is drawn where it grows. */
class OrchidPainterTest {

    private static final OrchidData DATA = new JsonDb(Path.of("data")).load("entities/orchids", OrchidData.class);
    private static final OrchidField FIELD = new OrchidField(DATA);

    private static String frameAt(int phase) {
        return OrchidPainter.frameFor(FIELD, DATA, phase, "yellow");
    }

    /**
     * Found by eye, then by counting: anchors are room-local, and offsetting them by the
     * room's world origin drew every flower a thousand pixels off screen.
     */
    @Test
    void aFlowerIsDrawnAtItsAnchor() {
        wulf.Content c = wulf.Content.load(Path.of("data"));
        wulf.sim.Ecosystem eco = new wulf.sim.Ecosystem(c.creatures(), wulf.sim.RoomPopulator.NONE, 0,
                wulf.sim.WulfRules.NONE, wulf.sim.QuestRules.NONE, c.orchids());
        wulf.world.RoomAddress room = new wulf.world.RoomAddress(5, 5);
        wulf.sim.Simulation sim = wulf.sim.Simulation.startingIn(c.player(), new wulf.world.WorldGrid(c.rooms()), 6,
                room, eco, 3L);
        int[] anchors = sim.orchidAnchors();
        assertThat(anchors).as("this room grows flowers").isNotEmpty();
        OrchidPainter painter = new OrchidPainter(c.display(), new SpriteBank(c.sprites()), c.orchids());
        Framebuffer fb = new Framebuffer(c.display().canvas().w(), c.display().canvas().h());
        int ground = 3;
        int drawnSomewhere = 0;
        for (int t = 0; t < DATA.cycle().totalTicks(); t += 10) {
            fb.clear(ground);
            painter.paint(fb, sim);
            for (int i = 0; i < anchors.length / 2; i++) {
                int px = c.display().playfield().x() + anchors[2 * i];
                int py = c.display().playfield().y() + anchors[2 * i + 1];
                int drawn = 0;
                for (int y = py - 16; y < py; y++) {
                    for (int x = px - 8; x < px + 8; x++) {
                        drawn += fb.get(x, y) != ground ? 1 : 0;
                    }
                }
                boolean bare = FIELD.stageAt(sim.orchidPhase(i)) == OrchidField.Stage.SEED;
                assertThat(drawn > 0).as("anchor %d at tick %d, stage %s", i, t,
                        FIELD.stageAt(sim.orchidPhase(i))).isEqualTo(!bare);
                drawnSomewhere += drawn;
            }
            for (int k = 0; k < 10; k++) {
                sim.tick(wulf.input.InputState.NONE);
            }
        }
        assertThat(drawnSomewhere).as("something grew").isPositive();
    }

    @Test
    void bareGroundThenAShootThenAColourThenABloomThenAWilt() {
        OrchidData.Cycle c = DATA.cycle();
        int sprout = c.seedTicks();
        int bud = sprout + c.sproutTicks();
        int bloom = bud + c.budTicks();
        int wilt = bloom + c.bloomTicks();
        assertThat(frameAt(0)).as("seed").isNull();
        assertThat(frameAt(sprout)).isEqualTo("sprout");
        assertThat(frameAt(bud)).as("the colour shows before it opens").isEqualTo("bud_yellow");
        assertThat(frameAt(bloom)).isEqualTo("bloom0_yellow");
        assertThat(frameAt(bloom + c.swayTicks())).as("it sways").isEqualTo("bloom1_yellow");
        assertThat(frameAt(bloom + 2 * c.swayTicks())).isEqualTo("bloom0_yellow");
        assertThat(frameAt(wilt)).isEqualTo("wilt_yellow");
    }
}
