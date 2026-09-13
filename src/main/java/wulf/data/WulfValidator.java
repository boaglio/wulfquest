package wulf.data;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** What the schema cannot say about {@code wulf.json} (AGENTS.md §13, §20.6). */
public final class WulfValidator {

    /** Running, and howling at the edge while it warns — each facing both ways. */
    public static final List<String> FRAMES = List.of("walk0", "walk1", "howl", "walk0_l", "walk1_l", "howl_l");

    private static final String FILE = "data/entities/wulf.json";

    private WulfValidator() {
    }

    public static void check(WulfData wulf, SpriteRepository sprites) {
        check(wulf, CreatureSpriteValidator.framesBySprite(sprites));
    }

    public static void check(WulfData wulf, Map<String, Set<String>> framesBySprite) {
        Set<String> frames = framesBySprite.get(wulf.sprite());
        if (frames == null) {
            throw new DataException(FILE, "/sprite", "names sprite '" + wulf.sprite() + "', which is not in data/art/sprites/index.json");
        }
        for (String needed : FRAMES) {
            if (!frames.contains(needed)) {
                throw new DataException(FILE, "/sprite",
                        "names sprite '" + wulf.sprite() + "', which has no frame '" + needed + "'; the Wulf needs " + FRAMES);
            }
        }
        WulfData.Appearance a = wulf.appearance();
        if (a.warningFlashOnTicks() > a.warningFlashPeriodTicks()) {
            throw new DataException(FILE, "/appearance/warningFlashOnTicks", "is " + a.warningFlashOnTicks()
                    + ", longer than warningFlashPeriodTicks (" + a.warningFlashPeriodTicks() + ")");
        }
        CreatureData.Box b = wulf.collisionBox();
        if (b.w() > wulf.size().w() || b.h() > wulf.size().h()) {
            throw new DataException(FILE, "/collisionBox", "is " + b.w() + "x" + b.h() + ", larger than the sprite's "
                    + wulf.size().w() + "x" + wulf.size().h());
        }
    }
}
