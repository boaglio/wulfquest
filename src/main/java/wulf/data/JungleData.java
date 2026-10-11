package wulf.data;

import java.util.List;

/**
 * Bound view of {@code data/entities/jungle.json} (AGENTS.md §12.8, [CANON]): the original's
 * creatures. The small ones come as a swarm — puffs in the player's room ({@code $9A68}) that
 * turn into one of {@code pick.table} ({@code $A3E0}) — and the roster says how each moves. The
 * big ones are {@code beasts} (§12.9): set down by the actor table ({@code $AC77}) and never killed.
 */
public record JungleData(int schemaVersion, String fidelity, Swarm swarm, Beasts beasts,
                         List<CreatureData.Species> creatures) {

    public JungleData {
        creatures = List.copyOf(creatures);
    }

    /**
     * How the swarm comes ({@code $9A3C}, {@code $9A68}, {@code $9B0D}, {@code $A3C5}, {@code $A493}).
     *
     * @param roomMax                  at most this many in the player's room, puffs and bursts counted ({@code $96BE})
     * @param slots                    and this many anywhere: those left behind keep their slot a while ({@code $9792}, 6)
     * @param everyTicks               how often a new one may come: once an update, two of our ticks (§25 Q5)
     * @param appearTicks              how long the puff takes to become a creature ({@code $01}–{@code $06})
     * @param lingerTicks              how long one left behind waits for the player to come back ({@code $A493}, {@code $40} updates)
     * @param quietAfterGettingUpTicks none come for this long after a fall ({@code $96B1}, {@code $10} updates)
     * @param area                     where a puff may appear, as room-local feet ({@code $9B0D}: {@code $30} + 0..127 each way)
     * @param playerClearancePx        never this near the player ({@code $AB01} with {@code $1717})
     * @param tints                    a puff's colour, one at random, as palette names ({@code $9AB8})
     * @param appearScores             a puff struck while it forms, by its frame ({@code $A4B3} on sprites 1–5)
     * @param facingRightBonus         {@code $A4B3} reads the facing bit too: one facing right is worth this much more
     */
    public record Swarm(int roomMax, int slots, int everyTicks, int appearTicks, int lingerTicks,
                        int quietAfterGettingUpTicks, CreatureData.Box area, int playerClearancePx, Pick pick,
                        List<String> tints, List<Integer> appearScores, int facingRightBonus) {

        public Swarm {
            tints = List.copyOf(tints);
            appearScores = List.copyOf(appearScores);
        }
    }

    /**
     * {@code $A3EC}: a roll of 0..{@code table.size()-1}; at or past {@code open + openPerQuarter × quarters
     * held} it folds back into the first {@code fold} ({@code AND 7}), so the later kinds come only as the amulet fills.
     */
    public record Pick(List<String> table, int open, int openPerQuarter, int fold) {

        public Pick {
            table = List.copyOf(table);
        }
    }

    /**
     * The big beasts (§12.9): rhinos, tribesmen, and the sleeper that wakes a hippo or a warthog.
     *
     * @param followRows    a tribesman or a sleeper this many rows from the player is set down near him ({@code $A630}),
     *                      a row from him, in his block of {@code followColumns} columns ({@code AND $FC}), in its own column of it
     * @param screenTopPx the original's y counts from the top of the screen, banner and all: this much above our rooms
     * @param spots       where a beast set down in a room stands, by room type: the original's x (the sprite's left)
     *                    and y (its feet) — the table the orchids grow at ({@code $D6A6}, {@code $A7D4})
     * @param kinds       each beast as a species; its behaviour names its kind and speeds
     * @param placed      where a new game sets them, from the actor table, in the original's numbers
     */
    public record Beasts(int followRows, int followColumns, int screenTopPx, List<List<Integer>> spots, List<CreatureData.Species> kinds,
                         List<Placed> placed) {

        public Beasts {
            spots = spots.stream().map(List::copyOf).toList();
            kinds = List.copyOf(kinds);
            placed = List.copyOf(placed);
        }
    }

    /** One record of the actor table: a kind, its room ("col,row"), the original's x and y, its heading and colour. */
    public record Placed(String kind, String room, int x, int y, int heading, String tint, boolean asleep) {
    }
}
