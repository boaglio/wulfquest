package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AGENTS.md §20.6 — creature sprites carry the frames the renderer draws. */
class CreatureSpriteValidatorTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final CreatureData CREATURES = DB.load("entities/creatures", CreatureData.class);
    private static final SpriteRepository SPRITES = new SpriteRepository(DB);

    private static Map<String, Set<String>> realFrames() {
        Map<String, Set<String>> m = new HashMap<>();
        SPRITES.all().forEach((name, d) -> {
            Set<String> frames = new HashSet<>();
            d.frames().forEach(f -> frames.add(f.id()));
            frames.addAll(d.mirror().keySet());
            m.put(name, frames);
        });
        return m;
    }

    @Test
    void theShippedSpritesAreComplete() {
        CreatureSpriteValidator.check(CREATURES, SPRITES);
    }

    @Test
    void aMissingSpriteIsReported() {
        Map<String, Set<String>> frames = realFrames();
        frames.remove(CREATURES.creatures().get(0).sprite());
        DataException e = catchThrowableOfType(DataException.class, () -> CreatureSpriteValidator.check(CREATURES, frames));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/creatures/0/sprite");
    }

    @Test
    void aMissingMirrorFrameIsReported() {
        Map<String, Set<String>> frames = realFrames();
        frames.get(CREATURES.creatures().get(0).sprite()).remove("walk1_l");
        DataException e = catchThrowableOfType(DataException.class, () -> CreatureSpriteValidator.check(CREATURES, frames));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/creatures/0/sprite");
        assertThat(e.detail()).contains("walk1_l");
    }

    @Test
    void aMissingPuffIsReported() {
        Map<String, Set<String>> frames = realFrames();
        frames.remove("puff");
        DataException e = catchThrowableOfType(DataException.class, () -> CreatureSpriteValidator.check(CREATURES, frames));
        assertThat(e).isNotNull();
        assertThat(e.file()).endsWith("index.json");
    }
}
