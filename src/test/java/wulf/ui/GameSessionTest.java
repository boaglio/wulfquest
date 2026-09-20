package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wulf.data.CreatureData;
import wulf.data.JsonDb;
import wulf.data.PlayerData;
import wulf.data.WulfData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.Ecosystem;
import wulf.sim.Player;
import wulf.sim.RoomPopulator;
import wulf.sim.Simulation;
import wulf.sim.Wulf;
import wulf.sim.WulfRules;
import wulf.world.CollisionWorld;
import wulf.world.WorldGrid;

/** One game's pause, dev toggles and panel line — headless (AGENTS.md §17.4). */
class GameSessionTest {

    private static final PlayerData RULES = new JsonDb(Path.of("data")).load("entities/player", PlayerData.class);
    private static final CollisionWorld OPEN =
            (gx, gy) -> gx < 0 || gy < 0 || gx >= WorldGrid.COLS || gy >= WorldGrid.ROWS;
    private static final InputState RIGHT = InputState.of(1, 0, false, false);

    static Simulation sim() {
        return Simulation.at(RULES, OPEN, 0, Fixed.fp(1000), Fixed.fp(1000));
    }

    /** The same empty world, but walking into a new room is worth something — enough to earn a hi-score. */
    static Simulation scoringSim() {
        Ecosystem scoring = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 10);
        return Simulation.at(RULES, OPEN, 0, Fixed.fp(1000), Fixed.fp(1000), scoring, 1L);
    }

    private static GameSession session() {
        return new GameSession(sim());
    }

    static InputState pressed(boolean fire, boolean pause, boolean quit, boolean kill, boolean mask) {
        return new InputState(false, false, false, false, fire, fire, pause, quit, kill, mask, false);
    }

    @Test
    void pauseFreezesTheSimulationUntilPressedAgain() {
        GameSession s = session();
        s.tick(pressed(false, true, false, false, false), false);
        assertThat(s.paused()).isTrue();
        long tick = s.sim().tick();
        int x = s.sim().player().xFp();
        s.tick(RIGHT, false);
        assertThat(s.sim().tick()).isEqualTo(tick);
        assertThat(s.sim().player().xFp()).isEqualTo(x);
        s.tick(pressed(false, true, false, false, false), false);
        s.tick(RIGHT, false);
        assertThat(s.sim().player().xFp()).isGreaterThan(x);
    }

    @Test
    void theDevKeySummonsTheWulfOnlyInDevMode() {
        WulfData wulf = new JsonDb(Path.of("data")).load("entities/wulf", WulfData.class);
        Ecosystem eco = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 0, WulfRules.of(wulf, 12, 250, Set.of()));
        GameSession s = new GameSession(
                Simulation.at(RULES, OPEN, 0, Fixed.fp(1000), Fixed.fp(1000), eco, 1L));
        InputState summon = new InputState(false, false, false, false, false, false, false, false, false, false, true);
        s.tick(summon, false);
        assertThat(s.sim().wulf().state()).isEqualTo(Wulf.State.ABSENT);
        s.tick(summon, true);
        assertThat(s.sim().wulf().state()).isEqualTo(Wulf.State.WARNING);
    }

    @Test
    void devKeysDoNothingOutsideDevMode() {
        GameSession s = session();
        s.tick(pressed(false, false, false, true, true), false);
        assertThat(s.sim().player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(s.showMask()).isFalse();
        s.tick(pressed(false, false, false, true, true), true);
        assertThat(s.sim().player().mode()).isEqualTo(Player.Mode.DYING);
        assertThat(s.showMask()).isTrue();
    }

    @Test
    void aFinishedGameStopsTickingItsSimulation() {
        GameSession s = session();
        Simulation sim = s.sim();
        playUntilGameOver(s, sim);
        assertThat(s.over()).isTrue();
        long tick = sim.tick();
        s.tick(RIGHT, false);
        assertThat(sim.tick()).isEqualTo(tick);
    }

    @Test
    void thePanelLineReflectsTheSession() {
        GameSession s = session();
        assertThat(s.message(false)).isNull();
        assertThat(s.message(true)).isEqualTo("ROOM 3,5  X232 Y40");
        s.tick(pressed(false, true, false, false, false), false);
        assertThat(s.message(true)).isEqualTo("PAUSED");
    }

    /** Spends every life, which is the only way to reach {@code GAME_OVER}. */
    static void playUntilGameOver(GameSession s, Simulation sim) {
        for (int life = 0; life < RULES.lives().start(); life++) {
            for (int i = 0; i < RULES.spawn().invulnTicks(); i++) {
                s.tick(InputState.NONE, false);
            }
            sim.kill();
            for (int i = 0; i < RULES.death().animTicks() + RULES.death().freezeTicks(); i++) {
                s.tick(InputState.NONE, false);
            }
        }
    }
}
