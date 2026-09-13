package wulf.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bound view of {@code data/entities/creatures.json} (AGENTS.md §12). Integers
 * only: it feeds the simulation.
 */
public record CreatureData(
        int schemaVersion,
        String fidelity,
        int diagonalScaleFp,
        int puffTicks,
        int hurtFlashTicks,
        int walkTicksPerFrame,
        List<Species> creatures,
        List<Projectile> projectiles) {

    /** No creatures at all: a simulation with this is M3's empty jungle. */
    public static final CreatureData EMPTY = new CreatureData(1, "none", 256, 1, 0, 1, List.of(), List.of());

    public CreatureData {
        creatures = List.copyOf(creatures);
        projectiles = List.copyOf(projectiles);
    }

    public record Box(int x, int y, int w, int h) {
    }

    public record Size(int w, int h) {
    }

    /** Fixed-point 8.8 pixels per tick. */
    public record Speed(int xFp, int yFp) {
    }

    /** A behaviour kind (§12.5) and its integer parameters, in file order. */
    public record Behaviour(String kind, Map<String, Integer> params) {
        public Behaviour {
            // Order-preserving on purpose: Map.copyOf iterates in an order randomised per JVM run.
            params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
        }

        public int param(String name) {
            Integer v = params.get(name);
            if (v == null) {
                throw new IllegalStateException("behaviour " + kind + " has no parameter '" + name + "'");
            }
            return v;
        }
    }

    public record Species(
            String id,
            String displayName,
            String sprite,
            Size size,
            Box collisionBox,
            Speed speed,
            int hp,
            int score,
            boolean ignoresScenery,
            Behaviour behaviour) {
    }

    public record Projectile(
            String id,
            String displayName,
            int speedFp,
            int lengthPx,
            Box collisionBox,
            int score,
            int maxTicks) {
    }

    public int indexOf(String speciesId) {
        for (int i = 0; i < creatures.size(); i++) {
            if (creatures.get(i).id().equals(speciesId)) {
                return i;
            }
        }
        throw new IllegalStateException("no creature '" + speciesId + "' in creatures.json");
    }

    public boolean has(String speciesId) {
        for (Species s : creatures) {
            if (s.id().equals(speciesId)) {
                return true;
            }
        }
        return false;
    }

    public Species species(String speciesId) {
        return creatures.get(indexOf(speciesId));
    }

    public Projectile projectile(String id) {
        for (Projectile p : projectiles) {
            if (p.id().equals(id)) {
                return p;
            }
        }
        throw new IllegalStateException("no projectile '" + id + "' in creatures.json");
    }
}
