package wulf.data;

/**
 * One scenery object placed in a room template (AGENTS.md §7.2).
 * {@code x} and {@code y} are in cells, from the top-left of the playfield —
 * once {@link OriginalMapRepository} has taken the banner rows off {@code y};
 * in the JSON itself {@code y} counts from the top of the original's screen.
 */
public record Placement(String graphic, int x, int y) {
}
