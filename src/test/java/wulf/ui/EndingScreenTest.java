package wulf.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.ShellConfig;
import wulf.render.Fonts;
import wulf.render.Framebuffer;
import wulf.render.SpriteBank;

/** AGENTS.md §14.7 — the closing scene: Vale walks the amulet to Mr Jones, they speak, the prompt comes last. */
class EndingScreenTest {

    private static final Content C = Content.load(Path.of("data"));
    private static final ShellConfig.Ending E = C.shell().ending();
    private static final SpriteBank SPRITES = new SpriteBank(C.sprites());
    private static final Chrome CHROME = new Chrome(new Fonts("font", C.font()), C.palette());
    private static final EndingScreen SCREEN = new EndingScreen(E, CHROME.big(), C.palette(), SPRITES,
            SPRITES.get(C.landmarks().amulet().sprite()), wulf.render.QuestPainter.framesBySlot(C.landmarks()));

    private static Framebuffer at(long t) {
        Framebuffer fb = new Framebuffer(C.display().canvas().w(), C.display().canvas().h());
        SCREEN.paint(fb, t);
        return fb;
    }

    /** Whether anything is drawn in the band of rows from {@code y}, {@code h} tall. */
    private static boolean anyIn(Framebuffer fb, int y, int h) {
        int ground = C.palette().indexOf("black");
        for (int v = y; v < y + h; v++) {
            for (int u = 0; u < fb.width(); u++) {
                if (fb.get(u, v) != ground) {
                    return true;
                }
            }
        }
        return false;
    }

    @Test
    void valeWalksInAndStopsBeforeMrJones() {
        assertThat(SCREEN.valeX(0)).isEqualTo(E.vale().fromX());
        assertThat(SCREEN.valeX(E.vale().walkTicks() / 2)).isBetween(E.vale().fromX(), E.vale().toX());
        assertThat(SCREEN.valeX(E.vale().walkTicks())).isEqualTo(E.vale().toX());
        assertThat(E.vale().toX()).as("he stops short of Mr Jones").isLessThan(E.jones().x());
        assertThat(E.amulet().handOverAt()).as("he has arrived before he hands it over").isGreaterThanOrEqualTo(E.vale().walkTicks());
    }

    @Test
    void theAmuletChangesHandsAndMrJonesHoldsItAfter() {
        assertThat(SCREEN.handedOver(E.amulet().handOverAt())).isFalse();
        assertThat(SCREEN.handedOver(E.amulet().handOverAt() + E.amulet().handOverTicks())).isTrue();
        Framebuffer before = at(E.amulet().handOverAt() - 1);
        Framebuffer after = at(E.lastTick());
        assertThat(after.hash()).isNotEqualTo(before.hash());
    }

    @Test
    void eachLineTypesOutFromItsOwnTickAndThePromptComesLast() {
        ShellConfig.Line vale = E.lines().get(0);
        assertThat(anyIn(at(vale.at() - 1), vale.y(), 8)).as("nothing said yet").isFalse();
        assertThat(anyIn(at(vale.at() + E.ticksPerChar() * 3L), vale.y(), 8)).as("the first words").isTrue();
        int promptY = C.display().canvas().h() - 24;
        assertThat(anyIn(at(E.promptAt() - 1), promptY, 8)).isFalse();
        assertThat(anyIn(at(E.promptAt()), promptY, 8)).isTrue();
        // Each speaks in turn: Mr Jones answers after Vale has finished.
        ShellConfig.Line jones = E.lines().get(1);
        assertThat(jones.at()).isGreaterThanOrEqualTo(vale.at() + vale.length() * E.ticksPerChar());
        assertThat(E.tuneAt()).as("the tune after the words").isGreaterThanOrEqualTo(jones.at() + jones.length() * E.ticksPerChar());
    }
}
