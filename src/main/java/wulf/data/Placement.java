package wulf.data;

/**
 * One scenery object placed in a room template (AGENTS.md §7.2).
 * {@code x} and {@code y} are in cells, from the top-left of the playfield.
 */
public record Placement(String graphic, int x, int y) {
}
