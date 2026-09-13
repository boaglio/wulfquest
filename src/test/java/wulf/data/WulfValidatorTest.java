package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** AGENTS.md §13, §20.6 — the Wulf's data, and what the schema cannot check. */
class WulfValidatorTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final WulfData WULF = DB.load("entities/wulf", WulfData.class);
    private static final PlayerData PLAYER = DB.load("entities/player", PlayerData.class);
    private static final SpriteRepository SPRITES = new SpriteRepository(DB);

    @Test
    void theShippedWulfIsValid() {
        WulfValidator.check(WULF, SPRITES);
    }

    @Test
    void itOutrunsThePlayerAndAlwaysWarnsFirst() {
        assertThat(WULF.speed().xFp()).isGreaterThan(PLAYER.speed().xFp());
        assertThat(WULF.speed().yFp()).isGreaterThan(PLAYER.speed().yFp());
        assertThat(WULF.appearance().warningTicks()).as("§13.3: 0.8 s, non-negotiable").isEqualTo(40);
        assertThat(WULF.appearance().neverInRooms()).contains(WulfData.START);
    }

    @Test
    void aMissingFrameIsReported() {
        Map<String, Set<String>> frames = CreatureSpriteValidator.framesBySprite(SPRITES);
        frames.get(WULF.sprite()).remove("howl_l");
        DataException e = catchThrowableOfType(DataException.class, () -> WulfValidator.check(WULF, frames));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/sprite");
        assertThat(e.detail()).contains("howl_l");
    }

    @Test
    void aFlashLongerThanItsPeriodIsReported() {
        WulfData.Appearance a = WULF.appearance();
        WulfData odd = new WulfData(WULF.schemaVersion(), WULF.fidelity(), WULF.id(), WULF.displayName(), WULF.sprite(),
                WULF.size(), WULF.collisionBox(), WULF.speed(),
                new WulfData.Appearance(a.baseChancePer10k(), a.chancePerAmuletPiecePer10k(), a.chancePerQuietRoomPer10k(),
                        a.quietRoomsCap(), a.rollEveryTicks(), a.minTicksBetweenAppearances(), a.graceTicksAfterPlayerDeath(),
                        a.neverInRooms(), a.warningTicks(), 8, 9, a.minDistancePx()),
                WULF.pursuit(), WULF.parry(), WULF.audio());
        DataException e = catchThrowableOfType(DataException.class, () -> WulfValidator.check(odd, SPRITES));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/appearance/warningFlashOnTicks");
    }
}
