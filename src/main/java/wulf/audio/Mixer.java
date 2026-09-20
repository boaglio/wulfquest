package wulf.audio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import wulf.data.MusicData;
import wulf.data.SfxData;
import wulf.sim.SoundSink;

/**
 * The audio thread (AGENTS.md §18.1): one {@link SourceDataLine}, two channels,
 * mixed by addition and clipped — which is itself period-correct.
 *
 * <p>Audio never affects the simulation and never blocks a tick. The game thread
 * only ever drops a name into a queue; this thread does the rest. If there is no
 * sound card at all — a CI box, a headless machine — the mixer runs silent and
 * the game does not notice.
 *
 * <p>Music is monophonic and shares the two channels: an effect steals the
 * channel the tune is on, and the tune keeps running underneath, audible again
 * when the effect ends. That is what a one-bit speaker actually did.
 */
public final class Mixer implements SoundSink, AutoCloseable {

    private static final int BYTES_PER_SAMPLE = 2;

    private final SfxData data;
    private final MusicData music;
    private final BeeperSynth synth;
    private final Map<String, short[]> rendered = new ConcurrentHashMap<>();
    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private final Map<String, Boolean> looping = new ConcurrentHashMap<>();
    private final AtomicReference<String> tuneWanted = new AtomicReference<>();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Voice[] voices;
    private volatile int volumePercent;
    private volatile boolean enabled;
    private Thread thread;

    /** One playing sound: where it came from and how far through it is. */
    private static final class Voice {
        String id;
        short[] samples;
        int at;
        int priority;
        boolean loop;

        boolean free() {
            return samples == null;
        }
    }

    public Mixer(SfxData data, MusicData music, boolean enabled, int volumePercent) {
        this.data = data;
        this.music = music;
        this.synth = new BeeperSynth(data.sampleRateHz());
        this.enabled = enabled;
        this.volumePercent = volumePercent;
        this.voices = new Voice[data.channels()];
        for (int i = 0; i < voices.length; i++) {
            voices[i] = new Voice();
        }
    }

    // ---------------------------------------------------------------- the game thread's side

    @Override
    public void play(String id) {
        if (enabled && data.has(id)) {
            pending.add(id);
        }
    }

    @Override
    public void loop(String id, boolean on) {
        if (!data.has(id)) {
            return;
        }
        if (on) {
            if (enabled && looping.put(id, Boolean.TRUE) == null) {
                pending.add(id);
            }
        } else {
            looping.remove(id);
        }
    }

    /** Starts a tune, or stops whatever is playing when {@code id} is null (§18.3). */
    public void tune(String id) {
        tuneWanted.set(enabled ? id : null);
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            pending.clear();
            looping.clear();
            tuneWanted.set(null);
        }
    }

    public boolean enabled() {
        return enabled;
    }

    public void setVolumePercent(int percent) {
        volumePercent = Math.max(0, Math.min(100, percent));
    }

    // ---------------------------------------------------------------- the audio thread's side

    /** Opens the line and starts mixing. Never throws: no sound card simply means no sound. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        thread = new Thread(this::run, "wulf-audio");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() {
        running.set(false);
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void run() {
        AudioFormat format = new AudioFormat(data.sampleRateHz(), 16, 1, true, false);
        SourceDataLine line;
        try {
            line = AudioSystem.getSourceDataLine(format);
            line.open(format, data.bufferFrames() * BYTES_PER_SAMPLE * 2);
            line.start();
        } catch (LineUnavailableException | IllegalArgumentException e) {
            System.err.println("wulfquest: no audio output (" + e.getMessage() + "); playing silent");
            running.set(false);
            return;
        }
        byte[] block = new byte[data.bufferFrames() * BYTES_PER_SAMPLE];
        short[] mixed = new short[data.bufferFrames()];
        try {
            while (running.get()) {
                fill(mixed);
                for (int i = 0; i < mixed.length; i++) {
                    block[2 * i] = (byte) (mixed[i] & 0xFF);
                    block[2 * i + 1] = (byte) ((mixed[i] >> 8) & 0xFF);
                }
                line.write(block, 0, block.length);
            }
        } finally {
            line.stop();
            line.close();
        }
    }

    // ---------------------------------------------------------------- mixing

    private String tunePlaying;
    private short[] tuneSamples;
    private int tuneAt;

    /** One buffer's worth: start what is waiting, advance every voice, add, clip. */
    void fill(short[] out) {
        startPending();
        followTune();
        int gain = volumePercent;
        for (int i = 0; i < out.length; i++) {
            int sum = 0;
            boolean channelZeroBusy = false;
            for (int v = 0; v < voices.length; v++) {
                Voice voice = voices[v];
                if (voice.free()) {
                    continue;
                }
                sum += voice.samples[voice.at++];
                if (v == 0) {
                    channelZeroBusy = true;
                }
                if (voice.at >= voice.samples.length) {
                    if (voice.loop && Boolean.TRUE.equals(looping.get(voice.id))) {
                        voice.at = 0;
                    } else {
                        voice.samples = null;
                        voice.id = null;
                    }
                }
            }
            if (tuneSamples != null) {
                if (!channelZeroBusy) {
                    // A stolen channel simply silences the tune for as long as it holds it.
                    sum += tuneSamples[tuneAt];
                }
                if (++tuneAt >= tuneSamples.length) {
                    if (music.tune(tunePlaying).loop()) {
                        tuneAt = 0;
                    } else {
                        // A tune that has finished must not start again on the next block.
                        tuneWanted.compareAndSet(tunePlaying, null);
                        tuneSamples = null;
                        tunePlaying = null;
                    }
                }
            }
            out[i] = clip(sum * gain / 100);
        }
    }

    private void startPending() {
        for (String id = pending.poll(); id != null; id = pending.poll()) {
            SfxData.Effect effect = data.effect(id);
            Voice voice = pick(effect.priority());
            if (voice == null) {
                continue;   // everything playing is more important; §18.2's stealing order
            }
            voice.id = id;
            voice.samples = samplesFor(id, effect);
            voice.at = 0;
            voice.priority = effect.priority();
            voice.loop = effect.loop();
        }
    }

    /** A free channel, else the least important busy one if this is more important than it. */
    private Voice pick(int priority) {
        for (Voice v : voices) {
            if (v.free()) {
                return v;
            }
        }
        Voice weakest = voices[0];
        for (Voice v : voices) {
            if (v.priority < weakest.priority) {
                weakest = v;
            }
        }
        return priority > weakest.priority ? weakest : null;
    }

    private void followTune() {
        String wanted = tuneWanted.get();
        if (java.util.Objects.equals(wanted, tunePlaying)) {
            return;
        }
        tunePlaying = wanted;
        tuneAt = 0;
        tuneSamples = wanted == null ? null
                : rendered.computeIfAbsent("tune:" + wanted, key -> synth.render(music.tune(wanted)));
    }

    private short[] samplesFor(String id, SfxData.Effect effect) {
        return rendered.computeIfAbsent(id, key -> synth.render(effect));
    }

    private static short clip(int sample) {
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
    }

    /** Renders everything up front so the first of each sound is not late (§23.3). */
    public void warmUp() {
        Map<String, short[]> all = new LinkedHashMap<>();
        for (String id : data.sfx().keySet()) {
            all.put(id, synth.render(data.effect(id)));
        }
        rendered.putAll(all);
    }

    /** For tests: what is on each channel right now. */
    String[] channelIds() {
        String[] ids = new String[voices.length];
        for (int i = 0; i < voices.length; i++) {
            ids[i] = voices[i].id;
        }
        return ids;
    }

    /** For tests: the tune the mixer is currently sounding, or null. */
    String tunePlaying() {
        return tunePlaying;
    }
}
