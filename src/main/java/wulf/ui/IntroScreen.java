package wulf.ui;

import java.util.List;
import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.FontSet;
import wulf.render.Framebuffer;
import wulf.render.Sprite;
import wulf.render.SpriteBank;

/**
 * The scene the idle title plays before the demo (AGENTS.md §17.2, {@code [NEW]}): a small plane
 * comes down over the forest and lands, and a line types out under it. Every place, frame, word
 * and tick is {@code shell.json → attract.intro}'s; this only draws the moment it is given.
 */
final class IntroScreen {

    private final ShellConfig.Intro config;
    private final FontSet font;
    private final int ground;
    private final int textColour;
    private final List<Sprite> backdrop;
    private final Sprite plane;

    IntroScreen(ShellConfig.Intro config, FontSet font, Palette palette, SpriteBank sprites) {
        this.config = config;
        this.font = font;
        this.ground = palette.indexOf("black");
        this.textColour = palette.indexOf(config.textColour());
        this.backdrop = config.backdrop().stream().map(p -> sprites.get(p.sprite())).toList();
        this.plane = sprites.get(config.plane().sprite());
    }

    void paint(Framebuffer fb, long t) {
        fb.clear(ground);
        for (int i = 0; i < backdrop.size(); i++) {
            ShellConfig.Placed p = config.backdrop().get(i);
            Sprite s = backdrop.get(i);
            s.blit(fb, p.frame(), p.x() + s.originX(), p.y() + s.originY());
        }
        ShellConfig.Plane p = config.plane();
        int[] at = p.at(t);
        String frame = p.parked(t) ? p.parked() : p.frames().get((int) (t / p.ticksPerFrame() % p.frames().size()));
        plane.blit(fb, frame, at[0], at[1]);
        paintText(fb, t);
    }

    /** The rows type out a character every {@code ticksPerChar} from {@code textAt}, one after the other. */
    private void paintText(Framebuffer fb, long t) {
        if (t < config.textAt()) {
            return;
        }
        long shown = (t - config.textAt()) / config.ticksPerChar();
        int y = config.textY();
        for (String row : config.text()) {
            if (shown <= 0) {
                break;
            }
            // Placed by the whole row, so typing grows it left to right instead of from the middle.
            int left = (fb.width() - font.widthOf(row)) / 2;
            font.draw(fb, row.substring(0, (int) Math.min(row.length(), shown)), left, y, textColour);
            shown -= row.length();
            y += font.lineHeight();
        }
    }
}
