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
                s.hiScores(), s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.quickSave(), s.credits(), s.newGamePlus(), s.difficulty());
    }

    private static ShellConfig.Title titleWith(List<String> colours, List<ShellConfig.MenuItem> menu) {
        ShellConfig.Title t = c.shell().title();
        return new ShellConfig.Title(t.wordmark(), t.tagline(), t.prompt(),
                new ShellConfig.Cycle(colours, t.cycle().ticksPerStep()), menu, t.footer());
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
                s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.quickSave(), s.credits(), s.newGamePlus(), s.difficulty());
        DataException e = error(broken);
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/hiScores/alphabet");
    }

    private static ShellConfig withReveal(ShellConfig.AmuletReveal reveal) {
        ShellConfig s = c.shell();
        return new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), s.title(), s.attract(), s.gameOver(), s.ending(), s.tally(),
                s.hiScores(), s.keyConfig(), s.sound(), s.stats(), reveal, s.map(), s.quickSave(), s.credits(), s.newGamePlus(), s.difficulty());
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
        for (InputConfig.Profile profile : c.input().profiles().values()) {
            for (String action : c.shell().keyConfig().actionOrder()) {
                assertThat(wulf.ui.KeyConfigScreen.keysFor(profile, action)).as(action).isNotEmpty();
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
                s.hiScores(), s.keyConfig(), s.sound(), s.stats(), s.amuletReveal(), s.map(), s.quickSave(), s.credits(), s.newGamePlus(), s.difficulty());
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
    void theCreditsNameWhoMadeTheGame() {
        assertThat(c.shell().credits().lines()).anyMatch(l -> l.contains("FERNANDO BOAGLIO"));
        assertThat(c.shell().credits().rollRows()).containsSubsequence(c.shell().credits().heading(),
                c.shell().credits().lines().get(0));
    }
}
