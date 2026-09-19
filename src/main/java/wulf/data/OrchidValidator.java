package wulf.data;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the schema cannot say about {@code orchids.json} (AGENTS.md §15, §20.6). */
public final class OrchidValidator {

    private static final String FILE = "data/entities/orchids.json";

    private OrchidValidator() {
    }

    /** A bloom's frames per colour, plus the one shoot they all share. */
    public static List<String> frames(String colour) {
        return List.of("bud_" + colour, "bloom0_" + colour, "bloom1_" + colour, "wilt_" + colour);
    }

    public static void check(OrchidData orchids, Palette palette, SpriteRepository sprites) {
        check(orchids, palette, CreatureSpriteValidator.framesBySprite(sprites));
    }

    public static void check(OrchidData orchids, Palette palette, Map<String, Set<String>> framesBySprite) {
        Set<String> frames = framesBySprite.get(orchids.sprite());
        if (frames == null) {
            throw new DataException(FILE, "/sprite",
                    "names sprite '" + orchids.sprite() + "', which is not in data/art/sprites/index.json");
        }
        if (!frames.contains("sprout")) {
            throw new DataException(FILE, "/sprite", "names a sprite with no 'sprout' frame");
        }
        Set<String> colours = new HashSet<>();
        Set<String> effects = new HashSet<>();
        for (int i = 0; i < orchids.orchids().size(); i++) {
            OrchidData.Orchid o = orchids.orchids().get(i);
            String at = "/orchids/" + i;
            if (!colours.add(o.colour())) {
                throw new DataException(FILE, at + "/colour", "repeats '" + o.colour() + "'");
            }
            if (!effects.add(o.effect())) {
                throw new DataException(FILE, at + "/effect", "repeats '" + o.effect() + "'");
            }
            // A bloom is drawn bright and its wilt plain, so both must be in the palette (§15.1).
            colour(palette, o.colour(), at + "/colour");
            colour(palette, bright(o.colour()), at + "/colour");
            for (String frame : frames(o.colour())) {
                if (!frames.contains(frame)) {
                    throw new DataException(FILE, at + "/colour",
                            "is '" + o.colour() + "', but sprite '" + orchids.sprite() + "' has no frame '" + frame + "'");
                }
            }
            if ((o.effect().equals("HASTE") || o.effect().equals("TORPOR")) && o.speedScaleFp() == 256) {
                throw new DataException(FILE, at + "/speedScaleFp",
                        "is 256, which leaves " + o.effect() + " doing nothing");
            }
        }
        OrchidData.Anchors a = orchids.anchors();
        if (a.perRoom() > 0 && a.insetPx() * 2 >= 192) {
            throw new DataException(FILE, "/anchors/insetPx", "is " + a.insetPx() + ", which leaves no room to grow in");
        }
    }

    /** {@code yellow} to {@code brightYellow}: the bloom's colour. */
    public static String bright(String colour) {
        return "bright" + Character.toUpperCase(colour.charAt(0)) + colour.substring(1);
    }

    private static void colour(Palette palette, String name, String at) {
        try {
            palette.indexOf(name);
        } catch (IllegalStateException e) {
            throw new DataException(FILE, at, "needs a palette colour named '" + name + "', which does not exist");
        }
    }
}
