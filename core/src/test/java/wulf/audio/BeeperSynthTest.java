package wulf.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.data.JsonDb;
import wulf.data.MusicData;
import wulf.data.SfxData;

/** Square waves with hard edges and an LFSR for noise (AGENTS.md §18.1). */
class BeeperSynthTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final SfxData SFX = DB.load("audio/sfx", SfxData.class);
    private static final MusicData MUSIC = DB.load("audio/music", MusicData.class);

    private final BeeperSynth synth = new BeeperSynth(SFX.sampleRateHz());

    private static SfxData.Effect one(SfxData.Step step) {
        return new SfxData.Effect(0, false, List.of(step));
    }

    private static SfxData.Step square(int hz, int ms) {
        return new SfxData.Step(SfxData.Step.SQUARE, hz, ms, 1000, 0, 0, 0);
    }

    /** Zero crossings in the steady middle of the sound, away from the fades. */
    private static int crossings(short[] s) {
        int count = 0;
        int edge = 64;
        for (int i = edge + 1; i < s.length - edge; i++) {
            if (Integer.signum(s[i]) != Integer.signum(s[i - 1])) {
                count++;
            }
        }
        return count;
    }

    @Test
    void aSquareWaveHasTheFrequencyItWasAskedFor() {
        short[] out = synth.render(one(square(1000, 1000)));
        assertThat(out).hasSize(SFX.sampleRateHz());
        // A 1 kHz square crosses zero twice per cycle, minus the trimmed edges.
        assertThat(crossings(out)).isBetween(1980, 2000);
    }

    @Test
    void aSquareWaveIsOnlyEverAtItsTwoLevels() {
        short[] out = synth.render(one(new SfxData.Step(SfxData.Step.SQUARE, 500, 200, 500, 0, 0, 0)));
        int peak = Short.MAX_VALUE / 2;
        int middle = out.length / 2;
        assertThat(Math.abs(out[middle])).isCloseTo(peak, org.assertj.core.data.Offset.offset(2));
    }

    @Test
    void itFadesInAndOutSoAStepDoesNotClick() {
        short[] out = synth.render(one(square(440, 200)));
        assertThat(Math.abs(out[0])).isLessThan(Math.abs(out[out.length / 2]));
        assertThat(Math.abs(out[out.length - 1])).isLessThan(Math.abs(out[out.length / 2]));
    }

    @Test
    void aSlideEndsSomewhereElseFromWhereItStarted() {
        short[] out = synth.render(one(new SfxData.Step(SfxData.Step.SQUARE, 200, 400, 1000, 2000, 0, 0)));
        int third = out.length / 3;
        short[] head = java.util.Arrays.copyOfRange(out, 0, third);
        short[] tail = java.util.Arrays.copyOfRange(out, out.length - third, out.length);
        assertThat(crossings(tail)).as("it ends higher than it began").isGreaterThan(crossings(head) * 3);
    }

    @Test
    void noiseIsNotASquareWave() {
        short[] noise = synth.render(one(new SfxData.Step(SfxData.Step.NOISE, 1000, 500, 1000, 0, 0, 0)));
        short[] tone = synth.render(one(square(1000, 500)));
        assertThat(crossings(noise)).as("noise does not cross regularly").isNotEqualTo(crossings(tone));
        assertThat(noise).isNotEqualTo(tone);
    }

    @Test
    void theSameSoundRendersTheSameSamplesEveryTime() {
        SfxData.Effect howl = SFX.effect("wulf_howl");
        assertThat(synth.render(howl)).isEqualTo(synth.render(howl));
    }

    @Test
    void aMultiStepSoundIsAsLongAsItsSteps() {
        SfxData.Effect piece = SFX.effect("amulet_piece");
        int ms = piece.steps().stream().mapToInt(SfxData.Step::ms).sum();
        assertThat(synth.render(piece).length).isCloseTo(synth.samplesFor(ms),
                org.assertj.core.data.Offset.offset(piece.steps().size()));
    }

    @Test
    void everyShippedSoundRenders() {
        for (String id : SFX.sfx().keySet()) {
            assertThat(synth.render(SFX.effect(id))).as(id).isNotEmpty();
        }
    }

    @Test
    void notesLandOnTheirEqualTemperedFrequencies() {
        assertThat(BeeperSynth.hzOf(new MusicData.Note("A", 4, 100))).isEqualTo(440);
        assertThat(BeeperSynth.hzOf(new MusicData.Note("A", 3, 100))).isEqualTo(220);
        assertThat(BeeperSynth.hzOf(new MusicData.Note("A", 5, 100))).isEqualTo(880);
        assertThat(BeeperSynth.hzOf(new MusicData.Note("C", 4, 100))).isEqualTo(262);
        assertThat(BeeperSynth.hzOf(new MusicData.Note("E", 4, 100))).isEqualTo(330);
    }

    @Test
    void aRestIsSilence() {
        MusicData.Tune rest = new MusicData.Tune(120, false, List.of(new MusicData.Note("R", 0, 100)));
        assertThat(synth.render(rest)).containsOnly((short) 0);
    }

    @Test
    void everyTuneRendersAtItsOwnLength() {
        for (String id : MUSIC.tunes().keySet()) {
            MusicData.Tune tune = MUSIC.tune(id);
            short[] out = synth.render(tune);
            assertThat(out.length).as(id).isCloseTo(synth.samplesFor(tune.totalMs()),
                    org.assertj.core.data.Offset.offset(tune.notes().size()));
            assertThat(out).as("%s is not silent", id).isNotEqualTo(new short[out.length]);
        }
    }
}
