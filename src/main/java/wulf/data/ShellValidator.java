package wulf.data;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Semantic checks for {@code shell.json} beyond its schema (AGENTS.md §20.6):
 * the colours it cycles must exist, its hotkeys must not collide with each
 * other or with the alphabet the hi-score selector uses, and the alphabet must
 * be something the font can actually draw.
 */
public final class ShellValidator {

    private static final String SOURCE = "data/config/shell.json";

    private ShellValidator() {
    }

    public static void check(ShellConfig shell, Palette palette, InputConfig input) {
        for (int i = 0; i < shell.title().cycle().colours().size(); i++) {
            String colour = shell.title().cycle().colours().get(i);
            if (!inPalette(palette, colour)) {
                throw new DataException(SOURCE, "/title/cycle/colours/" + i,
                        "names '" + colour + "', which is not a palette entry");
            }
        }
        ShellConfig.MapScreen map = shell.map();
        java.util.List<String> mapColours = new java.util.ArrayList<>(
                java.util.List.of(map.visited(), map.revealed(), map.here(), map.trail().colour()));
        for (String colour : mapColours) {
            if (!inPalette(palette, colour)) {
                throw new DataException(SOURCE, "/map", "names '" + colour + "', which is not a palette entry");
            }
        }
        // §17.7: the map's key must not also walk, swing or pause in any profile.
        for (Map.Entry<String, InputConfig.Profile> e : input.profiles().entrySet()) {
            InputConfig.Profile p = e.getValue();
            String key = map.keyFor(e.getKey());
            for (List<String> bound : List.of(p.up(), p.down(), p.left(), p.right(), p.fire(), p.pause(), p.quit())) {
                if (bound.contains(key)) {
                    throw new DataException(SOURCE, "/map", "opens the map with '" + key + "', which the '"
                            + e.getKey() + "' input profile already plays with");
                }
            }
        }
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < shell.title().menu().size(); i++) {
            ShellConfig.MenuItem item = shell.title().menu().get(i);
            if (!keys.add(item.key())) {
                throw new DataException(SOURCE, "/title/menu/" + i + "/key",
                        "binds '" + item.key() + "' twice on the title screen");
            }
        }
        // §17.2: the new game's key is a title key too, and not the difficulty's or the jobs'.
        String fresh = shell.title().newGameKey();
        if (keys.contains(fresh) || fresh.equals(shell.difficulty().key()) || fresh.equals(shell.newGamePlus().key())) {
            throw new DataException(SOURCE, "/title/newGameKey", "binds '" + fresh + "' twice on the title screen");
        }
        // The keys page offers a profile per digit; a menu hotkey on one of those digits would fight it.
        for (int i = 0; i < input.profiles().size(); i++) {
            String digit = String.valueOf((char) ('1' + i));
            if (keys.contains(digit)) {
                throw new DataException(SOURCE, "/title/menu",
                        "binds '" + digit + "', which the keys page already uses to choose an input profile");
            }
        }
        // §17.2, §17.4, §17.9: every menu line does something the shell knows, and none twice.
        checkEntries(shell.title().items(), ShellConfig.Title.IDS, "/title/items");
        if (shell.title().items().stream().noneMatch(e -> e.id().equals("start"))) {
            throw new DataException(SOURCE, "/title/items", "has no way to start a game ('start')");
        }
        checkEntries(shell.pause().items(), ShellConfig.Pause.IDS, "/pause/items");
        checkEntries(shell.options().items(), ShellConfig.Options.IDS, "/options/items");
        for (String id : shell.options().eightBit()) {
            if (!ShellConfig.Options.SWITCHES.contains(id)) {
                throw new DataException(SOURCE, "/options/eightBit", "names '" + id + "', which is not a switch "
                        + ShellConfig.Options.SWITCHES);
            }
        }
        if (shell.pause().items().stream().noneMatch(e -> e.id().equals("resume"))) {
            throw new DataException(SOURCE, "/pause/items", "has no way back to the game ('resume')");
        }
        if (shell.saves().jobNames().size() < shell.newGamePlus().levels().size()) {
            throw new DataException(SOURCE, "/saves/jobNames", "names fewer jobs than newGamePlus has");
        }
        // §17.8: the difficulty's key is a title key like the others, and its colour a real one.
        ShellConfig.Difficulty difficulty = shell.difficulty();
        if (keys.contains(difficulty.key()) || difficulty.key().equals(shell.newGamePlus().key())) {
            throw new DataException(SOURCE, "/difficulty/key",
                    "is '" + difficulty.key() + "', which the title already uses");
        }
        if (!inPalette(palette, difficulty.easyColour())) {
            throw new DataException(SOURCE, "/difficulty/easyColour",
                    "names '" + difficulty.easyColour() + "', which is not a palette entry");
        }
        if (shell.hiScores().alphabet().chars().distinct().count() != shell.hiScores().alphabet().length()) {
            throw new DataException(SOURCE, "/hiScores/alphabet", "repeats a letter");
        }
    }

    private static void checkEntries(List<ShellConfig.Entry> items, List<String> known, String at) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            String id = items.get(i).id();
            if (!known.contains(id)) {
                throw new DataException(SOURCE, at + "/" + i + "/id", "is '" + id + "', which is not one of " + known);
            }
            if (!seen.add(id)) {
                throw new DataException(SOURCE, at + "/" + i + "/id", "is '" + id + "' twice");
            }
        }
    }

    /**
     * The amulet reveal (§14.6): a verse for every count of quarters, a hold the fire
     * button can cut short but not before it has been seen, and every line and the
     * enlarged amulet fitting the playfield in the display font.
     */
    public static void checkAmuletReveal(ShellConfig shell, LandmarksData marks, DisplayConfig display, FontData font) {
        ShellConfig.AmuletReveal r = shell.amuletReveal();
        int quarters = marks.lairs().size();
        if (r.verses().size() != quarters) {
            throw new DataException(SOURCE, "/amuletReveal/verses",
                    "has " + r.verses().size() + " verses; there is one for each of the " + quarters + " quarters held");
        }
        FontData.Set big = font.sets().get("big");
        DisplayConfig.Playfield field = display.playfield();
        for (int v = 0; v < r.verses().size(); v++) {
            for (int l = 0; l < r.verses().get(v).size(); l++) {
                String line = r.verses().get(v).get(l);
                if (line.length() * big.advance() > field.w()) {
                    throw new DataException(SOURCE, "/amuletReveal/verses/" + v + "/" + l,
                            "is " + line.length() + " characters, wider than the playfield");
                }
            }
        }
        int tallest = 0;
        for (List<String> verse : r.verses()) {
            tallest = Math.max(tallest, verse.size());
        }
        if (r.prompt().length() * big.advance() > field.w()) {
            throw new DataException(SOURCE, "/amuletReveal/prompt", "is wider than the playfield");
        }
        // Two quarters down, a line of space, the verse, then the prompt.
        if (2 * marks.amulet().size().h() * r.scale() + (tallest + 2) * big.lineHeight() + REVEAL_MARGINS_PX > field.h()) {
            throw new DataException(SOURCE, "/amuletReveal/scale",
                    "is " + r.scale() + ": the amulet and its verse no longer fit the playfield");
        }
    }

    /**
     * §14.7, §14.9: a later job's ending — its words fitting the
     * screen in colours that exist, and its ride's frames drawn and its beats in order: the
     * horses there before anyone mounts, up before they ride, gone before the prompt.
     */
    private static void checkJobEnding(ShellConfig.JobEnding job, Palette palette, Map<String, SpriteData> sprites,
                                       int width, int big, String at) {
        for (int i = 0; i < job.lines().size(); i++) {
            ShellConfig.Line line = job.lines().get(i);
            colour(palette, line.colour(), at + "/lines/" + i + "/colour");
            for (int t = 0; t < line.text().size(); t++) {
                fits(line.text().get(t), big, width, at + "/lines/" + i + "/text/" + t);
            }
        }
        ShellConfig.Ride r = job.ride();
        if (r == null) {
            return;
        }
        for (List<String> frames : List.of(r.valeHorse(), r.jonesHorse(), r.valeRiding(), r.jonesRiding())) {
            for (String f : frames) {
                frame(sprites, r.sprite(), f, at + "/ride");
            }
        }
        if (r.comeAt() + r.comeTicks() > r.mountAt() || r.mountAt() > r.rideAt()
                || r.rideAt() + r.rideTicks() > job.promptAt()) {
            throw new DataException(SOURCE, at + "/ride",
                    "is out of order: the horses come, then they mount, then they ride, then the prompt");
        }
    }

    /** The game-over progress line must fit the playfield at its widest, "100". */
    public static void checkGameOver(ShellConfig shell, DisplayConfig display, FontData font) {
        String widest = shell.gameOver().progress().replace("{percent}", "100");
        if (widest.length() * font.sets().get("big").advance() > display.playfield().w()) {
            throw new DataException(SOURCE, "/gameOver/progress", "is wider than the playfield at 100%");
        }
        // §17.5: the continue offer, at its widest.
        int advance = font.sets().get("big").advance();
        String left = shell.gameOver().continuesLeft().replace("{n}", "9");
        if (left.length() * advance > display.playfield().w()) {
            throw new DataException(SOURCE, "/gameOver/continuesLeft", "is wider than the playfield");
        }
        if (shell.gameOver().continuePrompt().length() * advance > display.playfield().w()) {
            throw new DataException(SOURCE, "/gameOver/continuePrompt", "is wider than the playfield");
        }
    }

    /**
     * The map's quarter mark and arch mark (§17.7): sprites that exist, with every frame named,
     * small enough to sit inside one room of the map.
     */
    public static void checkMap(ShellConfig shell, DisplayConfig display, Map<String, SpriteData> sprites) {
        ShellConfig.QuarterIcon icon = shell.map().quarterIcon();
        SpriteData sprite = sprites.get(icon.sprite());
        if (sprite == null) {
            throw new DataException(SOURCE, "/map/quarterIcon/sprite", "names '" + icon.sprite() + "', which is not a sprite");
        }
        Set<String> frames = new HashSet<>();
        sprite.frames().forEach(f -> frames.add(f.id()));
        List<String> named = new java.util.ArrayList<>(icon.shiny());
        named.add(icon.taken());
        for (String frame : named) {
            if (!frames.contains(frame)) {
                throw new DataException(SOURCE, "/map/quarterIcon", "names frame '" + frame + "', which '"
                        + icon.sprite() + "' has not got");
            }
        }
        fitsARoom(display, sprite, "/map/quarterIcon/sprite");
        ShellConfig.ExitIcon exit = shell.map().exitIcon();
        SpriteData arch = sprites.get(exit.sprite());
        if (arch == null) {
            throw new DataException(SOURCE, "/map/exitIcon/sprite", "names '" + exit.sprite() + "', which is not a sprite");
        }
        Set<String> archFrames = new HashSet<>();
        arch.frames().forEach(f -> archFrames.add(f.id()));
        List<String> archNamed = new java.util.ArrayList<>(exit.open());
        archNamed.add(exit.frame());
        for (String frame : archNamed) {
            if (!archFrames.contains(frame)) {
                throw new DataException(SOURCE, "/map/exitIcon", "names frame '" + frame + "', which '"
                        + exit.sprite() + "' has not got");
            }
        }
        fitsARoom(display, arch, "/map/exitIcon/sprite");
    }

    /**
     * The mark before a menu's chosen line (§17.2): a sprite that exists, with the frame named, no
     * taller than a line of the big font and its gap.
     */
    public static void checkMarker(ShellConfig shell, FontData font, Map<String, SpriteData> sprites) {
        ShellConfig.Marker marker = shell.title().marker();
        SpriteData sprite = sprites.get(marker.sprite());
        if (sprite == null) {
            throw new DataException(SOURCE, "/title/marker/sprite", "names '" + marker.sprite() + "', which is not a sprite");
        }
        if (sprite.frames().stream().noneMatch(f -> f.id().equals(marker.frame()))) {
            throw new DataException(SOURCE, "/title/marker/frame", "names frame '" + marker.frame() + "', which '"
                    + marker.sprite() + "' has not got");
        }
        int line = font.sets().get("big").lineHeight() + 2;
        if (sprite.size().h() > line) {
            throw new DataException(SOURCE, "/title/marker/sprite",
                    "is " + sprite.size().h() + " px tall; a menu line is " + line);
        }
    }

    private static void fitsARoom(DisplayConfig display, SpriteData sprite, String pointer) {
        int cw = display.playfield().w() / wulf.world.RoomAddress.GRID_W;
        int ch = display.playfield().h() / wulf.world.RoomAddress.GRID_H;
        if (sprite.size().w() > cw || sprite.size().h() > ch) {
            throw new DataException(SOURCE, pointer, "is " + sprite.size().w() + "x" + sprite.size().h()
                    + ", larger than a room on the map (" + cw + "x" + ch + ")");
        }
    }

    /**
     * The idle title's scene (§17.2): its sprites and frames exist, its path's ticks rise, its
     * colour exists, and its rows fit the canvas in the display font.
     */
    public static void checkIntro(ShellConfig shell, Palette palette, DisplayConfig display, FontData font,
                                  Map<String, SpriteData> sprites) {
        ShellConfig.Intro intro = shell.attract().intro();
        if (intro == null) {
            return;
        }
        for (int i = 0; i < intro.backdrop().size(); i++) {
            ShellConfig.Placed p = intro.backdrop().get(i);
            frame(sprites, p.sprite(), p.frame(), "/attract/intro/backdrop/" + i);
        }
        ShellConfig.Plane plane = intro.plane();
        for (String f : plane.frames()) {
            frame(sprites, plane.sprite(), f, "/attract/intro/plane/frames");
        }
        frame(sprites, plane.sprite(), plane.parked(), "/attract/intro/plane/parked");
        for (int i = 1; i < plane.path().size(); i++) {
            if (plane.path().get(i).at() <= plane.path().get(i - 1).at()) {
                throw new DataException(SOURCE, "/attract/intro/plane/path/" + i + "/at",
                        "is not after the waypoint before it");
            }
        }
        colour(palette, intro.textColour(), "/attract/intro/textColour");
        int big = font.sets().get("big").advance();
        for (int i = 0; i < intro.text().size(); i++) {
            fits(intro.text().get(i), big, display.canvas().w(), "/attract/intro/text/" + i);
        }
    }

    /**
     * The end of a won game (§14.7): the closing scene's sprites, frames, colours and tune
     * all exist and every line of it fits the canvas in the display font; the tally's and the
     * roll's colours exist, and the roll's lines fit in the small one.
     */
    public static void checkEnding(ShellConfig shell, Palette palette, DisplayConfig display, FontData font,
                                   Map<String, SpriteData> sprites, MusicData music) {
        ShellConfig.Ending e = shell.ending();
        int width = display.canvas().w();
        int big = font.sets().get("big").advance();
        int small = font.sets().get("small").advance();
        for (int i = 0; i < e.backdrop().size(); i++) {
            ShellConfig.Placed p = e.backdrop().get(i);
            frame(sprites, p.sprite(), p.frame(), "/ending/backdrop/" + i);
        }
        for (String f : e.vale().walk()) {
            frame(sprites, e.vale().sprite(), f, "/ending/vale/walk");
        }
        frame(sprites, e.vale().sprite(), e.vale().stand(), "/ending/vale/stand");
        ShellConfig.Jones j = e.jones();
        for (String f : List.of(j.stand(), j.talk(), j.hold(), j.holdTalk())) {
            frame(sprites, j.sprite(), f, "/ending/jones");
        }
        if (!music.tunes().containsKey(e.tune())) {
            throw new DataException(SOURCE, "/ending/tune", "names '" + e.tune() + "', which music.json has not got");
        }
        colour(palette, e.promptColour(), "/ending/promptColour");
        fits(e.prompt(), big, width, "/ending/prompt");
        for (int i = 0; i < e.lines().size(); i++) {
            ShellConfig.Line line = e.lines().get(i);
            colour(palette, line.colour(), "/ending/lines/" + i + "/colour");
            for (int t = 0; t < line.text().size(); t++) {
                fits(line.text().get(t), big, width, "/ending/lines/" + i + "/text/" + t);
            }
        }
        for (int n = 0; n < shell.newGamePlus().levels().size(); n++) {
            ShellConfig.JobEnding job = shell.newGamePlus().levels().get(n).ending();
            if (job != null) {
                checkJobEnding(job, palette, sprites, width, big, "/newGamePlus/levels/" + n + "/ending");
            }
        }
        ShellConfig.Tally tally = shell.tally();
        colour(palette, tally.headingColour(), "/tally/headingColour");
        colour(palette, tally.labelColour(), "/tally/labelColour");
        colour(palette, tally.figureColour(), "/tally/figureColour");
        ShellConfig.Roll roll = shell.credits().roll();
        colour(palette, roll.headingColour(), "/credits/roll/headingColour");
        colour(palette, roll.bodyColour(), "/credits/roll/bodyColour");
        for (String line : shell.credits().lines()) {
            fits(line, small, width, "/credits/lines");
        }
        fits(roll.theEnd(), big, width, "/credits/roll/theEnd");
        for (String line : roll.before()) {
            fits(line, big, width, "/credits/roll/before");
        }
        for (String line : roll.after()) {
            fits(line, big, width, "/credits/roll/after");
        }
        if (roll.lineHeightPx() < font.sets().get("big").lineHeight()) {
            throw new DataException(SOURCE, "/credits/roll/lineHeightPx", "is less than the display font's line");
        }
        if (roll.fromY() > display.canvas().h()) {
            throw new DataException(SOURCE, "/credits/roll/fromY", "starts below the screen");
        }
    }

    private static void frame(Map<String, SpriteData> sprites, String sprite, String frame, String pointer) {
        SpriteData data = sprites.get(sprite);
        if (data == null) {
            throw new DataException(SOURCE, pointer, "names sprite '" + sprite + "', which does not exist");
        }
        if (data.frames().stream().noneMatch(f -> f.id().equals(frame)) && !data.mirror().containsKey(frame)) {
            throw new DataException(SOURCE, pointer, "names frame '" + frame + "', which '" + sprite + "' has not got");
        }
    }

    private static void colour(Palette palette, String name, String pointer) {
        if (!inPalette(palette, name)) {
            throw new DataException(SOURCE, pointer, "names '" + name + "', which is not a palette entry");
        }
    }

    private static void fits(String text, int advance, int width, String pointer) {
        if (text.length() * advance > width) {
            throw new DataException(SOURCE, pointer, "\"" + text + "\" is wider than the screen");
        }
    }

    /** The space the reveal keeps above the amulet, between it and the verse, and below. Layout, not a game number. */
    private static final int REVEAL_MARGINS_PX = 16;

    private static boolean inPalette(Palette palette, String name) {
        for (Palette.Entry e : palette.entries()) {
            if (e.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
