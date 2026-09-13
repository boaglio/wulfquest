package wulf.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import wulf.data.DisplayConfig;
import wulf.data.JsonDb;
import wulf.data.Palette;
import wulf.data.PlayerData;
import wulf.data.SpriteData;
import wulf.data.SpriteRepository;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.Simulation;
import wulf.world.CollisionWorld;
import wulf.world.WorldGrid;

/** AGENTS.md §11.4–§11.7: which frame Ranger Vale shows, and that he stays in the playfield. */
class PlayerPainterTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final PlayerData RULES = DB.load("entities/player", PlayerData.class);
    private static final CollisionWorld OPEN =
            (gx, gy) -> gx < 0 || gy < 0 || gx >= WorldGrid.COLS || gy >= WorldGrid.ROWS;

    private static Simulation sim() {
        return Simulation.at(RULES, OPEN, 0, Fixed.fp(1000), Fixed.fp(1000));
    }

    private static String frame(Simulation s) {
        return PlayerPainter.frameFor(s.player(), RULES);
    }

    private static void run(Simulation s, InputState in, int n) {
        for (int i = 0; i < n; i++) {
            s.tick(in);
        }
    }

    @Test
    void standingStillFacesTheViewer() {
        assertThat(frame(sim())).isEqualTo("down_walk0");
    }

    @Test
    void walkingEastCyclesTheSideGaitEveryFiveTicks() {
        Simulation s = sim();
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            s.tick(InputState.of(1, 0, false, false));
            seen.add(frame(s));
        }
        assertThat(seen.subList(0, 4)).containsOnly("side_walk0");
        assertThat(seen.get(4)).isEqualTo("side_walk1");
        assertThat(seen.get(9)).isEqualTo("side_walk2");
        assertThat(seen.get(14)).isEqualTo("side_walk3");
        assertThat(seen.get(19)).isEqualTo("side_walk0");
    }

    @Test
    void leftUsesTheMirroredSideFramesAndDiagonalsUseTheSideView() {
        Simulation s = sim();
        s.tick(InputState.of(-1, 0, false, false));
        assertThat(frame(s)).isEqualTo("side_walk0_l");
        s.tick(InputState.of(1, -1, false, false));
        assertThat(frame(s)).isEqualTo("side_walk0");
        s.tick(InputState.of(-1, 1, false, false));
        assertThat(frame(s)).isEqualTo("side_walk0_l");
    }

    @Test
    void upAndDownHaveTheirOwnViews() {
        Simulation s = sim();
        s.tick(InputState.of(0, -1, false, false));
        assertThat(frame(s)).isEqualTo("up_walk0");
        s.tick(InputState.of(0, 1, false, false));
        assertThat(frame(s)).isEqualTo("down_walk0");
    }

    @Test
    void stoppingSnapsToFrameZeroAndKeepsTheFacing() {
        Simulation s = sim();
        run(s, InputState.of(1, 0, false, false), 7);
        assertThat(frame(s)).isEqualTo("side_walk1");
        s.tick(InputState.NONE);
        assertThat(frame(s)).isEqualTo("side_walk0");
    }

    @Test
    void aSwingShowsWindupStrikeAndRecover() {
        Simulation s = sim();
        s.tick(InputState.of(1, 0, false, false));
        s.tick(InputState.of(0, 0, true, true));
        assertThat(frame(s)).isEqualTo("side_swing0");
        run(s, InputState.NONE, 4);
        assertThat(frame(s)).isEqualTo("side_swing1");
        run(s, InputState.NONE, 4);
        assertThat(frame(s)).isEqualTo("side_swing2");
    }

    @Test
    void dyingPlaysTheDieFramesThenHoldsTheLast() {
        Simulation s = sim();
        s.kill();
        assertThat(frame(s)).isEqualTo("die0");
        run(s, InputState.NONE, 10);
        assertThat(frame(s)).isEqualTo("die1");
        run(s, InputState.NONE, 30);
        assertThat(frame(s)).isEqualTo("die3");
    }

    @Test
    void spawnInvulnerabilityBlinksTwoOnTwoOff() {
        Simulation s = sim();
        s.kill();
        run(s, InputState.NONE, RULES.death().animTicks() + RULES.death().freezeTicks());
        StringBuilder pattern = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            pattern.append(PlayerPainter.visible(s.player(), RULES) ? '#' : '.');
            s.tick(InputState.NONE);
        }
        assertThat(pattern.toString()).isEqualTo("#..##..#");
    }

    @Test
    void everyFrameThePainterCanChooseExistsInTheSprite() {
        SpriteData sprite = new SpriteRepository(DB).get(RULES.sprite());
        Set<String> available = sprite.decode("player.sprite.json").keySet();
        Set<String> chosen = new LinkedHashSet<>();
        for (String view : List.of("side", "up", "down")) {
            for (String kind : List.of("walk_", "swing_")) {
                for (String f : RULES.animation(kind + view).frames()) {
                    chosen.add(f);
                    if (view.equals("side")) {
                        chosen.add(f + "_l");
                    }
                }
            }
        }
        chosen.addAll(RULES.animation("die").frames());
        assertThat(available).containsAll(chosen);
    }

    @Test
    void paintingNeverSpillsOutsideThePlayfield() {
        DisplayConfig display = DB.load("config/display", DisplayConfig.class);
        Palette palette = DB.load("art/palette", Palette.class);
        PlayerPainter painter = new PlayerPainter(display, new SpriteBank(new SpriteRepository(DB)), RULES, palette);
        DisplayConfig.Playfield f = display.playfield();

        // Feet 2 px inside the west edge of room 8,10, facing west and mid-swing: sprite and blade both overhang.
        Simulation s = Simulation.at(RULES, OPEN, 0, Fixed.fp(8 * 256 + 6), Fixed.fp(10 * 192 + 100));
        s.tick(InputState.of(-1, 0, false, false));
        s.tick(InputState.of(0, 0, true, true));
        run(s, InputState.NONE, 4);
        assertThat(s.room().col()).isEqualTo(8);
        assertThat(s.sabre().isEmpty()).isFalse();

        Framebuffer fb = new Framebuffer(display.canvas().w(), display.canvas().h());
        fb.clear(3);
        painter.paint(fb, s);
        int inside = 0;
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                boolean inField = x >= f.x() && y >= f.y() && x < f.x() + f.w() && y < f.y() + f.h();
                if (!inField) {
                    assertThat(fb.get(x, y)).as("pixel %d,%d outside the playfield", x, y).isEqualTo(3);
                } else if (fb.get(x, y) != 3) {
                    inside++;
                }
            }
        }
        assertThat(inside).as("the part inside the playfield was drawn").isPositive();
    }
}
