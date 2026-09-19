package wulf.data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the schema cannot say about {@code guardians.json} (AGENTS.md §14.3, §20.6). */
public final class GuardianValidator {

    /** Every guardian gallops; the Keeper stands, then steps aside over four frames (§14.4). */
    public static final List<String> GUARDIAN_FRAMES = List.of("walk0", "walk1", "walk0_l", "walk1_l");
    public static final List<String> KEEPER_FRAMES = List.of("stand", "step0", "step1", "aside");

    private static final String FILE = "data/entities/guardians.json";

    private GuardianValidator() {
    }

    public static void check(GuardianData wardens, CreatureData creatures, Palette palette, SpriteRepository sprites) {
        check(wardens, creatures, palette, CreatureSpriteValidator.framesBySprite(sprites));
    }

    public static void check(GuardianData wardens, CreatureData creatures, Palette palette,
                             Map<String, Set<String>> framesBySprite) {
        List<GuardianData.Guardian> all = wardens.guardians();
        for (int i = 0; i < all.size(); i++) {
            GuardianData.Guardian g = all.get(i);
            String at = "/guardians/" + i;
            if (!creatures.has(g.basedOn())) {
                throw new DataException(FILE, at + "/basedOn",
                        "is '" + g.basedOn() + "', which is not a species in data/entities/creatures.json");
            }
            CreatureData.Species sibling = creatures.species(g.basedOn());
            if (g.size().w() < sibling.size().w() || g.size().h() < sibling.size().h()) {
                throw new DataException(FILE, at + "/size", "is " + g.size().w() + "x" + g.size().h()
                        + ", no larger than the " + g.basedOn() + " it towers over (" + sibling.size().w() + "x"
                        + sibling.size().h() + ")");
            }
            try {
                palette.indexOf(g.colour());
            } catch (IllegalStateException e) {
                throw new DataException(FILE, at + "/colour", "is '" + g.colour() + "', which is not a palette colour");
            }
            frames(framesBySprite, at + "/sprite", g.sprite(), GUARDIAN_FRAMES);
            for (int j = i + 1; j < all.size(); j++) {
                if (all.get(j).id().equals(g.id())) {
                    throw new DataException(FILE, "/guardians/" + j + "/id", "repeats '" + g.id() + "'");
                }
            }
        }
        frames(framesBySprite, "/keeper/sprite", wardens.keeper().sprite(), KEEPER_FRAMES);
        if (wardens.orbit().lungePx() > 0 && wardens.orbit().lungeTicks() < 1) {
            throw new DataException(FILE, "/orbit/lungeTicks", "is " + wardens.orbit().lungeTicks()
                    + ", so a guardian that breaks orbit would never charge");
        }
    }

    private static void frames(Map<String, Set<String>> framesBySprite, String at, String sprite, List<String> needed) {
        Set<String> have = framesBySprite.get(sprite);
        if (have == null) {
            throw new DataException(FILE, at, "names sprite '" + sprite + "', which is not in data/art/sprites/index.json");
        }
        for (String frame : needed) {
            if (!have.contains(frame)) {
                throw new DataException(FILE, at,
                        "names sprite '" + sprite + "', which has no frame '" + frame + "'; this one needs " + needed);
            }
        }
    }
}
