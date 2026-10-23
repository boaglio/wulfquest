package wulf.sim;

import wulf.data.CreatureData;

/**
 * The treasures as the simulation needs them (AGENTS.md §16.4): where each room's two
 * places lie, how often one holds something, what a pick is worth, and which kinds are
 * a life. Places are indexed {@code room.index() * 2 + place}; {@code x[i] == Integer.MIN_VALUE}
 * where there is none.
 */
public record Treasures(int fillPercent, int score, CreatureData.Box pickupBox, boolean[] extraLife, int[] x, int[] y) {

    public static final int PLACES_PER_ROOM = 2;

    /** No treasures: most tests. */
    public static final Treasures NONE = new Treasures(0, 0, new CreatureData.Box(0, 0, 1, 1), new boolean[0],
            new int[0], new int[0]);

    public boolean any() {
        return extraLife.length > 0 && fillPercent > 0;
    }

    public int kinds() {
        return extraLife.length;
    }

    public boolean hasPlace(int index) {
        return index < x.length && x[index] != Integer.MIN_VALUE;
    }
}
