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

/** AGENTS.md §11.4–§11.7: which frame Ranger Vale shows, where the blade is drawn, and that he stays in the playfield. */
class PlayerPainterTest {

    private static final JsonDb DB = new JsonDb(Path.of("data"));
    private static final PlayerData RULES = DB.load("entities/player", PlayerData.class);
    private static final CollisionWorld OPEN =
            (gx, gy) -> gx < 0 || gy < 0 || gx >= WorldGrid.COLS || gy >= WorldGrid.ROWS;
    private static final InputState RIGHT = InputState.of(1, 0, false, false);
    private static final InputState FIRE = InputState.of(0, 0, true, true);

    private static Simulation sim() {
        return Simulation.at(RULES, OPEN, 0, Fixed.fp(3 * 256 + 128), Fixed.fp(5 * Simulation.ROOM_H_PX + 100));
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
            s.tick(RIGHT);
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
        run(s, RIGHT, 7);
        assertThat(frame(s)).isEqualTo("side_walk1");
        s.tick(InputState.NONE);
        assertThat(frame(s)).isEqualTo("side_walk0");
    }

    @Test
    void eachStrokeIsOnGuardThenLunges() {
        // §11.6: fencing, the whole body — on guard for one window, the lunge for the next —
        // and the blade live throughout, as the original's is while fire is held.
        Simulation s = sim();
        s.tick(RIGHT);
        s.tick(FIRE);
        PlayerData.Sabre sabre = RULES.sabre();
        int window = sabre.fence().everyTicks();
        for (int t = 0; t < sabre.totalTicks(); t++) {
            assertThat(s.player().swingTick()).isEqualTo(t);
            assertThat(frame(s)).as("stroke tick %d", t).isEqualTo(t < window ? "side_swing0" : "side_swing1");
            assertThat(s.sabre().isEmpty()).as("blade live at stroke tick %d", t).isFalse();
            s.tick(InputState.NONE);
        }
    }

    @Test
    void swingingOnTheMoveKeepsWalking() {
        // The M3 bug: fixed-leg swing frames made a moving swing glide.
        Simulation s = sim();
        run(s, RIGHT, 6);
        s.tick(InputState.of(1, 0, true, true));
        List<String> seen = new ArrayList<>();
        for (int i = 0; i < RULES.sabre().totalTicks() - 1; i++) {
            seen.add(frame(s));
            s.tick(RIGHT);
        }
        assertThat(seen).allMatch(f -> f.matches("side_swing[012]_walk[0-3]"));
        Set<Character> gaits = new LinkedHashSet<>();
        seen.forEach(f -> gaits.add(f.charAt(f.length() - 1)));
        assertThat(gaits).as("the legs keep stepping through the swing").hasSizeGreaterThan(1);
    }

    @Test
    void stoppingMidSwingDropsToTheStandingPose() {
        Simulation s = sim();
        run(s, RIGHT, 6);
        s.tick(InputState.of(1, 0, true, true));
        assertThat(frame(s)).endsWith("_walk1");
        s.tick(InputState.NONE);
        // No windup in the original's fighting (§11.6): standing still, it is the fencing pose at once.
        assertThat(frame(s)).isIn("side_swing0", "side_swing1");
    }

    @Test
    void theBladeIsDrawnFromTheHandNotFromTheFeet() {
        DisplayConfig display = DB.load("config/display", DisplayConfig.class);
        Palette palette = DB.load("art/palette", Palette.class);
        SpriteBank bank = new SpriteBank(new SpriteRepository(DB));
        PlayerPainter painter = new PlayerPainter(display, bank, RULES, palette);
        DisplayConfig.Playfield f = display.playfield();

        Simulation s = sim();
        s.tick(RIGHT);
        s.tick(FIRE);
        run(s, InputState.NONE, RULES.sabre().windupTicks());
        assertThat(s.sabre().isEmpty()).isFalse();
        String strike = frame(s);
        assertThat(strike).isIn("side_swing0", "side_swing1");

        Framebuffer fb = new Framebuffer(display.canvas().w(), display.canvas().h());
        fb.clear(0);
        painter.paint(fb, s);

        int feetX = Fixed.px(s.player().xFp()) + f.x() - 3 * 256;
        int feetY = Fixed.px(s.player().yFp()) + f.y() - 5 * Simulation.ROOM_H_PX;
        Sprite vale = bank.get("player");
        SpriteData.Point hand = vale.anchor(strike, "hand");
        int handX = feetX - vale.originX() + hand.x();
        int handY = feetY - vale.originY() + hand.y();
        // The sprite's own white is drawn too; the blade is what lies beyond it.
        Framebuffer bare = new Framebuffer(display.canvas().w(), display.canvas().h());
        bare.clear(0);
        vale.blit(bare, strike, feetX, feetY);
        int white = palette.indexOf("brightWhite");
        int reach = RULES.sabre().reachPx();
        int nearest = Integer.MAX_VALUE;
        int farthest = 0;
        for (int y = 0; y < fb.height(); y++) {
            for (int x = 0; x < fb.width(); x++) {
                if (fb.get(x, y) == white && bare.get(x, y) != white) {
                    int dist = Math.max(Math.abs(x - handX), Math.abs(y - handY));
                    nearest = Math.min(nearest, dist);
                    farthest = Math.max(farthest, dist);
                }
            }
        }
        assertThat(farthest).as("a blade was drawn").isPositive();
        assertThat(nearest).as("it starts at the hand").isLessThanOrEqualTo(2);
        assertThat(farthest).as("and reaches no further than the sabre does").isLessThanOrEqualTo(reach + 1);
        assertThat(handY).as("at hand height, well clear of the feet").isLessThan(feetY - 8);
    }

    /**
     * §11.6: the blade fences — drawn back to a guard, out in a thrust, back — every window a
     * new pose, each picked from its own list, as the original picks its fighting poses.
     */
    @Test
    void theBladeFencesInAndOutThroughEveryPose() {
        PlayerData.Fence fence = RULES.sabre().fence();
        java.util.Set<List<Integer>> seen = new java.util.HashSet<>();
        List<Integer> last = null;
        for (long t = 0; t < 400; t++) {
            int intoBlade = (int) (t % (2 * fence.everyTicks()));
            List<Integer> pose = PlayerPainter.pose(fence, intoBlade, t);
            if (t % fence.everyTicks() != 0) {
                assertThat(pose).as("tick %d holds its window's pose", t).isEqualTo(last);
            }
            boolean thrust = intoBlade >= fence.everyTicks();
            assertThat(thrust ? fence.thrusts() : fence.guards()).as("tick %d", t).contains(pose);
            seen.add(pose);
            last = pose;
        }
        assertThat(seen).as("every pose turns up").hasSize(fence.guards().size() + fence.thrusts().size());
        int longestGuard = fence.guards().stream().mapToInt(g -> g.get(0)).max().orElseThrow();
        int shortestThrust = fence.thrusts().stream().mapToInt(g -> g.get(0)).min().orElseThrow();
        assertThat(longestGuard).as("a guard is drawn back, a thrust is out").isLessThan(shortestThrust);
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
            int gaits = RULES.animation("walk_" + view).frames().size();
            List<String> names = new ArrayList<>(RULES.animation("walk_" + view).frames());
            for (String swing : RULES.animation("swing_" + view).frames()) {
                names.add(swing);
                for (int g = 0; g < gaits; g++) {
                    names.add(swing + "_walk" + g);
                }
            }
            for (String n : names) {
                chosen.add(n);
                if (view.equals("side")) {
                    chosen.add(n + "_l");
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

        // Feet just inside the west edge of room 8,10, facing west and mid-strike: sprite and blade both overhang.
        Simulation s = Simulation.at(RULES, OPEN, 0, Fixed.fp(8 * 256 + 6), Fixed.fp(10 * Simulation.ROOM_H_PX + 100));
        s.tick(InputState.of(-1, 0, false, false));
        s.tick(FIRE);
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
