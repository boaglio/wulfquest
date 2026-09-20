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
        return new ShellConfig(s.schemaVersion(), s.screenHoldTicks(), title, s.attract(), s.gameOver(), s.tally(),
                s.hiScores(), s.keyConfig(), s.sound(), s.stats(), s.credits());
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
                s.gameOver(), s.tally(),
                new ShellConfig.HiScores(h.heading(), h.entryHeading(), h.entryPrompt(), h.size(), h.nameLength(),
                        "AABC", h.cursorBlinkTicks(), h.repeatDelayTicks(), h.repeatEveryTicks()),
                s.keyConfig(), s.sound(), s.stats(), s.credits());
        DataException e = error(broken);
        assertThat(e).isNotNull();
        assertThat(e.pointer()).isEqualTo("/hiScores/alphabet");
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
}
