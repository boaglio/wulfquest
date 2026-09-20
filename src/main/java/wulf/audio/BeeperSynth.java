package wulf.audio;

import wulf.data.MusicData;
import wulf.data.SfxData;

/**
 * Square waves with hard edges, and a noise source made of an LFSR (AGENTS.md
 * §18.1). No reverb, no filters, no envelope beyond a linear attack and decay:
 * this is a one-bit speaker being hit, not an instrument.
 *
 * <p>Pure arithmetic — it renders a sound to 16-bit mono samples and knows
 * nothing about lines, threads or files, so the whole synth is testable by
 * looking at the numbers it produces.
 */
public final class BeeperSynth {

    /** The click a hard-edged square makes if it stops mid-cycle; a short fade is cheaper than a pop. */
    private static final int EDGE_FADE_SAMPLES = 32;
    /** Classic 16-bit maximal-length LFSR taps — the period's noise, not white noise. */
    private static final int LFSR_TAPS = 0xB400;

    private final int sampleRateHz;

    public BeeperSynth(int sampleRateHz) {
        this.sampleRateHz = sampleRateHz;
    }

    public int sampleRateHz() {
        return sampleRateHz;
    }

    /** One effect rendered end to end, in samples. */
    public short[] render(SfxData.Effect effect) {
        int total = 0;
        for (SfxData.Step step : effect.steps()) {
            total += samplesFor(step.ms());
        }
        short[] out = new short[total];
        int at = 0;
        int lfsr = 0xACE1;
        for (SfxData.Step step : effect.steps()) {
            lfsr = renderStep(step, out, at, lfsr);
            at += samplesFor(step.ms());
        }
        return out;
    }

    /** One tune rendered end to end: a monophonic square line and nothing else (§18.3). */
    public short[] render(MusicData.Tune tune) {
        int total = 0;
        for (MusicData.Note note : tune.notes()) {
            total += samplesFor(note.ms());
        }
        short[] out = new short[total];
        int at = 0;
        for (MusicData.Note note : tune.notes()) {
            if (!note.rest()) {
                SfxData.Step step = new SfxData.Step(SfxData.Step.SQUARE, hzOf(note), note.ms(), 420, 0, 0, 0);
                renderStep(step, out, at, 0);
            }
            at += samplesFor(note.ms());
        }
        return out;
    }

    public int samplesFor(int ms) {
        return Math.max(1, sampleRateHz * ms / 1000);
    }

    /**
     * Equal temperament from A4 = 440 Hz. Floating point is fine here: this is
     * the audio thread's arithmetic, not the simulation's (§6.2 binds the sim).
     */
    public static int hzOf(MusicData.Note note) {
        int semitone = semitoneOf(note.note());
        int fromA4 = semitone - 9 + (note.octave() - 4) * 12;
        return (int) Math.round(440.0 * Math.pow(2.0, fromA4 / 12.0));
    }

    private static int semitoneOf(String name) {
        return switch (name) {
            case "C" -> 0;
            case "C#" -> 1;
            case "D" -> 2;
            case "D#" -> 3;
            case "E" -> 4;
            case "F" -> 5;
            case "F#" -> 6;
            case "G" -> 7;
            case "G#" -> 8;
            case "A" -> 9;
            case "A#" -> 10;
            case "B" -> 11;
            default -> throw new IllegalArgumentException("not a note name: " + name);
        };
    }

    /** @return the noise generator's state after the step, so a multi-step sound does not repeat itself */
    private int renderStep(SfxData.Step step, short[] out, int at, int lfsr) {
        int n = Math.min(samplesFor(step.ms()), out.length - at);
        int peak = Short.MAX_VALUE * step.volPerMille() / 1000;
        boolean noise = SfxData.Step.NOISE.equals(step.shape());
        int state = lfsr == 0 ? 0xACE1 : lfsr;

        double phase = 0;
        double noisePhase = 0;
        int level = 1;
        for (int i = 0; i < n; i++) {
            double hz = step.hz();
            if (step.slides()) {
                hz = step.hz() + (step.slideToHz() - step.hz()) * (double) i / n;
            }
            if (step.vibrates()) {
                double bend = Math.sin(2 * Math.PI * step.vibratoHz() * i / sampleRateHz);
                hz *= 1 + bend * step.vibratoDepthPerMille() / 1000.0;
            }
            int amplitude = peak * fade(i, n) / 1000;
            if (noise) {
                noisePhase += hz / sampleRateHz;
                while (noisePhase >= 1) {
                    noisePhase -= 1;
                    // Shift right, feed the low bit back through the taps.
                    state = (state >>> 1) ^ (-(state & 1) & LFSR_TAPS);
                    level = (state & 1) == 0 ? -1 : 1;
                }
            } else {
                phase += hz / sampleRateHz;
                while (phase >= 1) {
                    phase -= 1;
                }
                level = phase < 0.5 ? 1 : -1;   // hard edges: no smoothing anywhere
            }
            out[at + i] = (short) (level * amplitude);
        }
        return state;
    }

    /** Linear attack and decay in per-mille, just enough that a step does not click. */
    private static int fade(int i, int n) {
        int edge = Math.min(EDGE_FADE_SAMPLES, n / 2);
        if (edge <= 0) {
            return 1000;
        }
        if (i < edge) {
            return 1000 * i / edge;
        }
        if (i >= n - edge) {
            return 1000 * (n - 1 - i) / edge;
        }
        return 1000;
    }
}
