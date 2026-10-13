package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.GameConfig;
import wulf.data.LandmarksData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §17.8 — Easy mode: the same jungle and quest, less that kills. Hard is the game as it was. */
class EasyModeTest {

    private static Content c;
    private static WorldGrid grid;
    private static GameConfig.Easy easy;

    @BeforeAll
    static void load() {
        c = Content.load(Path.of("data"));
        grid = new WorldGrid(c.rooms());
        easy = c.game().easy();
    }

    private static Simulation start(Ecosystem eco) {
        return Simulation.startingIn(c.player(), grid, c.game().transition().freezeTicks(), c.map().startRoom(), eco, 5L);
    }

    @Test
    void theShippedEasyModeIsWhatWasAgreed() {
        assertThat(easy.livesStart()).isEqualTo(9);
        assertThat(easy.swarmRoomMax()).isEqualTo(2);
        assertThat(easy.swarmOpenPerQuarter()).isEqualTo(1);
        assertThat(easy.wulfSpeedPercent()).isEqualTo(75);
        assertThat(easy.flame()).as("no flames").isFalse();
        assertThat(easy.showWayTicks()).as("three minutes").isEqualTo(3 * 60 * c.game().tickHz());
        assertThat(easy.hintsOncePerLife()).isFalse();
    }

    @Test
    void hardIsTheOriginalsGameUntouched() {
        Ecosystem hard = c.ecosystem(0, false);
        Ecosystem plain = c.ecosystem(0);
        assertThat(hard.quest().isEasy()).isFalse();
        assertThat(hard.swarm().roomMax()).isEqualTo(plain.swarm().roomMax());
        assertThat(hard.swarm().openPerQuarter()).isEqualTo(plain.swarm().openPerQuarter());
        assertThat(hard.flame()).isEqualTo(plain.flame());
        Simulation a = start(hard);
        Simulation b = start(plain);
        for (int i = 0; i < 500; i++) {
            a.tick(InputState.NONE);
            b.tick(InputState.NONE);
        }
        assertThat(a.stateHash()).isEqualTo(b.stateHash());
        assertThat(a.easy()).isFalse();
    }

    @Test
    void easyModeChangesWhatItSays() {
        Ecosystem e = c.ecosystem(0, true);
        Ecosystem hard = c.ecosystem(0, false);
        assertThat(e.quest().isEasy()).isTrue();
        assertThat(e.swarm().roomMax()).isEqualTo(2).isLessThan(hard.swarm().roomMax());
        assertThat(e.swarm().openPerQuarter()).isEqualTo(1);
        assertThat(e.flame().comes()).isFalse();
        assertThat(hard.flame().comes()).isTrue();
        Simulation s = start(e);
        assertThat(s.easy()).isTrue();
        assertThat(s.player().lives()).isEqualTo(9);
        assertThat(start(hard).player().lives()).isEqualTo(5);
    }

    @Test
    void noFlameComesHoweverLongHeStands() {
        Simulation s = start(c.ecosystem(0, true));
        s.god(true);
        for (int i = 0; i < 4 * c.flame().lingerTicks(); i++) {
            s.tick(InputState.NONE);
            for (Creature cr : s.creatures()) {
                assertThat(cr.species().id()).isNotEqualTo(c.flame().id());
            }
        }
    }

    @Test
    void theWulfProwlsSlowerAndAJobsPaceStillCounts() {
        int own = start(c.ecosystem(0, false)).wulf().speedFp;
        assertThat(start(c.ecosystem(0, true)).wulf().speedFp).isEqualTo(own * 75 / 100);
        int job2 = c.game().newGamePlus().level(2).wulfSpeedPercent();
        assertThat(start(c.ecosystem(2, true)).wulf().speedFp).isEqualTo(own * (job2 * 75 / 100) / 100);
    }

    @Test
    void aLookingGlassShowsTheWayForThreeMinutes() {
        LandmarksData.Place place = c.landmarks().items().glass().places().get(0);
        RoomAddress r = LandmarksData.room(place.room());
        Simulation s = Simulation.at(c.player(), grid, 0, Fixed.fp(r.col() * Simulation.ROOM_W_PX + place.spot().x()),
                Fixed.fp(r.row() * Simulation.ROOM_H_PX + place.spot().y()), c.ecosystem(0, true), 5L);
        s.tick(InputState.NONE);
        assertThat(s.quest().wayTicks()).isEqualTo(easy.showWayTicks());
    }
}
