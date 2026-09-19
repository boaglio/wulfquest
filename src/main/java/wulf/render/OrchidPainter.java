package wulf.render;

import wulf.data.DisplayConfig;
import wulf.data.OrchidData;
import wulf.sim.OrchidField;
import wulf.sim.Simulation;

/**
 * The room's orchids (AGENTS.md §15.1), drawn under everything that moves: a shoot,
 * then a bud already showing the colour it will open in, then the bloom swaying on
 * its two frames, then the wilt in the same colour drained of its bright.
 */
public final class OrchidPainter {

    private final DisplayConfig.Playfield field;
    private final OrchidData data;
    private final Sprite sprite;

    public OrchidPainter(DisplayConfig display, SpriteBank sprites, OrchidData data) {
        this.field = display.playfield();
        this.data = data;
        this.sprite = data.orchids().isEmpty() ? null : sprites.get(data.sprite());
    }

    public void paint(Framebuffer fb, Simulation sim) {
        int[] anchors = sim.orchidAnchors();
        if (sprite == null || anchors.length == 0) {
            return;
        }
        OrchidField orchids = sim.orchidField();
        // Anchors are room-local, unlike creatures: the playfield's corner is all they need.
        int ox = field.x();
        int oy = field.y();
        fb.setClip(field.x(), field.y(), field.w(), field.h());
        try {
            for (int i = 0; i < anchors.length / 2; i++) {
                int phase = sim.orchidPhase(i);
                String frame = frameFor(orchids, data, phase, data.orchid(sim.orchidAt(i)).colour());
                if (frame == null) {
                    continue;
                }
                sprite.blit(fb, frame, anchors[2 * i] + ox, anchors[2 * i + 1] + oy);
            }
        } finally {
            fb.clearClip();
        }
    }

    /** The frame for a stage, or null for bare ground. */
    static String frameFor(OrchidField orchids, OrchidData data, int phase, String colour) {
        OrchidField.Stage stage = orchids.stageAt(phase);
        return switch (stage) {
            case SEED -> null;
            case SPROUT -> "sprout";
            case BUD -> "bud_" + colour;
            case BLOOM -> "bloom" + (orchids.stageTick(phase) / data.cycle().swayTicks()) % 2 + "_" + colour;
            case WILT -> "wilt_" + colour;
        };
    }
}
