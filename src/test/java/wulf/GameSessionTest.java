package wulf;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.data.JsonDb;
import wulf.data.PlayerData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.Player;
import wulf.sim.Simulation;
import wulf.world.CollisionWorld;
import wulf.world.WorldGrid;
import java.util.Set;
import wulf.data.CreatureData;
import wulf.data.WulfData;
import wulf.sim.Ecosystem;
import wulf.sim.RoomPopulator;
import wulf.sim.Wulf;
import wulf.sim.WulfRules;

/** The play loop's pause, quit, game-over and dev behaviour — headless (AGENTS.md §17). */
class GameSessionTest {

    private static final PlayerData RULES = new JsonDb(Path.of("data")).load("entities/player", PlayerData.class);
    private static final CollisionWorld OPEN =
            (gx, gy) -> gx < 0 || gy < 0 || gx >= WorldGrid.COLS || gy >= WorldGrid.ROWS;
    private static final InputState RIGHT = InputState.of(1, 0, false, false);

    private static Boot.GameSession session() {
        return new Boot.GameSession(() -> Simulation.at(RULES, OPEN, 0, Fixed.fp(1000), Fixed.fp(1000)));
    }

    private static InputState pressed(boolean fire, boolean pause, boolean quit, boolean kill, boolean mask) {
        return new InputState(false, false, false, false, fire, fire, pause, quit, kill, mask, false);
    }

    @Test
    void pauseFreezesTheSimulationUntilPressedAgain() {
        Boot.GameSession s = session();
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
    void quitEndsTheSession() {
        Boot.GameSession s = session();
        s.tick(pressed(false, false, true, false, false), false);
        assertThat(s.quit()).isTrue();
    }

    @Test
    void theDevKeySummonsTheWulfOnlyInDevMode() {
        WulfData wulf = new JsonDb(Path.of("data")).load("entities/wulf", WulfData.class);
        Ecosystem eco = new Ecosystem(CreatureData.EMPTY, RoomPopulator.NONE, 0, WulfRules.of(wulf, 12, 250, Set.of()));
        Boot.GameSession s = new Boot.GameSession(
                () -> Simulation.at(RULES, OPEN, 0, Fixed.fp(1000), Fixed.fp(1000), eco, 1L));
        InputState summon = new InputState(false, false, false, false, false, false, false, false, false, false, true);
        s.tick(summon, false);
        assertThat(s.sim().wulf().state()).isEqualTo(Wulf.State.ABSENT);
        s.tick(summon, true);
        assertThat(s.sim().wulf().state()).isEqualTo(Wulf.State.WARNING);
    }

    @Test
    void devKeysDoNothingOutsideDevMode() {
        Boot.GameSession s = session();
        s.tick(pressed(false, false, false, true, true), false);
        assertThat(s.sim().player().mode()).isEqualTo(Player.Mode.ALIVE);
        assertThat(s.showMask()).isFalse();
        s.tick(pressed(false, false, false, true, true), true);
        assertThat(s.sim().player().mode()).isEqualTo(Player.Mode.DYING);
        assertThat(s.showMask()).isTrue();
    }

    @Test
    void gameOverWaitsForFireThenStartsAFreshGame() {
        Boot.GameSession s = session();
        Simulation first = s.sim();
        for (int life = 0; life < RULES.lives().start(); life++) {
            for (int i = 0; i < RULES.spawn().invulnTicks(); i++) {
                s.tick(InputState.NONE, false);
            }
            first.kill();
            for (int i = 0; i < RULES.death().animTicks() + RULES.death().freezeTicks(); i++) {
                s.tick(InputState.NONE, false);
            }
        }
        assertThat(first.player().mode()).isEqualTo(Player.Mode.GAME_OVER);
        assertThat(s.message(false)).isEqualTo("GAME OVER - PRESS FIRE");
        s.tick(RIGHT, false);
        assertThat(s.sim()).isSameAs(first);
        s.tick(pressed(true, false, false, false, false), false);
        assertThat(s.sim()).isNotSameAs(first);
        assertThat(s.sim().player().lives()).isEqualTo(RULES.lives().start());
        assertThat(s.sim().player().mode()).isEqualTo(Player.Mode.ALIVE);
    }

    @Test
    void theSessionRemembersItsBestScore() {
        wulf.sim.Ecosystem scoring = new wulf.sim.Ecosystem(wulf.data.CreatureData.EMPTY, wulf.sim.RoomPopulator.NONE, 10);
        Boot.GameSession s = new Boot.GameSession(
                () -> Simulation.at(RULES, OPEN, 0, Fixed.fp(1000), Fixed.fp(1000), scoring, 1L));
        assertThat(s.best()).isZero();
        for (int i = 0; i < 40 && s.sim().room().col() == 3; i++) {
            s.tick(RIGHT, false);
        }
        assertThat(s.sim().score()).isEqualTo(10);
        assertThat(s.best()).isEqualTo(10);
    }

    @Test
    void thePanelLineReflectsTheSession() {
        Boot.GameSession s = session();
        assertThat(s.message(false)).isNull();
        assertThat(s.message(true)).isEqualTo("ROOM 3,5  X232 Y40");
        s.tick(pressed(false, true, false, false, false), false);
        assertThat(s.message(true)).isEqualTo("PAUSED");
    }
}
