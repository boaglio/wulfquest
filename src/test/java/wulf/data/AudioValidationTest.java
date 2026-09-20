package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;

/** AGENTS.md §18, §20.6 — what the audio database must get right beyond its schemas. */
class AudioValidationTest {

    private static Content c;

    @BeforeAll
    static void load() {
        c = Content.load(Path.of("data"));
    }

    private static SfxData sfxWith(String id, SfxData.Effect effect) {
        Map<String, SfxData.Effect> all = new LinkedHashMap<>(c.sfx().sfx());
        all.put(id, effect);
        SfxData s = c.sfx();
        return new SfxData(s.schemaVersion(), s.sampleRateHz(), s.channels(), s.bufferFrames(), s.cadence(), all);
    }

    private static DataException error(SfxData sfx, MusicData music) {
        return catchThrowableOfType(DataException.class, () -> AudioValidator.check(sfx, music));
    }

    @Test
    void theShippedAudioIsValid() {
        AudioValidator.check(c.sfx(), c.music());
        assertThat(c.sfx().sfx()).hasSizeGreaterThanOrEqualTo(13);
        assertThat(c.music().tunes()).containsOnlyKeys(MusicData.TITLE, MusicData.WIN, MusicData.GAME_OVER);
    }

    @Test
    void theHowlMustOutrankEverything() {
        SfxData.Effect quietHowl = new SfxData.Effect(0, false, c.sfx().effect("wulf_howl").steps());
        DataException e = error(sfxWith("wulf_howl", quietHowl), c.music());
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/sfx/wulf_howl/priority");
    }

    @Test
    void onlyTheGrowlMayHold() {
        SfxData.Effect loopingFanfare = new SfxData.Effect(7, true, c.sfx().effect("amulet_piece").steps());
        DataException e = error(sfxWith("amulet_piece", loopingFanfare), c.music());
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/sfx/amulet_piece/loop");
    }

    @Test
    void aSoundMustBeASquareOrNoise() {
        SfxData.Effect sawtooth = new SfxData.Effect(0, false,
                List.of(new SfxData.Step("sawtooth", 440, 10, 100, 0, 0, 0)));
        DataException e = error(sfxWith("footstep", sawtooth), c.music());
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("square waves and noise");
    }

    @Test
    void moreThanTwoChannelsIsRefused() {
        SfxData s = c.sfx();
        SfxData wide = new SfxData(s.schemaVersion(), s.sampleRateHz(), 3, s.bufferFrames(), s.cadence(), s.sfx());
        DataException e = error(wide, c.music());
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/channels");
    }

    @Test
    void theTitleThemeLoopsAndTheEndingsDoNot() {
        MusicData m = c.music();
        Map<String, MusicData.Tune> tunes = new LinkedHashMap<>(m.tunes());
        MusicData.Tune title = m.tune(MusicData.TITLE);
        tunes.put(MusicData.TITLE, new MusicData.Tune(title.bpm(), false, title.notes()));
        DataException e = error(c.sfx(), new MusicData(m.schemaVersion(), tunes));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/tunes/title/loop");

        tunes = new LinkedHashMap<>(m.tunes());
        MusicData.Tune win = m.tune(MusicData.WIN);
        tunes.put(MusicData.WIN, new MusicData.Tune(win.bpm(), true, win.notes()));
        DataException forever = error(c.sfx(), new MusicData(m.schemaVersion(), tunes));
        assertThat(forever).isNotNull();
        assertThat(forever.pointer()).isEqualTo("/tunes/win/loop");
    }

    @Test
    void theTunesAreTheLengthsSection18point3AsksFor() {
        // 24 bars, 8 bars, 4 bars in 4/4 — checked as whole bars at each tune's own tempo.
        assertThat(bars(c.music().tune(MusicData.TITLE))).isEqualTo(24);
        assertThat(bars(c.music().tune(MusicData.WIN))).isEqualTo(8);
        assertThat(bars(c.music().tune(MusicData.GAME_OVER))).isEqualTo(4);
    }

    private static int bars(MusicData.Tune tune) {
        int msPerBar = 4 * 60_000 / tune.bpm();
        int bars = Math.round((float) tune.totalMs() / msPerBar);
        // The forge rounds a sixteenth to whole milliseconds, so a long tune drifts by a few of them.
        assertThat(Math.abs(tune.totalMs() - bars * msPerBar)).as("within a beat of whole bars")
                .isLessThan(msPerBar / 4);
        return bars;
    }

}
