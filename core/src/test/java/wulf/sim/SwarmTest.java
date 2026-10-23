package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.CreatureData;
import wulf.data.OrchidData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.RoomAddress;

/** AGENTS.md §12.8 — the original's small creatures: puffs in the player's room, a few at a time. */
class SwarmTest {

    private static final Content CONTENT = Content.load(Path.of("data"));
    private static final CreatureData ROSTER = CONTENT.originalCreatures();
    private static final SwarmRules SWARM = CONTENT.swarmRules();
    private static final InputState STILL = InputState.NONE;
    private static final RoomAddress ROOM = EcoFixtures.ROOM;

    private static Simulation sim(long seed) {
        Ecosystem eco = new Ecosystem(ROSTER, RoomPopulator.NONE, 0, WulfRules.NONE, QuestRules.NONE, OrchidData.NONE,
                wulf.data.FlameData.NONE, SWARM);
        return Simulation.at(SimFixtures.RULES, SimFixtures.OPEN, 0, Fixed.fp(ROOM.col() * Simulation.ROOM_W_PX + 128),
                Fixed.fp(ROOM.row() * Simulation.ROOM_H_PX + 100), eco, seed);
    }

    private static List<Creature> swarm(Simulation s) {
        List<Creature> out = new ArrayList<>();
        for (Creature c : s.creatures()) {
            if (c.mode() != Creature.Mode.DYING) {
                out.add(c);
            }
        }
        return out;
    }

    @Test
    void theyComeAsPuffsAndNeverMoreThanTheRoomHolds() {
        Simulation s = sim(3L);
        s.god(true);
        boolean sawPuff = false;
        boolean sawCreature = false;
        for (int t = 0; t < 600; t++) {
            s.tick(STILL);
            assertThat(s.creatures().size()).as("$96BE: three to a room").isLessThanOrEqualTo(SWARM.roomMax());
            for (Creature c : s.creatures()) {
                sawPuff |= c.appearing();
                sawCreature |= c.alive();
                assertThat(SWARM.tints()).as("in one of the four colours").contains(c.tint());
            }
        }
        assertThat(sawPuff).isTrue();
        assertThat(sawCreature).isTrue();
    }

    @Test
    void theLaterKindsComeOnlyAsTheAmuletFills() {
        // $A3EC: rolls past 8 + 2 × quarters fold back into the first eight.
        for (int roll = 0; roll < SWARM.pick().length; roll++) {
            assertThat(SWARM.pick(roll, 0)).isEqualTo(SWARM.pick()[roll % 8]);
            assertThat(SWARM.pick(roll, 4)).isEqualTo(SWARM.pick()[roll]);
        }
        assertThat(ROSTER.creatures().get(SWARM.pick(9, 0)).id()).isEqualTo("scorpion");
        assertThat(ROSTER.creatures().get(SWARM.pick(9, 1)).id()).isEqualTo("bird");
        assertThat(ROSTER.creatures().get(SWARM.pick(10, 1)).id()).isEqualTo("bat");
    }

    @Test
    void thoseLeftBehindWaitAWhileForHimToComeBack() {
        Simulation s = sim(5L);
        s.god(true);
        while (swarm(s).stream().filter(Creature::alive).count() < 2) {
            s.tick(STILL);
        }
        List<Integer> before = swarm(s).stream().map(Creature::id).toList();
        RoomAddress home = s.room();
        while (s.room().equals(home)) {
            s.tick(SimFixtures.RIGHT);
        }
        assertThat(s.creatures()).extracting(Creature::id).doesNotContainAnyElementsOf(before);
        SimFixtures.run(s, SimFixtures.RIGHT, 2);
        while (!s.room().equals(home)) {
            s.tick(SimFixtures.LEFT);
        }
        assertThat(s.creatures()).extracting(Creature::id).containsAll(before.stream()
                .filter(id -> s.creatures().stream().anyMatch(c -> c.id() == id)).toList());
        assertThat(s.creatures().stream().filter(c -> before.contains(c.id())).count())
                .as("back within lingerTicks, they are still there").isGreaterThan(0);
    }

    @Test
    void longGoneTheyAreGone() {
        Simulation s = sim(5L);
        s.god(true);
        while (swarm(s).stream().filter(Creature::alive).count() < 2) {
            s.tick(STILL);
        }
        List<Integer> before = swarm(s).stream().map(Creature::id).toList();
        RoomAddress home = s.room();
        while (s.room().equals(home)) {
            s.tick(SimFixtures.RIGHT);
        }
        SimFixtures.run(s, STILL, SWARM.lingerTicks() + 10);
        while (!s.room().equals(home)) {
            s.tick(SimFixtures.LEFT);
        }
        assertThat(s.creatures()).extracting(Creature::id).doesNotContainAnyElementsOf(before);
    }

    @Test
    void aTouchBurstsItAndTakesHimWithIt() {
        Simulation s = sim(11L);
        int deaths = s.deaths();
        int t = 0;
        while (s.deaths() == deaths && t++ < 5000) {
            s.tick(STILL);
        }
        assertThat(s.deaths()).as("standing still among them, he is touched").isGreaterThan(deaths);
        assertThat(s.score()).as("$A4A6: what touched him burst, and scored").isGreaterThan(0);
    }

    @Test
    void whenHeFallsTheRoomsSmallCreaturesBurstAndNoneComeForAWhile() {
        Simulation s = sim(13L);
        while (swarm(s).stream().noneMatch(Creature::alive)) {
            s.tick(STILL);
        }
        s.kill();
        s.tick(STILL);
        assertThat(swarm(s)).as("$A54F: with him down, they burst").noneMatch(Creature::alive);
        while (s.player().mode() != Player.Mode.ALIVE) {
            s.tick(STILL);
        }
        for (int t = 0; t < SWARM.quietAfterGettingUpTicks(); t++) {
            assertThat(swarm(s)).as("$96B1: a quiet moment after getting up").isEmpty();
            s.tick(STILL);
        }
    }

    @Test
    void fencingFacingItHeIsSafe() {
        // $AB68: fighting, the side he faces is the blade's.
        Simulation s = sim(17L);
        int t = 0;
        while (s.deaths() == 0 && s.kills() < 5 && t++ < 20000) {
            Creature near = null;
            for (Creature c : s.creatures()) {
                if (c.alive()) {
                    near = c;
                }
            }
            int dx = near == null ? 0 : near.centreXPx() < Fixed.px(s.player().xFp()) ? -1 : 1;
            s.tick(InputState.of(dx, 0, true, false));
        }
        assertThat(s.kills()).as("facing them with the blade out, he takes them").isGreaterThanOrEqualTo(5);
    }

    @Test
    void sameSeedSameSwarm() {
        Simulation a = sim(21L);
        Simulation b = sim(21L);
        for (int t = 0; t < 2000; t++) {
            InputState in = InputState.of((t / 97) % 3 - 1, (t / 61) % 3 - 1, t % 40 < 10, t % 40 == 0);
            a.tick(in);
            b.tick(in);
            assertThat(a.stateHash()).isEqualTo(b.stateHash());
        }
    }
}
