package wulf;

import java.nio.file.Path;
import wulf.data.AnimationValidator;
import wulf.data.CreatureData;
import wulf.data.CreatureRefValidator;
import wulf.data.CreatureSpriteValidator;
import wulf.data.DisplayConfig;
import wulf.data.FontData;
import wulf.data.GameConfig;
import wulf.data.InputConfig;
import wulf.data.JsonDb;
import wulf.data.LootData;
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
import wulf.sim.RoomPopulator;

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
        GuardianData guardians,
        LandmarksData landmarks) {

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
        OriginalMapRepository map = new OriginalMapRepository(db.load("world/original_map", OriginalMap.class));
        SpriteRepository sprites = new SpriteRepository(db);
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
        GuardianData guardians = db.load("entities/guardians", GuardianData.class);
        GuardianValidator.check(guardians, creatures, palette, sprites);
        LandmarksData landmarks = db.load("world/landmarks", LandmarksData.class);
        LandmarkValidator.check(landmarks, guardians, creatures, map, scenery.ids(),
                CreatureSpriteValidator.framesBySprite(sprites), solidity(rooms));

        return new Content(db, game, display, palette, font, input, map, sprites, scenery, rooms, player,
                creatures, roomEntities, loot, biomes, wulf, guardians, landmarks);
    }

    /** The living jungle: creatures, how rooms are populated, what exploring scores, the Wulf, and the quest. */
    public Ecosystem ecosystem() {
        RoomPopulator jungle = new BiomePopulator(creatures, roomEntities, biomes, game.difficulty());
        // §14.3: a lair is a single clean puzzle — its guardian and nothing else. The way out is kept clear too.
        Set<RoomAddress> clear = new LinkedHashSet<>(landmarks.lairRooms());
        clear.add(landmarks.exitRoom());
        RoomPopulator populator = (spawner, room, seed, visit) -> {
            if (!clear.contains(room)) {
                jungle.populate(spawner, room, seed, visit);
            }
        };
        return new Ecosystem(creatures, populator, loot.event("roomFirstVisit"), wulfRules(), questRules());
    }

    /** The quest's rules: each lair's guardian as a species, the Keeper, and the numbers that pace and score it. */
    public QuestRules questRules() {
        List<CreatureData.Species> wardens = new ArrayList<>();
        for (LandmarksData.Lair lair : landmarks.lairs()) {
            wardens.add(guardians.asSpecies(guardians.guardian(lair.guardian())));
        }
        return new QuestRules(true, landmarks, wardens, guardians.keeperSpecies(), guardians.orbit(),
                guardians.keeper().stepAsidePx(), game.exit().keeperStepAsideTicks(), game.exit().keeperNudgeZonePx(),
                game.exit().keeperNudgePx(), loot.event("amuletPiece"), loot.event("escapeBonus"),
                loot.event("timeBonusMax"), loot.event("timeBonusTicksDivisor"), loot.event("lifeRemainingBonus"),
                game.feature("caveHints"));
    }

    /** The Wulf's rules, with its forbidden rooms resolved (§13.3): the start room, the lairs, the way out. */
    public WulfRules wulfRules() {
        Set<RoomAddress> never = new LinkedHashSet<>();
        for (String where : wulf.appearance().neverInRooms()) {
            switch (where) {
                case WulfData.START -> never.add(map.startRoom());
                case WulfData.LAIR -> never.addAll(landmarks.lairRooms());
                case WulfData.EXIT -> never.add(landmarks.exitRoom());
                default -> throw new IllegalStateException("unknown neverInRooms name '" + where + "'");
            }
        }
        return WulfRules.of(wulf, game.transition().wulfArrivalDelayTicks(), loot.event("wulfEvaded"), never);
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
