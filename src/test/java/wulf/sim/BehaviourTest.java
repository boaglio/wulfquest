package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;
import static wulf.sim.EcoFixtures.CREATURES;
import static wulf.sim.EcoFixtures.FENCED;
import static wulf.sim.EcoFixtures.OPEN;
import static wulf.sim.EcoFixtures.ROOM_X;
import static wulf.sim.EcoFixtures.ROOM_Y;
import static wulf.sim.EcoFixtures.STILL;
import static wulf.sim.EcoFixtures.Spot;
import static wulf.sim.EcoFixtures.lx;
import static wulf.sim.EcoFixtures.ly;
import static wulf.sim.EcoFixtures.only;
import static wulf.sim.EcoFixtures.run;
import static wulf.sim.EcoFixtures.tuned;
import static wulf.sim.EcoFixtures.walls;
import static wulf.sim.EcoFixtures.with;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import wulf.data.CreatureData;
import wulf.engine.Fixed;
import wulf.input.InputState;
import wulf.sim.ai.AmbushBurst;
import wulf.world.CollisionWorld;

/** AGENTS.md §12.5 — each behaviour's defining property, on hand-placed creatures. */
class BehaviourTest {

    private static void assertInsideTheRoom(Creature c) {
        CreatureData.Box b = c.species().collisionBox();
        assertThat(lx(c) + b.x()).as("%s left", c.species().id()).isGreaterThanOrEqualTo(0);
        assertThat(lx(c) + b.x() + b.w() - 1).as("%s right", c.species().id()).isLessThanOrEqualTo(255);
        assertThat(ly(c) + b.y()).as("%s top", c.species().id()).isGreaterThanOrEqualTo(0);
        assertThat(ly(c) + b.y() + b.h() - 1).as("%s bottom", c.species().id()).isLessThanOrEqualTo(191);
    }

    // ------------------------------------------------------------------ LINEAR_BOUNCE

    @Test
    void linearBounceReflectsOffTheRoomEdge() {
        Simulation s = with(OPEN, 40, 170, tuned("tribesman", Map.of("reverseChancePer10k", 0)),
                new Spot("tribesman", 200, 60));
        Creature c = only(s);
        c.direction(1, 0);
        for (int guard = 0; guard < 100 && c.dirX() == 1; guard++) {
            s.tick(InputState.NONE);
        }
        assertThat(c.dirX()).isEqualTo(-1);
        assertInsideTheRoom(c);
    }

    @Test
    void linearBounceNeverLeavesTheRoom() {
        Simulation s = with(FENCED, 20, 180, CREATURES, new Spot("tribesman", 128, 60), new Spot("hippo", 60, 60));
        for (int t = 0; t < 3000; t++) {
            s.tick(InputState.NONE);
            s.creatures().forEach(BehaviourTest::assertInsideTheRoom);
        }
    }

    // ------------------------------------------------------------------ CHASE_AXIS

    @Test
    void chaseAxisRunsTheMajorAxisAtFullSpeedAndTheMinorAtHalf() {
        Simulation s = with(OPEN, 200, 120, CREATURES, new Spot("chief", 30, 150));
        Creature c = only(s);
        int x = c.xFp();
        int y = c.yFp();
        s.tick(InputState.NONE);
        assertThat(c.xFp() - x).isEqualTo(c.species().speed().xFp());
        assertThat(y - c.yFp()).isEqualTo(Fixed.mul(c.species().speed().yFp(), 128));
    }

    // ------------------------------------------------------------------ CHASE_DIRECT

    @Test
    void chaseDirectTurnsOneStepAtATimeWithACooldown() {
        Simulation s = with(OPEN, 60, 180, CREATURES, new Spot("rhino", 60, 40));
        Creature c = only(s);
        c.direction(1, 0);
        c.timer(0);
        Direction8 previous = Direction8.E;
        List<Integer> turnTicks = new ArrayList<>();
        for (int t = 1; t <= 40; t++) {
            s.tick(InputState.NONE);
            Direction8 now = Direction8.of(c.dirX(), c.dirY());
            if (now != previous) {
                assertThat(Math.floorMod(now.ordinal() - previous.ordinal(), 8)).as("one 45 degree step").isIn(1, 7);
                turnTicks.add(t);
                previous = now;
            }
        }
        assertThat(previous).as("ends heading for the player below").isEqualTo(Direction8.S);
        assertThat(turnTicks).hasSize(2);
        assertThat(turnTicks.get(1) - turnTicks.get(0)).isGreaterThanOrEqualTo(c.species().behaviour().param("turnCooldownTicks"));
    }

    @Test
    void chaseDirectStallsWhenEveryWayIsBlocked() {
        // Sliding along a wall counts as moving, so "boxed in" needs a box that fills its pocket
        // exactly: a 16x8 rhino in a two-cell pocket cannot move a pixel in any direction.
        CreatureData.Species r = CREATURES.species("rhino");
        List<CreatureData.Species> roster = new ArrayList<>(CREATURES.creatures());
        roster.set(CREATURES.indexOf("rhino"), new CreatureData.Species(r.id(), r.displayName(), r.sprite(), r.size(),
                new CreatureData.Box(-8, -8, 16, 8), r.speed(), r.hp(), r.score(), r.ignoresScenery(), r.behaviour()));
        CreatureData snug = new CreatureData(CREATURES.schemaVersion(), CREATURES.fidelity(),
                CREATURES.diagonalScaleFp(), CREATURES.puffTicks(), CREATURES.hurtFlashTicks(), CREATURES.walkTicksPerFrame(), roster,
                CREATURES.projectiles());
        CollisionWorld pocket = (gx, gy) -> {
            if (OPEN.isSolid(gx, gy)) {
                return true;
            }
            int cx = gx - EcoFixtures.CELL_X;
            int cy = gy - EcoFixtures.CELL_Y;
            boolean inRoom = cx >= 0 && cx < 32 && cy >= 0 && cy < 24;
            boolean rhinoPocket = cx >= 6 && cx <= 7 && cy == 5;
            boolean playerPen = cx >= 2 && cx <= 6 && cy >= 20 && cy <= 23;
            return inRoom && !rhinoPocket && !playerPen;
        };
        Simulation s = with(pocket, 32, 180, snug, new Spot("rhino", 56, 48));
        Creature c = only(s);
        int stall = c.species().behaviour().param("stallTicks");
        // A sub-pixel mover may take one last legitimate fraction of a pixel before it is truly stuck.
        boolean stalled = false;
        for (int t = 0; t < 5 && !stalled; t++) {
            s.tick(InputState.NONE);
            stalled = c.aux() == stall;
        }
        assertThat(stalled).as("every way blocked: it stalls within a few ticks").isTrue();
        int x = c.xFp();
        int y = c.yFp();
        run(s, stall - 1);
        assertThat(c.xFp()).isEqualTo(x);
        assertThat(c.yFp()).isEqualTo(y);
        assertThat(c.aux()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ WANDER_ERRATIC

    @Test
    void wanderErraticChangesDirectionConstantlyAndStaysInTheRoom() {
        Simulation s = with(FENCED, 20, 180, CREATURES, new Spot("scorpion", 128, 100));
        Creature c = only(s);
        int changes = 0;
        int lastX = c.dirX();
        int lastY = c.dirY();
        for (int t = 0; t < 1000; t++) {
            s.tick(InputState.NONE);
            assertInsideTheRoom(c);
            if (c.dirX() != lastX || c.dirY() != lastY) {
                changes++;
                lastX = c.dirX();
                lastY = c.dirY();
            }
        }
        assertThat(changes).isGreaterThan(60);
    }

    // ------------------------------------------------------------------ WALL_FOLLOW

    @Test
    void wallFollowRunsAlongAWall() {
        Simulation s = with(EcoFixtures.either(walls(new int[] {16, 2, 16, 20}), FENCED), 20, 180, CREATURES,
                new Spot("snake", 119, 140));
        Creature c = only(s);
        c.direction(0, -1);
        c.aux(1);   // the wall is on its clockwise side: to the east while heading north
        c.timer(0);
        run(s, 40);
        assertThat(lx(c)).as("hugging the wall's west face").isEqualTo(119);
        assertThat(140 - ly(c)).as("travelling along it").isGreaterThanOrEqualTo(20);
    }

    @Test
    void wallFollowWrapsRoundACorner() {
        Simulation s = with(EcoFixtures.either(walls(new int[] {16, 2, 16, 20}), FENCED), 20, 180, CREATURES,
                new Spot("snake", 119, 140));
        Creature c = only(s);
        c.direction(0, -1);
        c.aux(1);
        c.timer(0);
        boolean turnedOverTheTop = false;
        boolean reachedTheFarSide = false;
        for (int t = 0; t < 600 && !reachedTheFarSide; t++) {
            s.tick(InputState.NONE);
            if (c.dirX() == 1 && ly(c) <= 16) {
                turnedOverTheTop = true;
            }
            if (turnedOverTheTop && lx(c) + c.species().collisionBox().x() >= 136) {
                reachedTheFarSide = true;
            }
        }
        assertThat(turnedOverTheTop).as("turned east over the top of the wall").isTrue();
        assertThat(reachedTheFarSide).as("came round to the wall's east side").isTrue();
    }

    // ------------------------------------------------------------------ DROP_THREAD

    @Test
    void dropThreadDropsPausesAndClimbsBack() {
        Simulation s = with(OPEN, 104, 186, CREATURES, new Spot("spider", 100, 13));
        Creature c = only(s);
        int anchor = ly(c);
        List<Integer> ys = new ArrayList<>();
        for (int t = 0; t < 300; t++) {
            s.tick(InputState.NONE);
            ys.add(ly(c));
        }
        int deepest = ys.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertThat(deepest - anchor).isBetween(119, 121);
        int held = 0;
        int longestHold = 0;
        for (int y : ys) {
            held = y == deepest ? held + 1 : 0;
            longestHold = Math.max(longestHold, held);
        }
        assertThat(longestHold).as("the pause at the bottom").isGreaterThanOrEqualTo(c.species().behaviour().param("pauseTicks"));
        int deepestAt = ys.indexOf(deepest);
        assertThat(ys.subList(deepestAt, ys.size())).as("climbs back to its anchor").contains(anchor);
    }

    @Test
    void dropThreadIgnoresAPlayerWhoIsNotBeneath() {
        Simulation s = with(OPEN, 200, 186, CREATURES, new Spot("spider", 100, 13));
        Creature c = only(s);
        int y = c.yFp();
        run(s, 50);
        assertThat(c.yFp()).isEqualTo(y);
    }

    // ------------------------------------------------------------------ SINE_FLIGHT

    @Test
    void sineFlightWavesAcrossTheRoomThroughScenery() {
        // The room is solid except the player's pen; the bat ignores scenery.
        CollisionWorld solidRoom = (gx, gy) -> {
            if (OPEN.isSolid(gx, gy)) {
                return true;
            }
            int cx = gx - EcoFixtures.CELL_X;
            int cy = gy - EcoFixtures.CELL_Y;
            boolean inRoom = cx >= 0 && cx < 32 && cy >= 0 && cy < 24;
            return inRoom && !(cx <= 5 && cy >= 19);
        };
        Simulation s = with(solidRoom, 20, 180, CREATURES, new Spot("bat", 128, 90));
        Creature c = only(s);
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int flyingTicks = 0;
        for (int t = 0; t < 140; t++) {
            int x = c.xFp();
            s.tick(InputState.NONE);
            minY = Math.min(minY, ly(c));
            maxY = Math.max(maxY, ly(c));
            if (c.xFp() != x) {
                flyingTicks++;
            }
        }
        assertThat(maxY - minY).as("a wave of about twice the amplitude").isBetween(40, 58);
        // Bouncing off the room's edges revisits x positions, so count ticks in flight instead.
        assertThat(flyingTicks).as("flying on every tick, straight through solid scenery").isGreaterThanOrEqualTo(135);
    }

    // ------------------------------------------------------------------ HOP

    @Test
    void hopMovesInBurstsOfEightAfterRestsOfTwenty() {
        CreatureData slowFrog = tuned("frog", Map.of("towardPlayerPer10k", 0), new CreatureData.Speed(128, 128));
        Simulation s = with(FENCED, 20, 180, slowFrog, new Spot("frog", 128, 100));
        Creature c = only(s);
        List<Boolean> moved = new ArrayList<>();
        for (int t = 0; t < 300; t++) {
            int x = c.xFp();
            int y = c.yFp();
            s.tick(InputState.NONE);
            moved.add(c.xFp() != x || c.yFp() != y);
        }
        List<Integer> hops = new ArrayList<>();
        List<Integer> rests = new ArrayList<>();
        int run = 1;
        for (int i = 1; i <= moved.size(); i++) {
            if (i < moved.size() && moved.get(i).equals(moved.get(i - 1))) {
                run++;
                continue;
            }
            (moved.get(i - 1) ? hops : rests).add(run);
            run = 1;
        }
        // The first and last runs are cut off by the sampling window.
        assertThat(hops.subList(1, hops.size() - 1)).containsOnly(8);
        assertThat(rests.subList(1, rests.size() - 1)).containsOnly(20);
    }

    // ------------------------------------------------------------------ AMBUSH_BURST

    @Test
    void ambushBurstChargesWhenThePlayerComesClose() {
        Simulation s = with(OPEN, 130, 100, CREATURES, new Spot("boar", 60, 100));
        Creature c = only(s);
        s.tick(InputState.NONE);
        assertThat(c.phase()).isEqualTo(AmbushBurst.CHARGE);
        int x = c.xFp();
        s.tick(InputState.NONE);
        assertThat(c.xFp() - x).isEqualTo(c.species().behaviour().param("chargeSpeedFp"));
        assertThat(c.dirX()).isEqualTo(1);
        assertThat(c.dirY()).isZero();
    }

    @Test
    void ambushBurstWaitsWhileAWallBlocksTheLine() {
        Simulation s = with(walls(new int[] {12, 0, 12, 23}), 130, 100, CREATURES, new Spot("boar", 60, 100));
        Creature c = only(s);
        int x = c.xFp();
        run(s, 50);
        assertThat(c.phase()).isEqualTo(AmbushBurst.WAIT);
        assertThat(c.xFp()).isEqualTo(x);
    }

    @Test
    void ambushBurstIsStunnedWhenItHitsSomething() {
        Simulation s = with(OPEN, 220, 100, CREATURES, new Spot("boar", 150, 100));
        Creature c = only(s);
        s.tick(InputState.NONE);
        assertThat(c.phase()).isEqualTo(AmbushBurst.CHARGE);
        s.kill();   // the player goes down; the jungle keeps moving (§11.7), so the charge runs on into the edge
        boolean stunned = false;
        for (int t = 0; t < 50 && !stunned; t++) {
            s.tick(InputState.NONE);
            stunned = c.phase() == AmbushBurst.STUN;
        }
        assertThat(stunned).isTrue();
        CreatureData.Box b = c.species().collisionBox();
        assertThat(lx(c) + b.x() + b.w() - 1).as("stopped at the room's east edge").isEqualTo(255);
        int x = c.xFp();
        run(s, 10);
        assertThat(c.xFp()).isEqualTo(x);
    }

    // ------------------------------------------------------------------ HERD_BOUNCE

    @Test
    void aHerdReflectsAsOne() {
        CreatureData calm = tuned("wildebeest", Map.of("reverseChancePer10k", 0));
        Simulation s = with(FENCED, 20, 180, calm,
                new Spot("wildebeest", 200, 60), new Spot("wildebeest", 180, 90), new Spot("wildebeest", 220, 90));
        List<Creature> herd = new ArrayList<>(s.creatures());
        assertThat(herd).hasSize(3);
        Herd shared = herd.get(0).herd();
        assertThat(herd).allMatch(c -> c.herd() == shared);
        shared.direction(1, 0);
        for (int guard = 0; guard < 60 && shared.dirX() == 1; guard++) {
            s.tick(InputState.NONE);
        }
        assertThat(shared.dirX()).isEqualTo(-1);
        s.tick(InputState.NONE);
        assertThat(herd).allMatch(c -> c.dirX() == -1);
    }

    // ------------------------------------------------------------------ PATROL_THROW

    @Test
    void aSpearmanThrowsAtAPlayerAhead() {
        CreatureData quick = tuned("spearman", Map.of("throwPeriodTicks", 5, "reverseChancePer10k", 0), STILL);
        Simulation s = with(OPEN, 140, 100, quick, new Spot("spearman", 60, 100));
        Creature c = only(s);
        c.direction(1, 0);
        c.timer(0);
        run(s, 5);
        assertThat(s.spears()).hasSize(1);
        assertThat(s.spears().get(0).dirX()).isEqualTo(1);
    }

    @Test
    void aSpearmanNeverThrowsAtAPlayerBehindHim() {
        CreatureData quick = tuned("spearman", Map.of("throwPeriodTicks", 5, "reverseChancePer10k", 0), STILL);
        Simulation s = with(OPEN, 20, 100, quick, new Spot("spearman", 60, 100));
        Creature c = only(s);
        c.direction(1, 0);
        c.timer(0);
        run(s, 20);
        assertThat(s.spears()).isEmpty();
    }

    @Test
    void creaturesStartMovingOnlyAfterTheirRoomIsCurrent() {
        // Sanity for the fixtures: hand-placed creatures appear in the test room at the given spot.
        Simulation s = with(OPEN, 20, 180, CREATURES, new Spot("tribesman", 128, 60));
        Creature c = only(s);
        assertThat(Fixed.px(c.xFp())).isEqualTo(ROOM_X + 128);
        assertThat(Fixed.px(c.yFp())).isEqualTo(ROOM_Y + 60);
    }
}
