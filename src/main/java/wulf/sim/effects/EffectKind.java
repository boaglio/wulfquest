package wulf.sim.effects;

/**
 * What an orchid's bloom does to the player (AGENTS.md §15.2). One place, a few
 * methods: nothing else in the simulation branches on which flower it was.
 */
public enum EffectKind {

    /** Nothing: no bloom taken, or the last one has worn off. */
    NONE,
    /** Speed up. Exhilarating and lethal: you overshoot into creatures. */
    HASTE,
    /** Slow down. The Wulf becomes a death sentence. */
    TORPOR,
    /** Left/right and up/down inverted, at the input, so a recorded replay stays faithful. */
    REVERSAL,
    /** Contact cannot kill you — but the Keeper still bars the arch. */
    IMMUNITY,
    /** Now and then your legs go their own way. */
    DELIRIUM,
    /** Every creature in the room stands still. The one unambiguously good flower. */
    STILLNESS;

    /** @param scaleFp the orchid's {@code speedScaleFp}; 256 is unchanged */
    public int modifySpeedFp(int baseFp, int scaleFp) {
        return this == HASTE || this == TORPOR ? (baseFp * scaleFp) >> 8 : baseFp;
    }

    public boolean invertsInput() {
        return this == REVERSAL;
    }

    public boolean scramblesInput() {
        return this == DELIRIUM;
    }

    public boolean blocksLethalContact() {
        return this == IMMUNITY;
    }

    public boolean freezesCreatures() {
        return this == STILLNESS;
    }

    /** Whether the player's sprite flashes while this is active (§15.2, IMMUNITY). */
    public boolean flashesThePlayer() {
        return this == IMMUNITY;
    }

    public static EffectKind of(String name) {
        for (EffectKind kind : values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        throw new IllegalStateException("no effect kind '" + name + "'");
    }
}
