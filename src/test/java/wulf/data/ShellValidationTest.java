package wulf.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;

/** AGENTS.md §17.2, §20.6 — what shell.json must get right beyond its schema. */
class ShellValidationTest {

    private static Content c;

    @BeforeAll
    static void load() {
        c = Content.load(Path.of("data"));
    }

    private static ShellConfig withTitle(ShellConfig.Title title) {
        ShellConfig s = c.shell();
        return new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), title, s.attract(), s.gameOver(), s.ending(), s.tally(),
                s.hiScores(), s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.saves(), s.credits(), s.newGamePlus(), s.difficulty(), s.pause(), s.options());
    }

    private static ShellConfig.Title titleWith(List<String> colours, List<ShellConfig.MenuItem> menu) {
        ShellConfig.Title t = c.shell().title();
        return new ShellConfig.Title(t.wordmark(), t.tagline(), t.items(), t.newGameKey(),
                t.newGameLabel(), t.continueDetail(), new ShellConfig.Cycle(colours, t.cycle().ticksPerStep()), menu, t.footer(),
                t.marker());
    }

    private static ShellConfig.Title titleWithItems(List<ShellConfig.Entry> items) {
        ShellConfig.Title t = c.shell().title();
        return new ShellConfig.Title(t.wordmark(), t.tagline(), items, t.newGameKey(), t.newGameLabel(),
                t.continueDetail(), t.cycle(), t.menu(), t.footer(), t.marker());
    }

    private static DataException error(ShellConfig shell) {
        return catchThrowableOfType(DataException.class, () -> ShellValidator.check(shell, c.palette(), c.input()));
    }

    @Test
    void theShippedShellIsValid() {
        ShellValidator.check(c.shell(), c.palette(), c.input());
        assertThat(c.shell().title().menu()).isNotEmpty();
    }

    @Test
    void aCycleColourMustBeInThePalette() {
        DataException e = error(withTitle(titleWith(List.of("black", "puce"), c.shell().title().menu())));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/title/cycle/colours/1");
        assertThat(e.detail()).contains("puce");
    }

    @Test
    void theNewGameKeyCannotBeAMenuKey() {
        ShellConfig.Title t = c.shell().title();
        ShellConfig.Title clashing = new ShellConfig.Title(t.wordmark(), t.tagline(), t.items(),
                t.menu().get(0).key(), t.newGameLabel(), t.continueDetail(), t.cycle(), t.menu(), t.footer(), t.marker());
        DataException e = error(withTitle(clashing));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("twice");
    }

    @Test
    void theTitlesMenuMustStartAGameAndNameOnlyWhatTheShellDoes() {
        List<ShellConfig.Entry> items = new ArrayList<>(c.shell().title().items());
        items.removeIf(e -> e.id().equals("start"));
        DataException e = error(withTitle(titleWithItems(items)));
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/title/items");
        assertThat(e.detail()).contains("start");

        items.add(new ShellConfig.Entry("start", "START"));
        items.add(new ShellConfig.Entry("dance", "DANCE"));
        e = error(withTitle(titleWithItems(items)));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("dance");
    }

    @Test
    void theMenuMarkerMustBeASpriteWithItsFrameAndFitALine() {
        ShellConfig.Title t = c.shell().title();
        java.util.function.Function<ShellConfig.Marker, DataException> with = m -> catchThrowableOfType(
                DataException.class, () -> ShellValidator.checkMarker(withTitle(new ShellConfig.Title(t.wordmark(),
                        t.tagline(), t.items(), t.newGameKey(), t.newGameLabel(), t.continueDetail(), t.cycle(),
                        t.menu(), t.footer(), m)), c.font(), c.sprites().all()));
        assertThat(with.apply(t.marker())).isNull();
        assertThat(with.apply(new ShellConfig.Marker("no_such", "head")).pointer()).isEqualTo("/title/marker/sprite");
        assertThat(with.apply(new ShellConfig.Marker(t.marker().sprite(), "tail")).pointer())
                .isEqualTo("/title/marker/frame");
        assertThat(with.apply(new ShellConfig.Marker("creature_wulf", "walk0")).detail()).contains("tall");
    }

    @Test
    void twoMenuItemsCannotShareAKey() {
        List<ShellConfig.MenuItem> menu = new ArrayList<>(c.shell().title().menu());
        menu.set(1, new ShellConfig.MenuItem(menu.get(0).key(), "STATS", "X SOMETHING"));
        DataException e = error(withTitle(titleWith(c.shell().title().cycle().colours(), menu)));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("twice");
    }

    @Test
    void aMenuKeyCannotStealADigitTheKeysPageNeeds() {
        List<ShellConfig.MenuItem> menu = new ArrayList<>(c.shell().title().menu());
        menu.set(0, new ShellConfig.MenuItem("1", "STATS", "1 STATS"));
        DataException e = error(withTitle(titleWith(c.shell().title().cycle().colours(), menu)));
        assertThat(e).isNotNull();
        assertThat(e.detail()).contains("input profile");
    }

    @Test
    void theHiScoreAlphabetCannotRepeatALetter() {
        ShellConfig s = c.shell();
        ShellConfig.HiScores h = s.hiScores();
        ShellConfig broken = new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), s.title(), s.attract(),
                s.gameOver(), s.ending(), s.tally(),
                new ShellConfig.HiScores(h.heading(), h.entryHeading(), h.entryPrompt(), h.size(), h.nameLength(),
                        "AABC", h.cursorBlinkTicks(), h.repeatDelayTicks(), h.repeatEveryTicks()),
                s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.saves(), s.credits(), s.newGamePlus(), s.difficulty(), s.pause(), s.options());
        DataException e = error(broken);
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/hiScores/alphabet");
    }

    private static ShellConfig withReveal(ShellConfig.AmuletReveal reveal) {
        ShellConfig s = c.shell();
        return new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), s.title(), s.attract(), s.gameOver(), s.ending(), s.tally(),
                s.hiScores(), s.keyConfig(), s.sound(), s.stats(), reveal, s.map(), s.saves(), s.credits(), s.newGamePlus(), s.difficulty(), s.pause(), s.options());
    }

    private static DataException revealError(ShellConfig.AmuletReveal reveal) {
        return catchThrowableOfType(DataException.class,
                () -> ShellValidator.checkAmuletReveal(withReveal(reveal), c.landmarks(), c.display(), c.font()));
    }

    @Test
    void theAmuletRevealNeedsAVerseForEveryCountOfQuarters() {
        ShellConfig.AmuletReveal r = c.shell().amuletReveal();
        ShellValidator.checkAmuletReveal(c.shell(), c.landmarks(), c.display(), c.font());
        DataException e = revealError(new ShellConfig.AmuletReveal(r.skipAfterTicks(), r.scale(), r.prompt(),
                r.verses().subList(0, 3)));
        assertThat(e.pointer()).isEqualTo("/amuletReveal/verses");
    }

    @Test
    void anAmuletRevealLineWiderThanThePlayfieldIsReported() {
        ShellConfig.AmuletReveal r = c.shell().amuletReveal();
        List<List<String>> verses = new java.util.ArrayList<>(r.verses());
        verses.set(2, List.of("A LINE FAR TOO LONG FOR THIS SCREEN TO HOLD"));
        DataException e = revealError(new ShellConfig.AmuletReveal(r.skipAfterTicks(), r.scale(), r.prompt(), verses));
        assertThat(e.pointer()).isEqualTo("/amuletReveal/verses/2/0");
        assertThat(revealError(new ShellConfig.AmuletReveal(r.skipAfterTicks(), 8, r.prompt(), r.verses())).pointer())
                .isEqualTo("/amuletReveal/scale");
    }

    @Test
    void everyMenuScreenNamesAShellState() {
        for (ShellConfig.MenuItem item : c.shell().title().menu()) {
            assertThat(wulf.ui.Shell.State.valueOf(item.screen())).isNotNull();
        }
    }

    @Test
    void everyKeyConfigActionExistsInEveryProfile() {
        for (var profile : c.input().profiles().entrySet()) {
            String mapKey = c.shell().map().keyFor(profile.getKey());
            for (String action : c.shell().keyConfig().actionOrder()) {
                assertThat(wulf.ui.KeyConfigScreen.keysFor(profile.getValue(), mapKey, action)).as(action).isNotEmpty();
            }
        }
    }

    @Test
    void theCreditsCarryTheAttributionOfSection2point5() {
        String joined = String.join(" ", c.shell().credits().lines()).toLowerCase(java.util.Locale.ROOT);
        assertThat(joined).contains("clean-room").contains("all artwork").contains("original")
                .contains("no assets from any").contains("not affiliated with");
    }

    private static ShellConfig withEnding(ShellConfig.Ending ending) {
        ShellConfig s = c.shell();
        return new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), s.title(), s.attract(), s.gameOver(), ending, s.tally(),
                s.hiScores(), s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.saves(), s.credits(), s.newGamePlus(), s.difficulty(), s.pause(), s.options());
    }

    private static DataException endingError(ShellConfig.Ending ending) {
        return catchThrowableOfType(DataException.class, () -> ShellValidator.checkEnding(withEnding(ending), c.palette(),
                c.display(), c.font(), c.sprites().all(), c.music()));
    }

    private static ShellConfig.Ending ending(List<ShellConfig.Line> lines, String tune, ShellConfig.Jones jones) {
        ShellConfig.Ending e = c.shell().ending();
        return new ShellConfig.Ending(e.backdrop(), e.groundY(), e.vale(), jones, e.amulet(), lines, e.ticksPerChar(),
                tune, e.tuneAt(), e.prompt(), e.promptAt(), e.promptColour());
    }

    @Test
    void theClosingSceneMustFitTheScreenAndNameWhatExists() {
        // §14.7: a line too long, a tune that is not written, a frame Mr Jones has not got.
        ShellConfig.Ending e = c.shell().ending();
        ShellValidator.checkEnding(c.shell(), c.palette(), c.display(), c.font(), c.sprites().all(), c.music());
        List<ShellConfig.Line> lines = new ArrayList<>(e.lines());
        ShellConfig.Line first = lines.get(0);
        lines.set(0, new ShellConfig.Line(first.speaker(), first.at(), first.y(), first.colour(),
                List.of("- I FOUND IT, MR JONES, AND IT TOOK ME A VERY LONG TIME")));
        assertThat(endingError(ending(lines, e.tune(), e.jones())).pointer()).isEqualTo("/ending/lines/0/text/0");
        assertThat(endingError(ending(e.lines(), "raiders", e.jones())).pointer()).isEqualTo("/ending/tune");
        ShellConfig.Jones j = e.jones();
        ShellConfig.Jones noFrame = new ShellConfig.Jones(j.sprite(), j.x(), j.stand(), "wink", j.hold(), j.holdTalk(),
                j.talkTicksPerFrame());
        assertThat(endingError(ending(e.lines(), e.tune(), noFrame)).pointer()).isEqualTo("/ending/jones");
    }

    @Test
    void eachJobEndsItsOwnWayAndTheLastRidesHome() {
        // §14.7, §14.9: a different amulet and different words in each ending; the last, the ride home.
        ShellConfig s = c.shell();
        assertThat(s.endingFor(0)).isSameAs(s.ending());
        assertThat(s.endingFor(0).amuletFrames()).as("the game's own: its four quarters").isNull();
        java.util.Set<String> amulets = new java.util.HashSet<>();
        java.util.Set<String> answers = new java.util.HashSet<>();
        int last = s.newGamePlus().levels().size() - 1;
        for (int n = 0; n <= last; n++) {
            ShellConfig.Ending e = s.endingFor(n);
            amulets.add(e.amuletFrames() == null ? "quarters" : e.amuletFrames().get(0));
            assertThat(e.amuletFrames()).as("the amulet the job was played for").isEqualTo(s.amuletFor(n));
            answers.add(e.lines().stream().filter(l -> l.speaker().equals(ShellConfig.Line.JONES))
                    .map(l -> String.join(" ", l.text())).findFirst().orElseThrow());
            assertThat(e.ride() != null).as("job %d rides home", n).isEqualTo(n == last);
        }
        assertThat(amulets).hasSize(last + 1);
        assertThat(answers).hasSize(last + 1);
    }

    private static ShellConfig withJobEnding(int n, ShellConfig.JobEnding ending) {
        ShellConfig s = c.shell();
        List<ShellConfig.Job> jobs = new ArrayList<>(s.newGamePlus().levels());
        ShellConfig.Job j = jobs.get(n);
        jobs.set(n, new ShellConfig.Job(j.label(), j.tagline(), j.verses(), j.offer(), ending, j.theEnd(), j.amulet()));
        return new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), s.title(), s.attract(), s.gameOver(), s.ending(),
                s.tally(), s.hiScores(), s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.saves(),
                s.credits(), new ShellConfig.NewGamePlus(s.newGamePlus().key(), s.newGamePlus().labelColour(), jobs),
                s.difficulty(), s.pause(), s.options());
    }

    @Test
    void aJobsEndingMustNameWhatExistsAndRideInOrder() {
        ShellConfig.JobEnding last = c.shell().newGamePlus().levels().get(2).ending();
        ShellConfig.Ride r = last.ride();
        java.util.function.Function<ShellConfig, DataException> check = s -> catchThrowableOfType(DataException.class,
                () -> ShellValidator.checkEnding(s, c.palette(), c.display(), c.font(), c.sprites().all(), c.music()));
        ShellConfig.Ride early = new ShellConfig.Ride(r.sprite(), r.ticksPerFrame(), r.comeAt(), r.comeTicks(), r.fromX(),
                r.valeHorse(), r.jonesHorse(), r.comeAt(), r.valeRiding(), r.jonesRiding(), r.rideAt(), r.rideTicks(), r.toX());
        assertThat(check.apply(withJobEnding(2, new ShellConfig.JobEnding(last.lines(), last.tuneAt(),
                last.promptAt(), early))).pointer()).as("mounted before the horses came").isEqualTo("/newGamePlus/levels/2/ending/ride");
        ShellConfig.Ride lame = new ShellConfig.Ride(r.sprite(), r.ticksPerFrame(), r.comeAt(), r.comeTicks(), r.fromX(),
                List.of("bay_trot9"), r.jonesHorse(), r.mountAt(), r.valeRiding(), r.jonesRiding(), r.rideAt(), r.rideTicks(),
                r.toX());
        assertThat(check.apply(withJobEnding(2, new ShellConfig.JobEnding(last.lines(), last.tuneAt(),
                last.promptAt(), lame))).pointer()).isEqualTo("/newGamePlus/levels/2/ending/ride");
    }

    private static ShellConfig withJobAmulet(int n, List<String> frames) {
        ShellConfig s = c.shell();
        List<ShellConfig.Job> jobs = new ArrayList<>(s.newGamePlus().levels());
        ShellConfig.Job j = jobs.get(n);
        jobs.set(n, new ShellConfig.Job(j.label(), j.tagline(), j.verses(), j.offer(), j.ending(), j.theEnd(), frames));
        return new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), s.title(), s.attract(), s.gameOver(), s.ending(),
                s.tally(), s.hiScores(), s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.saves(),
                s.credits(), new ShellConfig.NewGamePlus(s.newGamePlus().key(), s.newGamePlus().labelColour(), jobs),
                s.difficulty(), s.pause(), s.options());
    }

    @Test
    void eachLaterJobHasItsOwnAmuletDrawnAndTheGameKeepsItsQuarters() {
        // §14.9, asked for by the user: the amulet in play is the job's own, quarter by quarter.
        NewGamePlusValidator.checkAmulets(c.shell(), c.landmarks(), c.sprites().all());
        assertThat(c.shell().amuletFor(1)).containsExactly("twin_nw", "twin_ne", "twin_sw", "twin_se");
        assertThat(c.shell().amuletFor(2)).containsExactly("sun_nw", "sun_ne", "sun_sw", "sun_se");
        java.util.function.Function<ShellConfig, DataException> check = s -> catchThrowableOfType(DataException.class,
                () -> NewGamePlusValidator.checkAmulets(s, c.landmarks(), c.sprites().all()));
        assertThat(check.apply(withJobAmulet(1, List.of("moon_nw", "twin_ne", "twin_sw", "twin_se"))).pointer())
                .isEqualTo("/newGamePlus/levels/1/amulet/0");
        assertThat(check.apply(withJobAmulet(2, List.of("sun_nw", "sun_ne"))).pointer())
                .isEqualTo("/newGamePlus/levels/2/amulet");
        assertThat(check.apply(withJobAmulet(0, List.of("nw", "ne", "sw", "se"))).pointer())
                .isEqualTo("/newGamePlus/levels/0/amulet");
    }

    @Test
    void eachEndingsRollStartsWithItsOwnEnd() {
        // §14.7, asked for by the user: the end, then perhaps not, then for real.
        ShellConfig s = c.shell();
        assertThat(s.theEndFor(0)).isEqualTo("THE END?");
        assertThat(s.theEndFor(1)).isEqualTo("THE END??");
        assertThat(s.theEndFor(2)).isEqualTo("THE END! (FOR REAL)");
        assertThat(s.credits().rollRows(s.theEndFor(2)).get(0)).isEqualTo("THE END! (FOR REAL)");
        assertThat(s.credits().rollRows(s.theEndFor(2))).hasSameSizeAs(s.credits().rollRows());
    }

    @Test
    void theCreditsNameWhoMadeTheGame() {
        assertThat(c.shell().credits().lines()).anyMatch(l -> l.contains("FERNANDO BOAGLIO"));
        assertThat(c.shell().credits().rollRows()).containsSubsequence(c.shell().credits().heading(),
                c.shell().credits().lines().get(0));
    }
}
