package wulf.data;

import java.util.List;

/**
 * Bound view of {@code data/entities/orchids.json} (AGENTS.md §15): how a flower
 * grows, what each colour does to you, and how many grow in a room.
 */
public record OrchidData(
        int schemaVersion,
        String fidelity,
        String sprite,
        Cycle cycle,
        Anchors anchors,
        List<Orchid> orchids,
        List<Orchid> inventedOrchids,
        Tint tint,
        Delirium delirium,
        Immunity immunity) {

    public OrchidData {
        orchids = List.copyOf(orchids);
        inventedOrchids = List.copyOf(inventedOrchids);
    }

    /** No orchids: a jungle that does not flower, for simulations without them. */
    public static final OrchidData NONE = new OrchidData(1, "none", "orchid", new Cycle(1, 1, 1, 1, 1, 1),
            new Anchors(0, 0, 1, 0), List.of(), List.of(), new Tint(0, 1), new Delirium(1, 1, 1), new Immunity(1));

    /** §15.4, {@code features.inventedOrchids} [NEW]: the same garden, growing our own six instead. */
    public OrchidData invented() {
        return new OrchidData(schemaVersion, fidelity, sprite, cycle, anchors, inventedOrchids, inventedOrchids, tint,
                delirium, immunity);
    }

    /** §15.1. The stages in order; one full turn is their sum. */
    public record Cycle(int seedTicks, int sproutTicks, int budTicks, int bloomTicks, int wiltTicks, int swayTicks) {

        public int totalTicks() {
            return seedTicks + sproutTicks + budTicks + bloomTicks + wiltTicks;
        }
    }

    /**
     * @param insetPx how far from the room's edges an orchid may root: a bloom half over
     *                an edge would be picked from the next room
     */
    public record Anchors(int perRoom, int minSpacingPx, int attempts, int insetPx) {
    }

    /**
     * @param speedScaleFp what the effect does to the player's base speed, 8.8 — 256 leaves it alone
     * @param untouchable  §15.2: while it lasts nothing can hurt him — the original's every colour but white
     */
    public record Orchid(String colour, int weight, String effect, int ticks, int speedScaleFp, int score,
                         boolean untouchable) {
    }

    /**
     * §15.2: an untouchable Vale is drawn in his flower's colour; for its last {@code warnTicks}
     * he flashes back to himself every {@code flashTicks}, as the original's does ({@code $AF7A}).
     */
    public record Tint(int warnTicks, int flashTicks) {
    }

    public record Delirium(int everyMinTicks, int everyRandomTicks, int holdTicks) {
    }

    public record Immunity(int flashPeriodTicks) {
    }

    public int indexOfEffect(String effect) {
        for (int i = 0; i < orchids.size(); i++) {
            if (orchids.get(i).effect().equals(effect)) {
                return i;
            }
        }
        return -1;
    }

    public Orchid orchid(int index) {
        return orchids.get(index);
    }
}
