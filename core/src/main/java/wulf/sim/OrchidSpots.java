package wulf.sim;

/**
 * Where the original's orchid grows in each room (AGENTS.md §15.2): one flower a room, at its
 * room type's spot ({@code $D6A6}, through {@code $A7D4}), as room-local feet. A room whose
 * type has no spot grows none.
 *
 * @param xPx by {@link wulf.world.RoomAddress#index()}: the feet's x, or {@link #NO_SPOT}
 * @param yPx by room index: the feet's y
 */
public record OrchidSpots(int[] xPx, int[] yPx) {

    /** No spots of the original's: the orchids find their own anchors (§15.1). */
    public static final OrchidSpots NONE = new OrchidSpots(new int[0], new int[0]);

    /** A room that grows no orchid. */
    public static final int NO_SPOT = Integer.MIN_VALUE;

    public OrchidSpots {
        xPx = xPx.clone();
        yPx = yPx.clone();
    }

    public boolean on() {
        return xPx.length > 0;
    }

    /** @return the room's anchor as an {@code x, y} pair, or none */
    int[] anchors(int roomIndex) {
        return xPx[roomIndex] == NO_SPOT ? new int[0] : new int[] {xPx[roomIndex], yPx[roomIndex]};
    }
}
