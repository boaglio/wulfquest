package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;

/** AGENTS.md §15, §20.6 — what orchids.json must get right beyond its schema. */
class OrchidValidationTest {

    private static Content c;
    private static Map<String, Set<String>> frames;

    @BeforeAll
    static void load() {
        c = Content.load(Path.of("data"));
        frames = CreatureSpriteValidator.framesBySprite(c.sprites());
    }

    private static OrchidData withFirst(String colour, String effect, int speedScaleFp) {
        OrchidData o = c.orchids();
        List<OrchidData.Orchid> all = new ArrayList<>(o.orchids());
        OrchidData.Orchid f = all.get(0);
        all.set(0, new OrchidData.Orchid(colour, f.weight(), effect, f.ticks(), speedScaleFp, f.score()));
        return new OrchidData(o.schemaVersion(), o.fidelity(), o.sprite(), o.cycle(), o.anchors(), all, o.delirium(),
                o.immunity());
    }

    private static DataException error(OrchidData o, Map<String, Set<String>> f) {
        return catchThrowableOfType(DataException.class, () -> OrchidValidator.check(o, c.palette(), f));
    }

    @Test
    void theShippedOrchidsAreValid() {
        OrchidValidator.check(c.orchids(), c.palette(), frames);
        assertThat(c.orchids().orchids()).as("all six colours").hasSize(6);
    }

    @Test
    void aColourThePaletteDoesNotHaveIsReported() {
        DataException e = error(withFirst("chartreuse", "HASTE", 512), frames);
        assertThat(e.pointer()).isEqualTo("/orchids/0/colour");
    }

    @Test
    void aColourWithNoFlowerDrawnForItIsReported() {
        Map<String, Set<String>> missing = CreatureSpriteValidator.framesBySprite(c.sprites());
        missing.get(c.orchids().sprite()).remove("bloom1_" + c.orchids().orchids().get(0).colour());
        DataException e = error(c.orchids(), missing);
        assertThat(e.pointer()).isEqualTo("/orchids/0/colour");
        assertThat(e.detail()).contains("bloom1_");
    }

    @Test
    void anEffectThatDoesNothingIsReported() {
        OrchidData same = withFirst(c.orchids().orchids().get(0).colour(), "HASTE", 256);
        DataException e = error(same, frames);
        assertThat(e.pointer()).isEqualTo("/orchids/0/speedScaleFp");
    }

    @Test
    void twoFlowersWithTheSameEffectAreReported() {
        OrchidData o = c.orchids();
        List<OrchidData.Orchid> all = new ArrayList<>(o.orchids());
        OrchidData.Orchid second = all.get(1);
        all.set(1, new OrchidData.Orchid(second.colour(), second.weight(), all.get(0).effect(), second.ticks(),
                second.speedScaleFp(), second.score()));
        DataException e = error(new OrchidData(o.schemaVersion(), o.fidelity(), o.sprite(), o.cycle(), o.anchors(), all,
                o.delirium(), o.immunity()), frames);
        assertThat(e.pointer()).isEqualTo("/orchids/1/effect");
    }
}
