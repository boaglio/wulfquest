package wulf.sim;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import wulf.Content;
import wulf.data.CreatureData;
import wulf.data.RoomEntitiesData;
import wulf.engine.Fixed;
import wulf.world.BiomeResolver;
import wulf.world.RoomAddress;
import wulf.world.WorldGrid;

/** AGENTS.md §12.2, §12.6, §7.6 — rooms fill with the right creatures, fairly, and replayably. */
class PopulationTest {

    private static final List<String> BIOMES = List.of(BiomeResolver.JUNGLE, BiomeResolver.SWAMP,
            BiomeResolver.MOUNTAIN, BiomeResolver.BONEFIELDS, BiomeResolver.HUT, BiomeResolver.WATER);

    private static Content content;
    private static WorldGrid grid;
    private static Ecosystem eco;

    @BeforeAll
    static void load() {
        content = Content.load(Path.of("data"));
        grid = new WorldGrid(content.rooms());
        eco = content.ecosystem();
    }

    private static RoomAddress interiorRoomIn(String biome) {
        for (int row = 1; row < 15; row++) {
            for (int col = 1; col < 15; col++) {
                RoomAddress r = new RoomAddress(col, row);
                if (!r.equals(content.map().startRoom()) && content.biomes().biomeOf(r).equals(biome)) {
                    return r;
                }
            }
        }
        throw new AssertionError("no interior room in biome " + biome);
    }

    private static Simulation sim(RoomAddress room, long seed) {
        return Simulation.startingIn(content.player(), grid, 6, room, eco, seed);
    }

    /** Budget slots used: each lone creature, and each herd as one. */
    private static int slots(Simulation s) {
        Set<Herd> herds = Collections.newSetFromMap(new IdentityHashMap<>());
        int singles = 0;
        for (Creature c : s.creatures()) {
            if (c.herd() == Herd.NONE) {
                singles++;
            } else {
                herds.add(c.herd());
            }
        }
        return singles + herds.size();
    }

    private static List<String> describe(Simulation s) {
        List<String> out = new ArrayList<>();
        for (Creature c : s.creatures()) {
            out.add(c.species().id() + "@" + Fixed.px(c.xFp()) + "," + Fixed.px(c.yFp()));
        }
        return out;
    }

    @Test
    void theStartRoomIsAlwaysSafe() {
        for (long seed = 0; seed < 20; seed++) {
            assertThat(sim(content.map().startRoom(), seed).creatures()).isEmpty();
        }
    }

    @Test
    void roomsStayWithinTheirBiomesBudget() {
        for (String biome : BIOMES) {
            RoomEntitiesData.Biome rules = content.roomEntities().biomes().get(biome);
            RoomAddress room = interiorRoomIn(biome);
            int total = 0;
            for (long seed = 0; seed < 60; seed++) {
                int used = slots(sim(room, seed));
                assertThat(used).as("%s room %s seed %d", biome, room, seed)
                        .isLessThanOrEqualTo(rules.budget().max() + rules.extras().size());
                total += used;
            }
            assertThat(total / 60.0).as("%s average", biome).isGreaterThanOrEqualTo(rules.budget().min() * 0.75);
        }
    }

    /**
     * Every place, to the pixel, the player's feet can get to in a room without
     * leaving it — a deliberately plain flood, independent of {@link Reach}.
     */
    private static boolean[][] walkable(Simulation s, RoomAddress room) {
        wulf.data.PlayerData p = content.player();
        int x0 = room.col() * 32;
        int y0 = room.row() * 24;
        wulf.world.CollisionWorld walled = (gx, gy) ->
                gx < x0 || gy < y0 || gx >= x0 + 32 || gy >= y0 + 24 || grid.isSolid(gx, gy);
        int[] start = SpawnFinder.nearestFree(grid, p.collisionBox(), p.spawn().insideRoomPx(), room,
                s.player().entryXFp(), s.player().entryYFp());
        boolean[][] seen = new boolean[257][193];
        java.util.ArrayDeque<int[]> todo = new java.util.ArrayDeque<>();
        int sx = Fixed.px(start[0]) - room.col() * 256;
        int sy = Fixed.px(start[1]) - room.row() * 192;
        seen[sx][sy] = true;
        todo.add(new int[] {sx, sy});
        while (!todo.isEmpty()) {
            int[] at = todo.poll();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = at[0] + d[0];
                int y = at[1] + d[1];
                if (x >= 0 && y >= 0 && x <= 256 && y <= 192 && !seen[x][y]
                        && !Collision.boxBlocked(walled, p.collisionBox(),
                                Fixed.fp(room.col() * 256 + x), Fixed.fp(room.row() * 192 + y))) {
                    seen[x][y] = true;
                    todo.add(new int[] {x, y});
                }
            }
        }
        return seen;
    }

    @Test
    void groundCreaturesStartWhereThePlayerCanGetToThem() {
        List<RoomAddress> rooms = new ArrayList<>();
        rooms.add(new RoomAddress(7, 3));   // bonefields with an open strip above its top wall
        rooms.add(new RoomAddress(4, 1));   // a hut
        for (String biome : BIOMES) {
            rooms.add(interiorRoomIn(biome));
        }
        int checked = 0;
        for (RoomAddress room : rooms) {
            boolean[][] walk = walkable(sim(room, 0), room);
            for (long seed = 0; seed < 25; seed++) {
                Simulation s = sim(room, seed);
                for (Creature c : s.creatures()) {
                    if (c.species().ignoresScenery()) {
                        continue;
                    }
                    int lx = Fixed.px(c.xFp()) - room.col() * 256;
                    int ly = Fixed.px(c.yFp()) - room.row() * 192;
                    boolean near = false;
                    for (int y = Math.max(0, ly - Simulation.REACH_SLACK_PX); y <= Math.min(192, ly + Simulation.REACH_SLACK_PX) && !near; y++) {
                        for (int x = Math.max(0, lx - Simulation.REACH_SLACK_PX); x <= Math.min(256, lx + Simulation.REACH_SLACK_PX) && !near; x++) {
                            near = walk[x][y];
                        }
                    }
                    assertThat(near).as("%s at %d,%d in %s seed %d is out of the player's reach",
                            c.species().id(), lx, ly, room, seed).isTrue();
                    checked++;
                }
            }
        }
        assertThat(checked).isGreaterThan(100);
    }

    @Test
    void spawnsKeepTheirDistanceFitTheRoomAndMissTheScenery() {
        int minDistance = content.roomEntities().spawn().minDistanceFromEntryPx();
        for (String biome : BIOMES) {
            RoomAddress room = interiorRoomIn(biome);
            for (long seed = 0; seed < 30; seed++) {
                Simulation s = sim(room, seed);
                int ex = Fixed.px(s.player().entryXFp());
                int ey = Fixed.px(s.player().entryYFp());
                for (Creature c : s.creatures()) {
                    CreatureData.Box b = c.species().collisionBox();
                    int x = Fixed.px(c.xFp());
                    int y = Fixed.px(c.yFp());
                    String what = c.species().id() + " in " + room + " seed " + seed;
                    long dx = x - ex;
                    long dy = y - ey;
                    assertThat(dx * dx + dy * dy).as("%s distance", what).isGreaterThanOrEqualTo((long) minDistance * minDistance);
                    assertThat(x + b.x()).as("%s left", what).isGreaterThanOrEqualTo(room.col() * 256);
                    assertThat(x + b.x() + b.w()).as("%s right", what).isLessThanOrEqualTo((room.col() + 1) * 256);
                    assertThat(y + b.y()).as("%s top", what).isGreaterThanOrEqualTo(room.row() * 192);
                    assertThat(y + b.y() + b.h()).as("%s bottom", what).isLessThanOrEqualTo((room.row() + 1) * 192);
                    if (!c.species().ignoresScenery()) {
                        assertThat(Collision.blocked(grid, b.x(), b.y(), b.w(), b.h(), c.xFp(), c.yFp()))
                                .as("%s inside scenery", what).isFalse();
                    }
                }
            }
        }
    }

    @Test
    void theSameSeedAndVisitGiveTheSameRoom() {
        boolean anyDifference = false;
        for (String biome : BIOMES) {
            RoomAddress room = interiorRoomIn(biome);
            assertThat(describe(sim(room, 1L))).isEqualTo(describe(sim(room, 1L)));
            anyDifference |= !describe(sim(room, 1L)).equals(describe(sim(room, 2L)));
        }
        assertThat(anyDifference).as("a different run seed changes some room").isTrue();
    }

    @Test
    void everySpeciesAppearsInItsBiomes() {
        for (String biome : BIOMES) {
            RoomEntitiesData.Biome rules = content.roomEntities().biomes().get(biome);
            Set<String> expected = new TreeSet<>(rules.weights().keySet());
            rules.extras().forEach(e -> expected.add(e.id()));
            RoomAddress room = interiorRoomIn(biome);
            Set<String> seen = new TreeSet<>();
            for (long seed = 0; seed < 300 && !seen.containsAll(expected); seed++) {
                sim(room, seed).creatures().forEach(c -> seen.add(c.species().id()));
            }
            assertThat(seen).as("%s room %s", biome, room).containsAll(expected);
        }
    }

    @Test
    void aChiefLeadsAboutAThirdOfHutRooms() {
        RoomAddress hut = interiorRoomIn(BiomeResolver.HUT);
        int withChief = 0;
        for (long seed = 0; seed < 400; seed++) {
            if (sim(hut, seed).creatures().stream().anyMatch(c -> c.species().id().equals("chief"))) {
                withChief++;
            }
        }
        assertThat(withChief).as("35%% of 400, allowing for chance and cramped rooms").isBetween(100, 180);
    }

    @Test
    void everySpeciesInTheRosterIsPlacedSomewhere() {
        Set<String> placeable = new TreeSet<>();
        content.roomEntities().biomes().values().forEach(b -> {
            placeable.addAll(b.weights().keySet());
            b.extras().forEach(e -> placeable.add(e.id()));
        });
        Set<String> roster = new TreeSet<>();
        content.creatures().creatures().forEach(sp -> roster.add(sp.id()));
        assertThat(placeable).isEqualTo(roster);
    }
}
