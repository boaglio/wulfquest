package wulf.data;

import java.util.List;
import java.util.Map;

/** Bound view of {@code data/entities/player.json} (AGENTS.md §11.1). Integers only: it feeds the simulation. */
public record PlayerData(
        int schemaVersion,
        String id,
        String displayName,
        String sprite,
        Box collisionBox,
        Speed speed,
        Lives lives,
        Spawn spawn,
        Death death,
        Sabre sabre,
        Map<String, Animation> animations) {

    public PlayerData {
        animations = Map.copyOf(animations);
    }

    /** The feet box, relative to the sprite origin (bottom-centre), in pixels (§7.4). */
    public record Box(int x, int y, int w, int h) {
    }

    /** Fixed-point 8.8 pixels per tick (§11.2). */
    public record Speed(int xFp, int yFp, int diagonalScaleFp) {
    }

    public record Lives(int start, int max, List<Integer> extraAt) {
        public Lives {
            extraAt = List.copyOf(extraAt);
        }
    }

    /** Respawn rules (§11.7). {@code insideRoomPx} keeps the whole sprite on screen, off the flip-screen clip. */
    public record Spawn(int invulnTicks, int blinkPeriodTicks, Margin insideRoomPx) {
    }

    /** Minimum distances of the feet origin from each room edge, in pixels. */
    public record Margin(int left, int right, int top, int bottom) {
    }

    public record Death(int animTicks, int freezeTicks, boolean keepAmulet) {
    }

    public record Sabre(
            int windupTicks,
            int activeTicks,
            int recoverTicks,
            int cooldownTicks,
            int reachPx,
            int thicknessPx,
            int diagonalOffsetPx,
            int moveSpeedScaleFp,
            int repelWulfPx,
            int repelWulfStunTicks) {

        public int totalTicks() {
            return windupTicks + activeTicks + recoverTicks;
        }
    }

    public record Animation(List<String> frames, int ticksPerFrame, boolean loop) {
        public Animation {
            frames = List.copyOf(frames);
        }
    }

    public Animation animation(String name) {
        Animation a = animations.get(name);
        if (a == null) {
            throw new IllegalStateException("player.json has no animation '" + name + "'");
        }
        return a;
    }
}
