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
        Delirium delirium,
        Immunity immunity) {

    public OrchidData {
        orchids = List.copyOf(orchids);
    }

    /** No orchids: a jungle that does not flower, for simulations without them. */
    public static final OrchidData NONE = new OrchidData(1, "none", "orchid", new Cycle(1, 1, 1, 1, 1, 1),
            new Anchors(0, 0, 1, 0), List.of(), new Delirium(1, 1, 1), new Immunity(1));

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
     */
    public record Orchid(String colour, int weight, String effect, int ticks, int speedScaleFp, int score) {
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
