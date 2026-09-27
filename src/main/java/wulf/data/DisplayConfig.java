package wulf.data;

/** Bound view of {@code data/config/display.json} (AGENTS.md §5.1). */
public record DisplayConfig(
        int schemaVersion,
        Size canvas,
        Playfield playfield,
        Rect panel,
        Scale scale,
        Border border,
        Crt crt,
        boolean spriteFlicker) {

    public record Size(int w, int h) {
    }

    public record Playfield(int x, int y, int w, int h, int cell, int cols, int rows) {
    }

    public record Rect(int x, int y, int w, int h) {
    }

    public record Scale(int defaultScale, int min, int max, boolean integerOnly) {
        public int clamp(int requested) {
            return Math.max(min, Math.min(max, requested));
        }
    }

    public record Border(String idleColour, boolean flashOnEvent) {
    }

    /**
     * The §5.4 retro toggles. {@code scanlineLuminancePercent} is what every odd
     * output row keeps of its brightness when scanlines are on.
     */
    public record Crt(boolean scanlines, int scanlineLuminancePercent, boolean glow, boolean attributeClash) {
    }

    /** The player's CRT choices (§21.3) over the shipped ones; the shipped scanline depth stays. */
    public DisplayConfig withCrt(Settings.Crt chosen) {
        return new DisplayConfig(schemaVersion, canvas, playfield, panel, scale, border,
                new Crt(chosen.scanlines(), crt.scanlineLuminancePercent(), chosen.glow(), chosen.attributeClash()),
                spriteFlicker);
    }
}
