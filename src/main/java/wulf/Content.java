package wulf;

import java.nio.file.Path;
import wulf.data.DisplayConfig;
import wulf.data.FontData;
import wulf.data.GameConfig;
import wulf.data.JsonDb;
import wulf.data.OriginalMap;
import wulf.data.OriginalMapRepository;
import wulf.data.Palette;
import wulf.data.SceneryData;
import wulf.data.SpriteRepository;
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
        OriginalMapRepository map,
        SpriteRepository sprites,
        SceneryCatalog scenery,
        RoomBaker rooms) {

    public static Content load(Path dataDir) {
        return load(new JsonDb(dataDir));
    }

    /** Loads through an existing database, so a caller can still reach its cache after a failure. */
    public static Content load(JsonDb db) {
        GameConfig game = db.load("config/game", GameConfig.class);
        DisplayConfig display = db.load("config/display", DisplayConfig.class);
        Palette palette = db.load("art/palette", Palette.class);
        FontData font = db.load("art/font/font", FontData.class);
        OriginalMapRepository map = new OriginalMapRepository(db.load("world/original_map", OriginalMap.class));
        SpriteRepository sprites = new SpriteRepository(db);
        SceneryCatalog scenery = new SceneryCatalog(db.load("world/scenery", SceneryData.class), sprites.all());
        RoomBaker rooms = new RoomBaker(map, scenery);
        return new Content(db, game, display, palette, font, map, sprites, scenery, rooms);
    }
}
