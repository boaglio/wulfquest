package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.engine.Rng;
import wulf.input.InputState;
import wulf.world.WorldGrid;

/**
 * AGENTS.md §22.4 — 100 000 ticks of play on the real map, with creatures, and no
 * display: nothing throws, nothing grows without bound, and it is quick.
 */
class HeadlessSimTest {

    @Test
    void survivesAHundredThousandTicksOfPlay() {
        Content c = Content.load(Path.of("data"));
        WorldGrid grid = new WorldGrid(c.rooms());
        Ecosystem eco = c.ecosystem();
        int freeze = c.game().transition().freezeTicks();
        Rng input = new Rng(2026);

        Simulation sim = Simulation.startingIn(c.player(), grid, freeze, c.map().startRoom(), eco, 1L);
        int maxCreatures = 0;
        int maxSpears = 0;
        long kills = 0;
        long deaths = 0;
        int games = 1;
        Set<String> rooms = new TreeSet<>();
        Set<String> species = new TreeSet<>();
        int dx = 0;
        int dy = 0;
        long start = System.nanoTime();
        for (int t = 0; t < 100_000; t++) {
            if (t % 40 == 0) {
                dx = input.nextInt(3) - 1;
                dy = input.nextInt(3) - 1;
            }
            boolean fire = input.chance(800);
            if (sim.player().mode() == Player.Mode.GAME_OVER) {
                kills += sim.kills();
                sim = Simulation.startingIn(c.player(), grid, freeze, c.map().startRoom(), eco, t);
                games++;
            }
            Player.Mode before = sim.player().mode();
            sim.tick(InputState.of(dx, dy, fire, fire));
            if (before == Player.Mode.ALIVE && sim.player().mode() == Player.Mode.DYING) {
                deaths++;
            }
            maxCreatures = Math.max(maxCreatures, sim.creatures().size());
            maxSpears = Math.max(maxSpears, sim.spears().size());
            rooms.add(sim.room().toString());
            for (Creature creature : sim.creatures()) {
                species.add(creature.species().id());
            }
        }
        kills += sim.kills();
        long millis = (System.nanoTime() - start) / 1_000_000L;
        System.out.printf("headless: %d ms, %d games, %d deaths, %d kills, %d rooms, %d species, max %d creatures, %d spears%n",
                millis, games, deaths, kills, rooms.size(), species.size(), maxCreatures, maxSpears);

        assertThat(millis).as("100k ticks in under 10 s").isLessThan(10_000L);
        assertThat(maxCreatures).as("bounded population").isLessThanOrEqualTo(20);
        assertThat(maxSpears).as("bounded spears").isLessThanOrEqualTo(20);
        assertThat(rooms.size()).as("it went places").isGreaterThan(5);
        assertThat(deaths).as("creatures are dangerous").isPositive();
        assertThat(species.size()).as("met a variety of creatures").isGreaterThanOrEqualTo(6);
    }
}
