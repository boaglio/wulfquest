package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.GameConfig;
import wulf.data.LandmarksData;
import wulf.data.WulfData;
import wulf.world.WorldGrid;

/** AGENTS.md §14.9 — Mr Jones's later jobs: fewer pieces in the same rooms, and a harder jungle. */
class NewGamePlusTest {

    private static Content content;
    private static WorldGrid grid;
    private static GameConfig.NewGamePlus jobs;

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
        grid = new WorldGrid(content.rooms());
        jobs = content.game().newGamePlus();
    }

    private static Simulation game(int job, long seed) {
        return Simulation.startingIn(content.player(), grid, content.game().transition().freezeTicks(),
                content.map().startRoom(), content.ecosystem(job), seed);
    }

    @Test
    void theGameItselfHidesAQuarterInEveryLair() {
        Simulation g = game(0, 5L);
        assertThat(g.level()).isZero();
        List<LandmarksData.Lair> lairs = g.landmarks().lairs();
        assertThat(lairs).hasSize(4);
        for (LandmarksData.Lair lair : lairs) {
            assertThat(lair.slotMask()).isEqualTo(1 << lair.piece().slot());
        }
    }

    @Test
    void laterJobsHideFewerPiecesInTheSameSetEachWithItsGuardian() {
        for (int n = 1; n < jobs.levels().size(); n++) {
            GameConfig.Level level = jobs.level(n);
            Set<String> everKept = new HashSet<>();
            for (long seed = 0; seed < 40; seed++) {
                Simulation itself = game(0, seed);
                Simulation g = game(n, seed);
                assertThat(g.level()).isEqualTo(n);
                assertThat(g.landmarks().amuletSet()).as("the same set as the game itself, seed %d", seed)
                        .isEqualTo(itself.landmarks().amuletSet());
                List<LandmarksData.Lair> kept = g.landmarks().lairs();
                assertThat(kept).hasSize(level.pieces().size());
                int all = 0;
                for (int j = 0; j < kept.size(); j++) {
                    LandmarksData.Lair lair = kept.get(j);
                    LandmarksData.Lair same = itself.landmarks().lairs().stream()
                            .filter(l -> l.id().equals(lair.id())).findFirst().orElseThrow();
                    assertThat(lair.room()).isEqualTo(same.room());
                    assertThat(lair.guardian()).isEqualTo(same.guardian());
                    assertThat(lair.slotMask()).isEqualTo(level.slotMask(j));
                    all |= lair.slotMask();
                    everKept.add(lair.id());
                }
                assertThat(all).as("the pieces make the whole amulet").isEqualTo(0b1111);
                assertThat(game(n, seed).landmarks().lairs()).as("a seed always keeps the same").isEqualTo(kept);
            }
            assertThat(everKept).as("job %d keeps any of the lairs, by the seed", n).hasSize(4);
        }
    }

    @Test
    void theWulfIsFasterInEachLaterJob() {
        WulfData.Patrol p = content.wulf().patrol();
        int last = Integer.MIN_VALUE;
        for (int n = 0; n < jobs.levels().size(); n++) {
            Simulation g = game(n, 3L);
            int pace = g.wulf().speedFp;
            assertThat(pace).as("job %d", n).isEqualTo(p.speedFp() * jobs.level(n).wulfSpeedPercent() / 100);
            assertThat(pace).isGreaterThan(last);
            last = pace;
        }
        assertThat(game(0, 3L).wulf().speedFp).as("the game itself keeps the original's").isEqualTo(p.speedFp());
    }

    @Test
    void theJungleStartsFurtherOnInEachLaterJob() {
        for (int n = 0; n < jobs.levels().size(); n++) {
            assertThat(game(n, 3L).ramp()).as("job %d", n).isEqualTo(jobs.level(n).rampHeadStart());
        }
    }
}
