package wulf.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bound view of {@code data/entities/wulf.json} (AGENTS.md §13). Integers only: it feeds the simulation. */
public record WulfData(
        int schemaVersion,
        String fidelity,
        String id,
        String displayName,
        String sprite,
        CreatureData.Size size,
        CreatureData.Box collisionBox,
        CreatureData.Speed speed,
        Appearance appearance,
        Pursuit pursuit,
        Parry parry,
        Audio audio) {

    /** {@code neverInRooms} names: the start room now; lairs and the exit arrive with the quest (M6). */
    public static final String START = "start";
    public static final String LAIR = "lair";
    public static final String EXIT = "exit";

    /** A Wulf that never comes: for simulations without one. */
    public static final WulfData NONE = new WulfData(1, "none", "wulf", "The Wulf", "creature_wulf",
            new CreatureData.Size(1, 1), new CreatureData.Box(0, 0, 1, 1), new CreatureData.Speed(0, 0),
            new Appearance(0, 0, 0, 0, 1, 0, 0, List.of(), 1, 1, 0, 0),
            new Pursuit(0, 0, 1, 1, 1, 0), new Parry(0), new Audio("none", "none", "none", 1));

    /**
     * §13.3. Chances are out of 10 000 per roll.
     *
     * @param minDistancePx how far, centre to centre on either axis, it must appear from the player
     */
    public record Appearance(
            int baseChancePer10k,
            int chancePerAmuletPiecePer10k,
            int chancePerQuietRoomPer10k,
            int quietRoomsCap,
            int rollEveryTicks,
            int minTicksBetweenAppearances,
            int graceTicksAfterPlayerDeath,
            List<String> neverInRooms,
            int warningTicks,
            int warningFlashPeriodTicks,
            int warningFlashOnTicks,
            int minDistancePx) {

        public Appearance {
            neverInRooms = List.copyOf(neverInRooms);
        }
    }

    /**
     * §13.4.
     *
     * @param arrivalClearancePx after a flip it arrives at least this far from the player, centre to centre
     *                           on either axis — a beat behind, never on top; with no such spot yet, it waits
     */
    public record Pursuit(int turnCooldownTicks, int stallTicks, int giveUpTicks, int giveUpRoomDistance, int leaveMaxTicks,
                          int arrivalClearancePx) {
    }

    /** §13.5. The push and the stun live with the sabre, in {@code player.json}. */
    public record Parry(int borderFlashTicks) {
    }

    /** §13.6. Sound names for M8's synthesiser; nothing plays them yet. */
    public record Audio(String warning, String loop, String parry, int loopSilentAtPx) {
    }

    /** The Wulf as a creature the CHASE_DIRECT behaviour can steer (§13.4). */
    public CreatureData.Species asSpecies() {
        Map<String, Integer> params = new LinkedHashMap<>();
        params.put("turnCooldownTicks", pursuit.turnCooldownTicks());
        params.put("stallTicks", pursuit.stallTicks());
        return new CreatureData.Species(id, displayName, sprite, size, collisionBox, speed, 1, 0, false,
                new CreatureData.Behaviour("CHASE_DIRECT", params));
    }
}
