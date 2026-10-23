package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import wulf.data.CreatureData;
import wulf.engine.Fixed;
import wulf.input.InputState;

/** AGENTS.md §17.5 — after game over the game may go on, three times, its score starting again. */
class ContinueTest {

    private static final InputState FIRE = InputState.of(0, 0, true, true);
    /** The dev kill key, so a death is in the input and a replay of it dies too. */
    private static final InputState KILL = new InputState(false, false, false, false, false, false, false, false,
            true, false, false);

    /** An empty jungle where a new room scores, with three continues. */
    private static Simulation sim() {
        Ecosystem eco = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 10)
                .withQuest(QuestRules.NONE.withContinues(3));
        return Simulation.at(SimFixtures.RULES, SimFixtures.OPEN, 0, Fixed.fp(1000), Fixed.fp(1000), eco, 1L);
    }

    private static void play(Simulation s, InputState in, List<InputState> log) {
        s.play(in, true);
        log.add(in);
    }

    /** Every life lost, down to game over. */
    private static void dieOut(Simulation s, List<InputState> log) {
        for (int guard = 0; guard < 100_000 && s.player().mode() != Player.Mode.GAME_OVER; guard++) {
            boolean hittable = s.player().mode() == Player.Mode.ALIVE && s.player().invulnTicks() == 0;
            play(s, hittable ? KILL : InputState.NONE, log);
        }
        assertThat(s.player().mode()).isEqualTo(Player.Mode.GAME_OVER);
    }

    @Test
    void fireAfterGameOverGoesOnWhereHeFellWithFreshLivesAndNoScore() {
        List<InputState> log = new ArrayList<>();
        Simulation s = sim();
        for (int i = 0; i < 200 && s.score() == 0; i++) {
            play(s, SimFixtures.RIGHT, log);
        }
        long scored = s.score();
        assertThat(scored).isPositive();
        dieOut(s, log);
        int x = s.player().xFp();
        play(s, InputState.NONE, log);
        assertThat(s.player().mode()).as("nothing without fire").isEqualTo(Player.Mode.GAME_OVER);

        play(s, FIRE, log);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(s.player().lives()).isEqualTo(SimFixtures.RULES.lives().start());
        assertThat(s.player().xFp()).as("where he fell").isEqualTo(x);
        assertThat(s.score()).isZero();
        assertThat(s.bestScore()).as("the run's best stays for the table").isEqualTo(scored);
        assertThat(s.continuesUsed()).isEqualTo(1);
        assertThat(s.continuesLeft()).isEqualTo(2);

        // The same input, played again, takes the same continue: a replay or a quick load knows it.
        Simulation again = sim();
        for (InputState in : log) {
            again.play(in, true);
        }
        assertThat(again.stateHash()).isEqualTo(s.stateHash());
    }

    @Test
    void threeTimesOnly() {
        List<InputState> log = new ArrayList<>();
        Simulation s = sim();
        for (int c = 0; c < 3; c++) {
            dieOut(s, log);
            play(s, FIRE, log);
            assertThat(s.player().mode()).as("continue %d", c + 1).isEqualTo(Player.Mode.ALIVE);
        }
        assertThat(s.continuesLeft()).isZero();
        dieOut(s, log);
        play(s, FIRE, log);
        assertThat(s.player().mode()).as("no fourth").isEqualTo(Player.Mode.GAME_OVER);
    }

    @Test
    void aGameWithoutContinuesEndsAsItAlwaysDid() {
        Simulation s = Simulation.at(SimFixtures.RULES, SimFixtures.OPEN, 0, Fixed.fp(1000), Fixed.fp(1000));
        List<InputState> log = new ArrayList<>();
        dieOut(s, log);
        s.play(FIRE, false);
        assertThat(s.player().mode()).isEqualTo(Player.Mode.GAME_OVER);
        assertThat(s.continuesLeft()).isZero();
    }
}
