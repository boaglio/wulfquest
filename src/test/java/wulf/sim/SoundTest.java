package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.JsonDb;
import wulf.data.PlayerData;
import wulf.data.SfxData;
import wulf.engine.Fixed;
import wulf.engine.Replay;
import wulf.input.InputState;
import wulf.input.RecordedInput;
import wulf.world.CollisionWorld;
import wulf.world.WorldGrid;

/**
 * What the jungle announces, and — much more importantly — that announcing it
 * changes nothing (AGENTS.md §18.1, §6.4). A sink is write-only: the same run
 * with sound and without must produce the same state hash, tick for tick.
 */
class SoundTest {

    private static final PlayerData RULES = new JsonDb(Path.of("data")).load("entities/player", PlayerData.class);
    private static final SfxData SFX = new JsonDb(Path.of("data")).load("audio/sfx", SfxData.class);
    private static final CollisionWorld OPEN =
            (gx, gy) -> gx < 0 || gy < 0 || gx >= WorldGrid.COLS || gy >= WorldGrid.ROWS;

    /** Records everything it is told, in order. */
    private static final class Ear implements SoundSink {
        final List<String> heard = new ArrayList<>();
        final Map<String, Boolean> loops = new LinkedHashMap<>();

        @Override
        public void play(String id) {
            heard.add(id);
        }

        @Override
        public void loop(String id, boolean on) {
            loops.put(id, on);
            heard.add((on ? "+" : "-") + id);
        }

        int count(String id) {
            return (int) heard.stream().filter(id::equals).count();
        }
    }

    private static Simulation sim(Ear ear) {
        Simulation s = Simulation.at(RULES, OPEN, 0, Fixed.fp(1000), Fixed.fp(1000));
        if (ear != null) {
            s.sounds(ear, SFX.cadence().footstepEveryTicks());
        }
        return s;
    }

    @Test
    void walkingMakesFootstepsAtTheCadenceAndStandingStillMakesNone() {
        Ear ear = new Ear();
        Simulation s = sim(ear);
        int ticks = SFX.cadence().footstepEveryTicks() * 4;
        for (int i = 0; i < ticks; i++) {
            s.play(InputState.of(1, 0, false, false), false);
        }
        assertThat(ear.count("footstep")).isEqualTo(4);
        ear.heard.clear();
        for (int i = 0; i < ticks; i++) {
            s.play(InputState.NONE, false);
        }
        assertThat(ear.count("footstep")).isZero();
    }

    @Test
    void theSabreIsHeardOncePerSwing() {
        Ear ear = new Ear();
        Simulation s = sim(ear);
        s.play(InputState.of(0, 0, true, true), false);
        assertThat(ear.count("sabre_swing")).isEqualTo(1);
        for (int i = 0; i < RULES.sabre().totalTicks() - 1; i++) {
            s.play(InputState.NONE, false);
        }
        assertThat(ear.count("sabre_swing")).as("one swing, one sound").isEqualTo(1);
    }

    @Test
    void dyingIsHeardAndTakesTheGrowlWithIt() {
        Ear ear = new Ear();
        Simulation s = sim(ear);
        for (int i = 0; i < RULES.spawn().invulnTicks(); i++) {
            s.play(InputState.NONE, false);
        }
        s.kill();
        assertThat(ear.count("player_die")).isEqualTo(1);
        assertThat(ear.loops).containsEntry("wulf_growl", false);
    }

    @Test
    void theWulfHowlsWhenItArrivesAndGrowlsUntilItIsGone() {
        Content c = Content.load(Path.of("data"));
        Ear ear = new Ear();
        Simulation s = Simulation.startingIn(c.player(), new WorldGrid(c.rooms()),
                c.game().transition().freezeTicks(), c.map().startRoom(), c.ecosystem(), 7L);
        s.sounds(ear, SFX.cadence().footstepEveryTicks());
        assertThat(s.summonWulf()).isTrue();
        assertThat(ear.count("wulf_howl")).isEqualTo(1);
        assertThat(ear.loops).containsEntry("wulf_growl", true);
    }

    @Test
    void aRoomFlipIsHeard() {
        Ear ear = new Ear();
        Simulation s = sim(ear);
        wulf.world.RoomAddress from = s.room();
        for (int i = 0; i < 2000 && s.room().equals(from); i++) {
            s.play(InputState.of(1, 0, false, false), false);
        }
        assertThat(s.room()).as("it walked out of the room").isNotEqualTo(from);
        assertThat(ear.count("room_flip")).isEqualTo(1);
    }

    @Test
    void everyNameTheSimulationAnnouncesExistsInSfxJson() {
        Content c = Content.load(Path.of("data"));
        Ear ear = new Ear();
        Replay replay = Replay.read(Path.of("replays/full_run.json"));
        Simulation s = Simulation.startingIn(c.player(), new WorldGrid(c.rooms()),
                c.game().transition().freezeTicks(), replay.start(), c.ecosystem(), replay.seed());
        s.sounds(ear, SFX.cadence().footstepEveryTicks());
        RecordedInput.Cursor in = replay.recorded().cursor();
        for (int i = 0; i < 20_000 && in.hasNext(); i++) {
            s.play(in.next(), replay.dev());
        }
        assertThat(ear.heard).isNotEmpty();
        for (String id : ear.heard) {
            String name = id.startsWith("+") || id.startsWith("-") ? id.substring(1) : id;
            assertThat(SFX.has(name)).as("sfx.json has '%s'", name).isTrue();
        }
    }

    @Test
    void aRunSoundsDifferentButSimulatesIdentically() {
        Content c = Content.load(Path.of("data"));
        Replay replay = Replay.read(Path.of("replays/attract.json"));
        Ear ear = new Ear();
        Simulation loud = Simulation.startingIn(c.player(), new WorldGrid(c.rooms()),
                c.game().transition().freezeTicks(), replay.start(), c.ecosystem(), replay.seed());
        loud.sounds(ear, SFX.cadence().footstepEveryTicks());
        Simulation quiet = Simulation.startingIn(c.player(), new WorldGrid(c.rooms()),
                c.game().transition().freezeTicks(), replay.start(), c.ecosystem(), replay.seed());

        RecordedInput.Cursor a = replay.recorded().cursor();
        RecordedInput.Cursor b = replay.recorded().cursor();
        while (a.hasNext()) {
            loud.play(a.next(), replay.dev());
            quiet.play(b.next(), replay.dev());
            assertThat(loud.stateHash()).as("at tick %d", loud.tick()).isEqualTo(quiet.stateHash());
        }
        assertThat(ear.heard).as("it did make noise").isNotEmpty();
    }
}
