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
        BiomeResolver biomes) {

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

        return new Content(db, game, display, palette, font, input, map, sprites, scenery, rooms, player,
                creatures, roomEntities, loot, biomes);
    }

    /** The living jungle: creatures, how rooms are populated, and what exploring scores. */
    public Ecosystem ecosystem() {
        return new Ecosystem(creatures,
                new BiomePopulator(creatures, roomEntities, biomes, game.difficulty()),
                loot.event("roomFirstVisit"));
    }
}
