package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static wulf.sim.EcoFixtures.CREATURES;
import static wulf.sim.EcoFixtures.OPEN;
import static wulf.sim.EcoFixtures.ROOM_X;
import static wulf.sim.EcoFixtures.ROOM_Y;
import static wulf.sim.EcoFixtures.RULES;
import static wulf.sim.EcoFixtures.STILL;
import static wulf.sim.EcoFixtures.Spot;
import static wulf.sim.EcoFixtures.only;
import static wulf.sim.EcoFixtures.run;
import static wulf.sim.EcoFixtures.tuned;
import static wulf.sim.EcoFixtures.with;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import wulf.data.CreatureData;
import wulf.engine.Fixed;
import wulf.input.InputState;

/** AGENTS.md §12.2 and §16.3 — touching, striking, spears, score and extra lives. */
class CombatTest {

    private static final InputState FIRE = InputState.of(0, 0, true, true);

    /** One full swing, then enough ticks for the cooldown to allow the next. */
    private static void swing(Simulation s) {
        s.tick(FIRE);
        run(s, RULES.sabre().totalTicks() - 1 + RULES.sabre().cooldownTicks());
    }

    @Test
    void touchingACreatureKillsThePlayer() {
        Simulation s = with(OPEN, 128, 100, tuned("tribesman", Map.of(), STILL), new Spot("tribesman", 128, 100));
        s.tick(InputState.NONE);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.DYING);
        assertThat(s.player().lives()).isEqualTo(RULES.lives().start() - 1);
    }

    @Test
    void spawnInvulnerabilityProtectsUntilItWearsOff() {
        Simulation s = with(OPEN, 128, 100, tuned("tribesman", Map.of(), STILL), new Spot("tribesman", 128, 100));
        s.tick(InputState.NONE);
        run(s, RULES.death().animTicks() + RULES.death().freezeTicks());
        assertThat(s.player().mode()).as("respawned, with the room re-rolled on top of him").isEqualTo(Player.Mode.ALIVE);
        run(s, RULES.spawn().invulnTicks() - 1);
        assertThat(s.player().mode()).as("still protected").isEqualTo(Player.Mode.ALIVE);
        run(s, 2);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.DYING);
    }

    @Test
    void aSabreStrikeKillsAOneHitCreatureAndScores() {
        // The player faces south by default; the tribesman stands just beyond his box.
        Simulation s = with(OPEN, 128, 100, tuned("tribesman", Map.of(), STILL), new Spot("tribesman", 128, 110));
        Creature c = only(s);
        s.tick(FIRE);
        run(s, RULES.sabre().windupTicks());
        assertThat(c.mode()).isEqualTo(Creature.Mode.DYING);
        assertThat(s.score()).isEqualTo(c.species().score());
        assertThat(s.kills()).isEqualTo(1);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
        run(s, CREATURES.puffTicks());
        assertThat(s.creatures()).as("the puff is gone").isEmpty();
    }

    @Test
    void aThreeHitCreatureTakesThreeSwingsAndEachSwingHitsOnce() {
        Simulation s = with(OPEN, 128, 100, tuned("rhino", Map.of(), STILL), new Spot("rhino", 128, 114));
        Creature c = only(s);
        swing(s);
        assertThat(c.hp()).as("six live ticks, one hit").isEqualTo(2);
        swing(s);
        assertThat(c.hp()).isEqualTo(1);
        assertThat(c.mode()).isEqualTo(Creature.Mode.ALIVE);
        s.tick(FIRE);
        run(s, RULES.sabre().windupTicks());
        assertThat(c.mode()).isEqualTo(Creature.Mode.DYING);
        assertThat(s.score()).isEqualTo(c.species().score());
    }

    @Test
    void aNonLethalHitFlashes() {
        Simulation s = with(OPEN, 128, 100, tuned("rhino", Map.of(), STILL), new Spot("rhino", 128, 114));
        Creature c = only(s);
        s.tick(FIRE);
        run(s, RULES.sabre().windupTicks());
        assertThat(c.hurtTicks()).isEqualTo(CREATURES.hurtFlashTicks());
    }

    @Test
    void theDeathPuffIsHarmless() {
        Simulation s = with(OPEN, 128, 100, tuned("tribesman", Map.of(), STILL), new Spot("tribesman", 128, 110));
        s.tick(FIRE);
        run(s, RULES.sabre().windupTicks());
        for (int i = 0; i < 6; i++) {
            s.tick(InputState.of(0, 1, false, false));   // walk into the puff
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
    }

    @Test
    void aSpearKillsThePlayer() {
        CreatureData thrower = tuned("spearman", Map.of("throwPeriodTicks", 5, "reverseChancePer10k", 0), STILL);
        Simulation s = with(OPEN, 120, 100, thrower, new Spot("spearman", 40, 100));
        Creature c = only(s);
        c.direction(1, 0);
        c.timer(0);
        run(s, 60);
        assertThat(s.player().mode()).isNotEqualTo(Player.Mode.ALIVE);
    }

    @Test
    void theSabreKnocksASpearOutOfTheAirForPoints() {
        CreatureData thrower = tuned("spearman", Map.of("throwPeriodTicks", 400, "reverseChancePer10k", 0), STILL);
        Simulation s = with(OPEN, 120, 100, thrower, new Spot("spearman", 40, 100));
        Creature c = only(s);
        c.direction(1, 0);
        c.timer(398);
        s.tick(InputState.of(-1, 0, false, false));   // face the thrower
        for (int t = 0; t < 80; t++) {
            if (!s.spears().isEmpty() && !s.player().swinging() && s.player().cooldown() == 0) {
                int gap = Fixed.px(s.player().xFp()) - Fixed.px(s.spears().get(0).xFp());
                if (gap <= 22) {
                    s.tick(FIRE);
                    continue;
                }
            }
            s.tick(InputState.NONE);
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(s.score()).isEqualTo(CREATURES.projectile("spear").score());
        assertThat(s.spears()).isEmpty();
    }

    @Test
    void crossingAScoreThresholdAwardsALife() {
        CreatureData prize = tuned("tribesman", Map.of(), STILL, RULES.lives().extraAt().get(0));
        Simulation s = with(OPEN, 128, 100, prize, new Spot("tribesman", 128, 110));
        s.tick(FIRE);
        run(s, RULES.sabre().windupTicks());
        assertThat(s.player().lives()).isEqualTo(RULES.lives().start() + 1);
    }

    @Test
    void theFirstVisitToARoomScoresOnce() {
        Ecosystem eco = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 10);
        Simulation s = Simulation.at(RULES, OPEN, 0, Fixed.fp(ROOM_X + 250), Fixed.fp(ROOM_Y + 100), eco, 1L);
        walkUntilColumn(s, 1, 4);
        assertThat(s.score()).isEqualTo(10);
        walkUntilColumn(s, -1, 3);
        assertThat(s.score()).as("the start room was already visited").isEqualTo(10);
        walkUntilColumn(s, 1, 4);
        assertThat(s.score()).as("no second award").isEqualTo(10);
    }

    @Test
    void everyEntryAndEveryRespawnRollsTheRoomAgain() {
        List<String> calls = new ArrayList<>();
        RoomPopulator counting = (spawner, room, seed, visit) -> calls.add(room + "#" + visit);
        Simulation s = Simulation.at(RULES, OPEN, 0, Fixed.fp(ROOM_X + 250), Fixed.fp(ROOM_Y + 100),
                new Ecosystem(CreatureData.EMPTY, counting, 0), 1L);
        walkUntilColumn(s, 1, 4);
        walkUntilColumn(s, -1, 3);
        s.kill();
        run(s, RULES.death().animTicks() + RULES.death().freezeTicks());
        assertThat(calls).containsExactly("3,5#0", "4,5#0", "3,5#1", "3,5#2");
    }

    private static void walkUntilColumn(Simulation s, int dx, int column) {
        for (int guard = 0; guard < 400 && s.room().col() != column; guard++) {
            s.tick(InputState.of(dx, 0, false, false));
        }
        assertThat(s.room().col()).isEqualTo(column);
    }
}
