package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;

/** AGENTS.md §14.9: the later jobs are checked at load, in game.json and in shell.json. */
class NewGamePlusValidationTest {

    private static Content c;

    @BeforeAll
    static void load() {
        c = Content.load(Path.of("data"));
    }

    private static GameConfig gameWith(List<GameConfig.Level> levels) {
        GameConfig g = c.game();
        return new GameConfig(g.schemaVersion(), g.tickHz(), g.maxCatchupTicks(), g.transition(), g.difficulty(), g.exit(),
                new GameConfig.NewGamePlus(levels), g.features());
    }

    private static List<GameConfig.Level> levels() {
        return new ArrayList<>(c.game().newGamePlus().levels());
    }

    private static DataException gameError(List<GameConfig.Level> levels) {
        return catchThrowableOfType(DataException.class, () -> NewGamePlusValidator.check(gameWith(levels), c.landmarks()));
    }

    private static ShellConfig shellWith(List<ShellConfig.Job> jobs) {
        ShellConfig s = c.shell();
        ShellConfig.NewGamePlus n = s.newGamePlus();
        return new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), s.title(), s.attract(), s.gameOver(), s.ending(),
                s.tally(), s.hiScores(), s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.quickSave(),
                s.credits(), new ShellConfig.NewGamePlus(n.key(), n.labelColour(), jobs));
    }

    private static DataException wordsError(List<ShellConfig.Job> jobs) {
        return catchThrowableOfType(DataException.class, () -> NewGamePlusValidator.checkWords(shellWith(jobs), c.game(),
                c.palette(), c.display(), c.font()));
    }

    @Test
    void theShippedJobsPass() {
        NewGamePlusValidator.check(c.game(), c.landmarks());
        NewGamePlusValidator.checkWords(c.shell(), c.game(), c.palette(), c.display(), c.font());
    }

    @Test
    void thePiecesMustFillEverySlotOnce() {
        List<GameConfig.Level> twice = levels();
        twice.set(1, new GameConfig.Level(List.of(List.of(0, 1), List.of(1, 2, 3)), 2, 110));
        assertThat(gameError(twice).pointer()).isEqualTo("/newGamePlus/levels/1/pieces/1");

        List<GameConfig.Level> short_ = levels();
        short_.set(1, new GameConfig.Level(List.of(List.of(0, 1), List.of(2)), 2, 110));
        assertThat(gameError(short_).pointer()).isEqualTo("/newGamePlus/levels/1/pieces");
    }

    @Test
    void levelZeroIsTheGameItself() {
        List<GameConfig.Level> harder = levels();
        GameConfig.Level first = harder.get(0);
        harder.set(0, new GameConfig.Level(first.pieces(), 1, 100));
        assertThat(gameError(harder).pointer()).isEqualTo("/newGamePlus/levels/0");

        List<GameConfig.Level> swapped = levels();
        swapped.set(0, new GameConfig.Level(List.of(List.of(1), List.of(0), List.of(2), List.of(3)), 0, 100));
        assertThat(gameError(swapped).pointer()).isEqualTo("/newGamePlus/levels/0/pieces/0");
    }

    @Test
    void everyJobHasItsWordsAndTheyFit() {
        List<ShellConfig.Job> jobs = new ArrayList<>(c.shell().newGamePlus().levels());
        assertThat(wordsError(jobs.subList(0, 2)).pointer()).as("one entry a level").isEqualTo("/newGamePlus/levels");

        List<ShellConfig.Job> noOffer = new ArrayList<>(jobs);
        noOffer.set(1, new ShellConfig.Job(jobs.get(1).label(), jobs.get(1).verses(), null));
        assertThat(wordsError(noOffer).pointer()).isEqualTo("/newGamePlus/levels/1");

        List<ShellConfig.Job> oneVerse = new ArrayList<>(jobs);
        oneVerse.set(1, new ShellConfig.Job(jobs.get(1).label(), jobs.get(1).verses().subList(0, 1), jobs.get(1).offer()));
        assertThat(wordsError(oneVerse).pointer()).as("a verse a piece").isEqualTo("/newGamePlus/levels/1/verses");

        List<ShellConfig.Job> wide = new ArrayList<>(jobs);
        ShellConfig.Line line = jobs.get(2).offer().lines().get(0);
        wide.set(2, new ShellConfig.Job(jobs.get(2).label(), jobs.get(2).verses(), new ShellConfig.Offer(List.of(
                new ShellConfig.Line(line.speaker(), line.at(), line.y(), line.colour(),
                        List.of("- I HAVE ONE LAST JOB FOR YOU, AND IT IS A LONG ONE"))), 10)));
        assertThat(wordsError(wide).pointer()).isEqualTo("/newGamePlus/levels/2/offer/lines/0/text/0");
    }
}
