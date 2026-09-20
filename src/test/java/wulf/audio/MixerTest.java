package wulf.audio;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.data.JsonDb;
import wulf.data.MusicData;
import wulf.data.SfxData;

/**
 * The mixer's own rules (AGENTS.md §18.1, §18.2), exercised without ever opening
 * a line: {@code fill} is the whole of the mixing, and it is pure arithmetic.
 */
class MixerTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final SfxData SFX = DB.load("audio/sfx", SfxData.class);
    private static final MusicData MUSIC = DB.load("audio/music", MusicData.class);

    private Mixer mixer() {
        return new Mixer(SFX, MUSIC, true, 100);
    }

    private static short[] block(Mixer m) {
        return block(m, SFX.bufferFrames());
    }

    /** A short block, for looking at the channels before the shortest sound has finished. */
    private static short[] block(Mixer m, int frames) {
        short[] out = new short[frames];
        m.fill(out);
        return out;
    }

    private static boolean silent(short[] block) {
        for (short s : block) {
            if (s != 0) {
                return false;
            }
        }
        return true;
    }

    @Test
    void aSoundIsSilentUntilItIsAskedFor() {
        Mixer m = mixer();
        assertThat(silent(block(m))).isTrue();
        m.play("sabre_swing");
        assertThat(silent(block(m))).isFalse();
    }

    @Test
    void twoSoundsUseBothChannels() {
        Mixer m = mixer();
        m.play("sabre_swing");
        m.play("creature_die");
        block(m, 64);
        assertThat(m.channelIds()).containsExactlyInAnyOrder("sabre_swing", "creature_die");
    }

    @Test
    void aLouderEventStealsAChannelAndAQuieterOneIsDropped() {
        Mixer m = mixer();
        m.play("footstep");
        m.play("room_flip");
        block(m, 64);
        // Both channels are busy with low-priority noise; the howl outranks them (§18.2).
        m.play("wulf_howl");
        block(m, 64);
        assertThat(m.channelIds()).contains("wulf_howl");

        Mixer full = mixer();
        full.play("wulf_howl");
        full.play("player_die");
        block(full, 64);
        full.play("footstep");
        block(full, 64);
        assertThat(full.channelIds()).as("nothing important was pushed aside for a footstep")
                .containsExactlyInAnyOrder("wulf_howl", "player_die");
    }

    @Test
    void theGrowlHoldsUntilItIsStopped() {
        Mixer m = mixer();
        m.loop("wulf_growl", true);
        int growlSamples = new BeeperSynth(SFX.sampleRateHz()).render(SFX.effect("wulf_growl")).length;
        int blocks = growlSamples / SFX.bufferFrames() + 2;
        for (int i = 0; i < blocks; i++) {
            block(m);
        }
        assertThat(m.channelIds()).as("still growling after it would have ended").contains("wulf_growl");
        m.loop("wulf_growl", false);
        for (int i = 0; i < blocks; i++) {
            block(m);
        }
        assertThat(m.channelIds()).doesNotContain("wulf_growl");
    }

    @Test
    void askingForTheSameLoopTwiceStartsItOnce() {
        Mixer m = mixer();
        m.loop("wulf_growl", true);
        m.loop("wulf_growl", true);
        block(m, 64);
        assertThat(m.channelIds()[1]).as("the second channel is still free").isNull();
    }

    @Test
    void aTunePlaysAndStops() {
        Mixer m = mixer();
        m.tune(MusicData.TITLE);
        assertThat(silent(block(m))).isFalse();
        assertThat(m.tunePlaying()).isEqualTo(MusicData.TITLE);
        m.tune(null);
        assertThat(silent(block(m))).isTrue();
        assertThat(m.tunePlaying()).isNull();
    }

    @Test
    void turningTheSoundOffSilencesEverything() {
        Mixer m = new Mixer(SFX, MUSIC, false, 100);
        m.tune(MusicData.TITLE);
        m.play("wulf_howl");
        m.loop("wulf_growl", true);
        assertThat(silent(block(m))).isTrue();
    }

    @Test
    void theVolumeScalesWhatComesOut() {
        Mixer loud = mixer();
        loud.play("wulf_howl");
        short[] atFull = block(loud);
        Mixer quiet = new Mixer(SFX, MUSIC, true, 50);
        quiet.play("wulf_howl");
        short[] atHalf = block(quiet);
        int full = 0;
        int half = 0;
        for (int i = 0; i < atFull.length; i++) {
            full += Math.abs(atFull[i]);
            half += Math.abs(atHalf[i]);
        }
        assertThat(half).isLessThan(full);
        assertThat(half * 2).isCloseTo(full, org.assertj.core.data.Percentage.withPercentage(5));
    }

    @Test
    void nothingEverClipsPastSixteenBits() {
        Mixer m = mixer();
        m.tune(MusicData.TITLE);
        m.play("wulf_howl");
        m.play("player_die");
        for (int i = 0; i < 10; i++) {
            for (short s : block(m)) {
                assertThat(s).isBetween(Short.MIN_VALUE, Short.MAX_VALUE);
            }
        }
    }
}
