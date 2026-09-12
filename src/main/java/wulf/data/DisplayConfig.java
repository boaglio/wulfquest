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

    public record Crt(boolean scanlines, boolean glow, boolean attributeClash) {
    }
}
