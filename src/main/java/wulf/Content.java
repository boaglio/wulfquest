package wulf;

import java.nio.file.Path;
import wulf.data.AnimationValidator;
import wulf.data.CreatureData;
import wulf.data.CreatureRefValidator;
import wulf.data.CreatureSpriteValidator;
import wulf.data.DataException;
import wulf.data.DisplayConfig;
import wulf.data.FlameData;
import wulf.data.JungleData;
import wulf.data.FontData;
import wulf.data.GameConfig;
import wulf.data.InputConfig;
import wulf.data.JsonDb;
import wulf.data.LootData;
import wulf.data.TreasureData;
import wulf.data.OriginalMap;
import wulf.data.OriginalMapRepository;
import wulf.data.Palette;
import wulf.data.PlayerData;
import wulf.data.RoomEntitiesData;
import wulf.data.SceneryData;
import wulf.data.SpriteRepository;
import wulf.sim.BiomePopulator;
import wulf.sim.Ecosystem;
import wulf.sim.ai.BehaviourCatalog;
import wulf.world.BiomeResolver;
import wulf.world.RoomBaker;
import wulf.world.SceneryCatalog;
import java.util.Set;
import wulf.data.WulfData;
import wulf.data.WulfValidator;
import wulf.sim.WulfRules;
import wulf.world.RoomAddress;
import java.util.LinkedHashSet;
import wulf.data.GuardianData;
import wulf.data.GuardianValidator;
import wulf.data.LandmarkValidator;
import wulf.data.LandmarksData;
import wulf.world.CollisionMask;
import java.util.ArrayList;
import java.util.List;
import wulf.sim.QuestRules;
import wulf.sim.Treasures;
import wulf.sim.RoomPopulator;
import wulf.sim.SwarmRules;
import wulf.sim.Beast;
import wulf.sim.BeastRules;
import wulf.sim.OrchidSpots;
import wulf.sim.Simulation;
import wulf.data.OrchidData;
import wulf.data.OrchidValidator;
import wulf.data.HighScores;
import wulf.data.Settings;
import wulf.data.MusicData;
import wulf.data.SfxData;
import wulf.data.ShellConfig;
import wulf.data.ShellValidator;
import wulf.data.AudioValidator;

/**
 * Everything loaded from the content database, validated and cross-checked,
 * in dependency order (AGENTS.md §20). Shared by the game and the dev tools so
 * they can never disagree about what the data means.
 */
public record Content(
        JsonDb db,
        GameConfig game,
        DisplayConfig display,
        Palette palette,
        FontData font,
        InputConfig input,
        OriginalMapRepository map,
        SpriteRepository sprites,
        SceneryCatalog scenery,
        RoomBaker rooms,
        PlayerData player,
        CreatureData creatures,
        RoomEntitiesData roomEntities,
        LootData loot,
        BiomeResolver biomes,
        WulfData wulf,
        FlameData flame,
        JungleData jungle,
        GuardianData guardians,
        LandmarksData landmarks,
        TreasureData treasures,
        OrchidData orchids,
        ShellConfig shell,
        SfxData sfx,
        MusicData music,
        HighScores defaultScores,
        Settings defaultSettings) {

    public static Content load(Path dataDir) {
        return load(new JsonDb(dataDir));
    }

    /** Loads through an existing database, so a caller can still reach its cache after a failure. */
    public static Content load(JsonDb db) {
        GameConfig game = db.load("config/game", GameConfig.class);
        DisplayConfig display = db.load("config/display", DisplayConfig.class);
        Palette palette = db.load("art/palette", Palette.class);
        FontData font = db.load("art/font/font", FontData.class);
        InputConfig input = db.load("config/input", InputConfig.class);
        ShellConfig shell = db.load("config/shell", ShellConfig.class);
        ShellValidator.check(shell, palette, input);
        ShellValidator.checkGameOver(shell, display, font);
        SfxData sfx = db.load("audio/sfx", SfxData.class);
        MusicData music = db.load("audio/music", MusicData.class);
        AudioValidator.check(sfx, music);
        // The shipped halves of the player database (§21.2, §21.3): defaults for a first run.
        HighScores defaultScores = db.load("config/default_highscores", HighScores.class, "highscores");
        Settings defaultSettings = db.load("config/settings_defaults", Settings.class, "settings");
        OriginalMapRepository map = new OriginalMapRepository(db.load("world/original_map", OriginalMap.class));
        SpriteRepository sprites = new SpriteRepository(db);
        ShellValidator.checkMap(shell, display, sprites.all());
        ShellValidator.checkEnding(shell, palette, display, font, sprites.all(), music);
        SceneryCatalog scenery = new SceneryCatalog(db.load("world/scenery", SceneryData.class), sprites.all());
        RoomBaker rooms = new RoomBaker(map, scenery);
        PlayerData player = db.load("entities/player", PlayerData.class);
        AnimationValidator.check(player, sprites);

        CreatureData creatures = db.load("entities/creatures", CreatureData.class);
        BehaviourCatalog.validate(creatures);
        CreatureSpriteValidator.check(creatures, sprites);
        RoomEntitiesData roomEntities = db.load("world/room_entities", RoomEntitiesData.class);
        LootData loot = db.load("entities/loot", LootData.class);
        BiomeResolver biomes = new BiomeResolver(map, scenery, roomEntities.biomeMarkers());
        CreatureRefValidator.check(creatures, roomEntities, biomes.inUse(), scenery.ids());
        WulfData wulf = db.load("entities/wulf", WulfData.class);
        WulfValidator.check(wulf, sprites);
        FlameData flame = db.load("entities/flame", FlameData.class);
        checkFlame(flame, sprites);
        JungleData jungle = db.load("entities/jungle", JungleData.class);
        checkJungle(jungle, creatures, palette, sprites);
        GuardianData guardians = db.load("entities/guardians", GuardianData.class);
        GuardianValidator.check(guardians, creatures, palette, sprites);
        OrchidData orchids = db.load("entities/orchids", OrchidData.class);
        OrchidValidator.check(orchids, palette, sprites);
        LandmarksData landmarks = db.load("world/landmarks", LandmarksData.class);
        ShellValidator.checkAmuletReveal(shell, landmarks, display, font);
        LandmarkValidator.check(landmarks, guardians, creatures, map, scenery.ids(),
                CreatureSpriteValidator.framesBySprite(sprites), solidity(rooms));
        TreasureData treasures = db.load("world/treasures", TreasureData.class);
        treasures.check(map, palette, CreatureSpriteValidator.framesBySprite(sprites));

        return new Content(db, game, display, palette, font, input, map, sprites, scenery, rooms, player,
                creatures, roomEntities, loot, biomes, wulf, flame, jungle, guardians, landmarks, treasures, orchids, shell, sfx, music,
                defaultScores, defaultSettings);
    }

    /**
     * The same content, with the player's own choices laid over it (§21.3): the
     * input profile they picked and any feature flag they turned on or off.
     * {@code game.json} ships the defaults; settings are the player's answer.
     */
    public Content withSettings(Settings settings) {
        return new Content(db, game.withFeatures(settings.features()), display.withCrt(settings.crt()), palette, font,
                input.withActive(settings.inputProfile()), map, sprites, scenery, rooms, player, creatures,
                roomEntities, loot, biomes, wulf, flame, jungle, guardians, landmarks, treasures, orchids, shell, sfx, music, defaultScores,
                defaultSettings);
    }

    /**
     * The same content with the amulet laid out as the original's set {@code set} (§14.2):
     * a game started from it hides the quarters there instead of picking a set from its seed.
     * For tests, tools and {@code --amulet-set}.
     */
    public Content withAmuletSet(int set) {
        return new Content(db, game, display, palette, font, input, map, sprites, scenery, rooms, player, creatures,
                roomEntities, loot, biomes, wulf, flame, jungle, guardians, landmarks.withAmuletSet(set), treasures,
                orchids, shell, sfx, music, defaultScores, defaultSettings);
    }

    /** The same content with one feature flag set (§1.1): for tests and tools that need a [NEW] rule on or off. */
    public Content withFeature(String name, boolean on) {
        return withFeatures(java.util.Map.of(name, on));
    }

    /** The same content with these feature flags laid over {@code game.json}'s; an unknown name is refused. */
    public Content withFeatures(java.util.Map<String, Boolean> flags) {
        if (flags.isEmpty()) {
            return this;
        }
        for (String name : flags.keySet()) {
            game.feature(name);
        }
        return new Content(db, game.withFeatures(flags), display, palette, font, input, map, sprites,
                scenery, rooms, player, creatures, roomEntities, loot, biomes, wulf, flame, jungle, guardians, landmarks, treasures, orchids,
                shell, sfx, music, defaultScores, defaultSettings);
    }

    /**
     * The orchids that grow (§15): the original's five, or with {@code features.inventedOrchids}
     * our own six [NEW]. Everything — the jungle, the painter, the panel — reads them from here.
     */
    @Override
    public OrchidData orchids() {
        return game.feature("inventedOrchids") ? orchids.invented() : orchids;
    }

    /**
     * The living jungle: creatures, how rooms are populated, what exploring scores, the Wulf, and the quest.
     * The creatures are the original's (§12.8), or with {@code features.inventedCreatures} our own [NEW].
     */
    public Ecosystem ecosystem() {
        if (!game.feature("inventedCreatures")) {
            return new Ecosystem(originalCreatures(), RoomPopulator.NONE, loot.event("roomFirstVisit"), wulfRules(),
                    questRules(), orchids(), flame, swarmRules(), beastRules(), orchidSpots());
        }
        // §14.3: the lairs and the way out are kept clear by the simulation, which knows this game's lairs.
        RoomPopulator populator = new BiomePopulator(creatures, roomEntities, biomes, game.difficulty());
        return new Ecosystem(creatures, populator, loot.event("roomFirstVisit"), wulfRules(), questRules(), orchids(),
                flame);
    }

    /** The quest's rules: each lair's guardian as a species, the Keeper, and the numbers that pace and score it. */
    public QuestRules questRules() {
        List<CreatureData.Species> wardens = new ArrayList<>();
        for (LandmarksData.Lair lair : landmarks.lairs()) {
            wardens.add(guardians.asSpecies(guardians.guardian(lair.guardian())));
        }
        return new QuestRules(true, landmarks, wardens, guardians.keeperSpecies(), guardians.orbit(),
                guardians.keeper().stepAsideFp(), guardians.keeper().asideMinPx(), guardians.keeper().asideMaxPx(),
                game.exit().keeperNudgeZonePx(),
                game.exit().keeperNudgePx(), loot.event("amuletPiece"), loot.event("escapeBonus"),
                loot.event("timeBonusMax"), loot.event("timeBonusTicksDivisor"), loot.event("lifeRemainingBonus"),
                game.feature("caveHints"), treasurePlaces());
    }

    /**
     * Where the treasures may lie (§16.4): every room's two places, less the rooms the quest
     * keeps for itself — the way out, and the map scroll's and the eyes' rooms. This game's
     * lairs are left out when the game lays them, once it has picked where the amulet lies.
     */
    public Treasures treasurePlaces() {
        Set<RoomAddress> keepClear = new LinkedHashSet<>();
        keepClear.add(landmarks.exitRoom());
        LandmarksData.Items items = landmarks.items();
        if (items.map() != null) {
            keepClear.add(LandmarksData.room(items.map().room()));
        }
        for (LandmarksData.Eye eye : items.eyes()) {
            keepClear.add(LandmarksData.room(eye.room()));
        }
        int[][] places = treasures.places(map, solidity(rooms), keepClear);
        boolean[] life = new boolean[treasures.kinds().size()];
        for (int i = 0; i < life.length; i++) {
            life[i] = treasures.kinds().get(i).extraLife();
        }
        return new Treasures(treasures.fillPercent(), treasures.score(), treasures.pickupBox(), life, places[0],
                places[1]);
    }

    /**
     * The Wulf's rules: the original's prowler (§13.8), or with {@code features.roamingWulf} the roaming
     * one [NEW], its forbidden rooms resolved (§13.3): the start room, the lairs, the way out.
     */
    public WulfRules wulfRules() {
        if (!game.feature("roamingWulf")) {
            return WulfRules.patrol(wulf);
        }
        Set<RoomAddress> never = new LinkedHashSet<>();
        boolean lairs = false;
        for (String where : wulf.appearance().neverInRooms()) {
            switch (where) {
                case WulfData.START -> never.add(map.startRoom());
                case WulfData.LAIR -> lairs = true;
                case WulfData.EXIT -> never.add(landmarks.exitRoom());
                default -> throw new IllegalStateException("unknown neverInRooms name '" + where + "'");
            }
        }
        return WulfRules.of(wulf, game.transition().wulfArrivalDelayTicks(), loot.event("wulfEvaded"), never, lairs);
    }

    /**
     * The original's roster (§12.8) as the simulation reads a roster: {@code jungle.json}'s creatures
     * with {@code creatures.json}'s shared numbers — the puff, the hit flash, the gait.
     */
    public CreatureData originalCreatures() {
        return new CreatureData(jungle.schemaVersion(), jungle.fidelity(), creatures.diagonalScaleFp(),
                creatures.puffTicks(), creatures.hurtFlashTicks(), creatures.walkTicksPerFrame(), jungle.creatures(),
                creatures.projectiles());
    }

    /** How the original's small creatures come (§12.8), its names resolved to roster and palette indices. */
    public SwarmRules swarmRules() {
        JungleData.Swarm s = jungle.swarm();
        CreatureData roster = originalCreatures();
        int[] pick = s.pick().table().stream().mapToInt(roster::indexOf).toArray();
        int[] tints = s.tints().stream().mapToInt(palette::indexOf).toArray();
        int[] appear = s.appearScores().stream().mapToInt(Integer::intValue).toArray();
        return new SwarmRules(true, s.roomMax(), s.slots(), s.everyTicks(), s.appearTicks(), s.lingerTicks(),
                s.quietAfterGettingUpTicks(), s.area(), s.playerClearancePx(), pick, s.pick().open(),
                s.pick().openPerQuarter(), s.pick().fold(), tints, appear, s.facingRightBonus());
    }

    /**
     * The original's big beasts (§12.9): each room's spot from its type ({@code $A7D4}), and the actor
     * table's placings turned from the original's numbers — a sprite's left edge, feet counted from
     * the top of the screen — into our world feet.
     */
    public BeastRules beastRules() {
        JungleData.Beasts b = jungle.beasts();
        List<CreatureData.Species> kinds = b.kinds();
        List<Beast.Kind> kindOf = kinds.stream().map(k -> Beast.Kind.valueOf(k.behaviour().kind())).toList();
        int rooms = map.gridW() * map.gridH();
        int[] spotX = new int[rooms];
        int[] spotY = new int[rooms];
        for (int row = 0; row < map.gridH(); row++) {
            for (int col = 0; col < map.gridW(); col++) {
                List<Integer> spot = b.spots().get(map.roomType(col, row));
                int i = new RoomAddress(col, row).index();
                spotX[i] = spot.get(0);
                spotY[i] = spot.get(1) - b.screenTopPx();
            }
        }
        int[] sleepers = java.util.stream.IntStream.range(0, kinds.size())
                .filter(k -> kindOf.get(k) == Beast.Kind.SLEEPER).toArray();
        List<BeastRules.Placing> placings = new ArrayList<>();
        for (JungleData.Placed p : b.placed()) {
            int k = indexOfKind(kinds, p.kind());
            RoomAddress room = LandmarksData.room(p.room());
            int x = room.col() * Simulation.ROOM_W_PX + p.x() + kinds.get(k).size().w() / 2;
            int y = room.row() * Simulation.ROOM_H_PX + p.y() - b.screenTopPx();
            placings.add(new BeastRules.Placing(k, room, x, y, p.heading(), palette.indexOf(p.tint()), p.asleep()));
        }
        return new BeastRules(true, b.followRows(), b.followColumns(), kinds, kindOf, spotX, spotY, sleepers, placings);
    }

    /**
     * The original's orchid spots (§15.2): one a room, its type's from {@code $D6A6} — the table
     * the beasts stand at — turned to our feet: half the flower's width right of the sprite's left
     * edge, and the screen's top taken off. A spot of {@code 0, 0} grows nothing. With
     * {@code features.inventedOrchids} our own flowers find their own anchors instead.
     */
    public OrchidSpots orchidSpots() {
        if (game.feature("inventedOrchids")) {
            return OrchidSpots.NONE;
        }
        JungleData.Beasts b = jungle.beasts();
        int halfW = sprites.get(orchids.sprite()).size().w() / 2;
        int rooms = map.gridW() * map.gridH();
        int[] x = new int[rooms];
        int[] y = new int[rooms];
        for (int row = 0; row < map.gridH(); row++) {
            for (int col = 0; col < map.gridW(); col++) {
                List<Integer> spot = b.spots().get(map.roomType(col, row));
                int i = new RoomAddress(col, row).index();
                boolean none = spot.get(0) == 0 && spot.get(1) == 0;
                x[i] = none ? OrchidSpots.NO_SPOT : spot.get(0) + halfW;
                y[i] = spot.get(1) - b.screenTopPx();
            }
        }
        return new OrchidSpots(x, y);
    }

    private static int indexOfKind(List<CreatureData.Species> kinds, String id) {
        for (int k = 0; k < kinds.size(); k++) {
            if (kinds.get(k).id().equals(id)) {
                return k;
            }
        }
        throw new IllegalStateException("no beast '" + id + "' in jungle.json");
    }

    /**
     * §12.8: every behaviour known with its parameters, every sprite drawn, every name in the pick
     * table a creature of the roster, the fold inside the table, and every tint a bright colour.
     */
    private static void checkJungle(JungleData jungle, CreatureData shared, Palette palette, SpriteRepository sprites) {
        String source = "data/entities/jungle.json";
        CreatureData roster = new CreatureData(jungle.schemaVersion(), jungle.fidelity(), shared.diagonalScaleFp(),
                shared.puffTicks(), shared.hurtFlashTicks(), shared.walkTicksPerFrame(), jungle.creatures(), List.of());
        BehaviourCatalog.validate(roster, source);
        CreatureSpriteValidator.check(roster, CreatureSpriteValidator.framesBySprite(sprites), source);
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < jungle.creatures().size(); i++) {
            if (!ids.add(jungle.creatures().get(i).id())) {
                throw new DataException(source, "/creatures/" + i + "/id", "repeats '" + jungle.creatures().get(i).id() + "'");
            }
        }
        JungleData.Pick pick = jungle.swarm().pick();
        for (int i = 0; i < pick.table().size(); i++) {
            if (!ids.contains(pick.table().get(i))) {
                throw new DataException(source, "/swarm/pick/table/" + i,
                        "is '" + pick.table().get(i) + "', which is not a creature of /creatures");
            }
        }
        if (pick.fold() > pick.table().size() || pick.open() > pick.table().size()) {
            throw new DataException(source, "/swarm/pick", "folds or opens past the end of its table of " + pick.table().size());
        }
        for (int i = 0; i < jungle.swarm().tints().size(); i++) {
            String tint = jungle.swarm().tints().get(i);
            if (!palette.has(tint) || palette.indexOf(tint) < 9) {
                throw new DataException(source, "/swarm/tints/" + i, "is '" + tint + "', which is not a bright colour of the palette");
            }
        }
        checkBeasts(jungle.beasts(), palette, sprites, source);
    }

    /** §12.9: every beast a known kind with its speeds, drawn, placed in a real room, in a bright colour. */
    private static void checkBeasts(JungleData.Beasts beasts, Palette palette, SpriteRepository sprites, String source) {
        java.util.Map<Beast.Kind, List<String>> params = java.util.Map.of(
                Beast.Kind.RHINO, List.of("paceFp", "shovedFp"),
                Beast.Kind.TRIBESMAN, List.of("walkSlowFp", "walkFastFp", "shovedFp", "hintPx", "callEveryTicks"),
                Beast.Kind.SLEEPER, List.of("chargeFp", "shovedFp", "wakeTicks"));
        List<CreatureData.Species> kinds = beasts.kinds();
        for (int i = 0; i < kinds.size(); i++) {
            CreatureData.Behaviour b = kinds.get(i).behaviour();
            String at = "/beasts/kinds/" + i + "/behaviour";
            Beast.Kind kind;
            try {
                kind = Beast.Kind.valueOf(b.kind());
            } catch (IllegalArgumentException e) {
                throw new DataException(source, at + "/kind", "is '" + b.kind() + "', which is not one of "
                        + java.util.Arrays.toString(Beast.Kind.values()));
            }
            if (!new LinkedHashSet<>(params.get(kind)).equals(b.params().keySet())) {
                throw new DataException(source, at + "/params", "has " + b.params().keySet() + "; " + kind + " takes "
                        + params.get(kind));
            }
        }
        CreatureData drawn = new CreatureData(1, "canon", 256, 1, 0, 1, kinds, List.of());
        java.util.Map<String, Set<String>> frames = CreatureSpriteValidator.framesBySprite(sprites);
        CreatureSpriteValidator.check(drawn, frames, source);
        for (int i = 0; i < kinds.size(); i++) {
            Set<String> has = frames.get(kinds.get(i).sprite());
            if (kinds.get(i).behaviour().kind().equals("SLEEPER") && !(has.contains("sleep") && has.contains("sleep_l"))) {
                throw new DataException(source, "/beasts/kinds/" + i + "/sprite",
                        "names sprite '" + kinds.get(i).sprite() + "', which has no 'sleep' frame; a sleeper lies in one");
            }
        }
        for (int i = 0; i < beasts.placed().size(); i++) {
            JungleData.Placed p = beasts.placed().get(i);
            String at = "/beasts/placed/" + i;
            if (kinds.stream().noneMatch(k -> k.id().equals(p.kind()))) {
                throw new DataException(source, at + "/kind", "is '" + p.kind() + "', which is not one of /beasts/kinds");
            }
            if (!palette.has(p.tint()) || palette.indexOf(p.tint()) < 9) {
                throw new DataException(source, at + "/tint", "is '" + p.tint() + "', which is not a bright colour of the palette");
            }
        }
    }

    /** §12.7: the flame flickers between two frames, each mirrored, as a creature walks. */
    private static void checkFlame(FlameData flame, SpriteRepository sprites) {
        java.util.Set<String> frames = CreatureSpriteValidator.framesBySprite(sprites).get(flame.sprite());
        for (String needed : java.util.List.of("walk0", "walk1", "walk0_l", "walk1_l")) {
            if (frames == null || !frames.contains(needed)) {
                throw new DataException("data/entities/flame.json", "/sprite",
                        "names sprite '" + flame.sprite() + "', which has no frame '" + needed + "'");
            }
        }
    }

    /** World-cell solidity straight off the baked masks, for the load-time landmark checks. */
    private static LandmarkValidator.Solid solidity(RoomBaker rooms) {
        return (gx, gy) -> {
            if (gx < 0 || gy < 0 || gx >= RoomAddress.GRID_W * CollisionMask.COLS
                    || gy >= RoomAddress.GRID_H * CollisionMask.ROWS) {
                return true;
            }
            return rooms.room(new RoomAddress(gx / CollisionMask.COLS, gy / CollisionMask.ROWS))
                    .mask().isSolid(gx % CollisionMask.COLS, gy % CollisionMask.ROWS);
        };
    }
}
