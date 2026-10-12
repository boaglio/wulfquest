package wulf.ui;

import java.util.List;
import wulf.data.Palette;
import wulf.data.ShellConfig;
import wulf.render.FontSet;
import wulf.render.Framebuffer;
import wulf.render.Sprite;
import wulf.render.SpriteBank;

/**
 * The closing scene (AGENTS.md §14.7, {@code [NEW]}), asked for by the user: on the whole
 * screen, a clearing in the jungle; Vale walks in with the amulet to Mr Jones, an old hand
 * in a fedora, and gives it to him; what they say types out under them. Every place, frame,
 * word and tick is {@code shell.json → ending}'s; this only draws the moment it is given.
 *
 * <p>The same clearing, later, is where Mr Jones offers the next job (§14.9): Vale stands
 * before him, the amulet gone to its museum, and they talk.
 */
final class EndingScreen {

    /** Layout, not a game number: how far up from the bottom the prompt sits. */
    private static final int PROMPT_FROM_BOTTOM_PX = 24;

    private final ShellConfig.Ending config;
    private final FontSet font;
    private final Palette palette;
    private final int ground;
    private final int prompt;
    private final int[] lineColours;
    private final List<Sprite> backdrop;
    private final Sprite vale;
    private final Sprite jones;
    private final Sprite amulet;
    private final List<String> framesBySlot;

    EndingScreen(ShellConfig.Ending config, FontSet font, Palette palette, SpriteBank sprites, Sprite amulet,
                 List<String> framesBySlot) {
        this.config = config;
        this.font = font;
        this.palette = palette;
        this.ground = palette.indexOf("black");
        this.prompt = palette.indexOf(config.promptColour());
        this.lineColours = config.lines().stream().mapToInt(l -> palette.indexOf(l.colour())).toArray();
        this.backdrop = config.backdrop().stream().map(p -> sprites.get(p.sprite())).toList();
        this.vale = sprites.get(config.vale().sprite());
        this.jones = sprites.get(config.jones().sprite());
        this.amulet = amulet;
        this.framesBySlot = List.copyOf(framesBySlot);
    }

    /** Where Vale's feet are at tick {@code t}: walking in, then standing. */
    int valeX(long t) {
        ShellConfig.Walker v = config.vale();
        if (t >= v.walkTicks()) {
            return v.toX();
        }
        return v.fromX() + (int) ((v.toX() - v.fromX()) * t / v.walkTicks());
    }

    /** Whether Mr Jones has the amulet at tick {@code t}. */
    boolean handedOver(long t) {
        return t >= config.amulet().handOverAt() + config.amulet().handOverTicks();
    }

    /** Whether {@code speaker}'s line is still typing at tick {@code t}. */
    private boolean speaking(List<ShellConfig.Line> lines, String speaker, long t) {
        for (ShellConfig.Line line : lines) {
            if (line.speaker().equals(speaker) && t >= line.at()
                    && t < line.at() + (long) line.length() * config.ticksPerChar()) {
                return true;
            }
        }
        return false;
    }

    void paint(Framebuffer fb, long t) {
        paintBackdrop(fb);
        int gy = config.groundY();
        ShellConfig.Jones j = config.jones();
        boolean talking = talking(config.lines(), t);
        boolean holding = handedOver(t);
        String jonesFrame = holding ? (talking ? j.holdTalk() : j.hold()) : (talking ? j.talk() : j.stand());
        jones.blit(fb, jonesFrame, j.x(), gy);

        ShellConfig.Walker v = config.vale();
        int vx = valeX(t);
        String valeFrame = t < v.walkTicks() ? v.walk().get((int) (t / v.ticksPerFrame() % v.walk().size())) : v.stand();
        vale.blit(fb, valeFrame, vx, gy);

        paintAmulet(fb, t, vx, gy);
        paintLines(fb, t, config.lines());
        if (t >= config.promptAt()) {
            font.drawCentred(fb, config.prompt(), 0, fb.width(), fb.height() - PROMPT_FROM_BOTTOM_PX, prompt);
        }
    }

    /**
     * §14.9: the next job. The clearing again; Vale standing where he stopped, Mr Jones with his
     * hands free — the amulet is in its museum — and the offer's lines typing out under them.
     */
    void paintOffer(Framebuffer fb, long t, ShellConfig.Offer offer) {
        paintBackdrop(fb);
        int gy = config.groundY();
        ShellConfig.Jones j = config.jones();
        jones.blit(fb, talking(offer.lines(), t) ? j.talk() : j.stand(), j.x(), gy);
        vale.blit(fb, config.vale().stand(), config.vale().toX(), gy);
        paintLines(fb, t, offer.lines());
        if (t >= offer.promptAt()) {
            font.drawCentred(fb, config.prompt(), 0, fb.width(), fb.height() - PROMPT_FROM_BOTTOM_PX, prompt);
        }
    }

    private void paintBackdrop(Framebuffer fb) {
        fb.clear(ground);
        for (int i = 0; i < backdrop.size(); i++) {
            ShellConfig.Placed p = config.backdrop().get(i);
            Sprite s = backdrop.get(i);
            s.blit(fb, p.frame(), p.x() + s.originX(), p.y() + s.originY());
        }
    }

    /** Mr Jones's mouth open at {@code t}: on every other beat while his line types. */
    private boolean talking(List<ShellConfig.Line> lines, long t) {
        return speaking(lines, ShellConfig.Line.JONES, t) && (t / config.jones().talkTicksPerFrame()) % 2 == 1;
    }

    /** The amulet, whole and at half size, centred where it is held — or on its way between them. */
    private void paintAmulet(Framebuffer fb, long t, int vx, int gy) {
        ShellConfig.Amulet a = config.amulet();
        int fromX = vx + a.carry().dx();
        int fromY = gy + a.carry().dy();
        int toX = config.jones().x() + a.hold().dx();
        int toY = gy + a.hold().dy();
        int cx = fromX;
        int cy = fromY;
        if (handedOver(t)) {
            cx = toX;
            cy = toY;
        } else if (t >= a.handOverAt()) {
            long k = t - a.handOverAt();
            cx = fromX + (int) ((toX - fromX) * k / a.handOverTicks());
            cy = fromY + (int) ((toY - fromY) * k / a.handOverTicks());
        }
        int half = amulet.w() / 2;
        int left = cx - half;
        int top = cy - half;
        for (int slot = 0; slot < framesBySlot.size(); slot++) {
            amulet.blitHalf(fb, framesBySlot.get(slot), left + (slot % 2) * half, top + (slot / 2) * half, -1);
        }
    }

    /** Each line types out a character every {@code ticksPerChar} from its own tick, row after row. */
    private void paintLines(Framebuffer fb, long t, List<ShellConfig.Line> lines) {
        for (int i = 0; i < lines.size(); i++) {
            ShellConfig.Line line = lines.get(i);
            int colour = lines == config.lines() ? lineColours[i] : palette.indexOf(line.colour());
            if (t < line.at()) {
                continue;
            }
            long shown = (t - line.at()) / config.ticksPerChar();
            int y = line.y();
            for (String row : line.text()) {
                if (shown <= 0) {
                    break;
                }
                String part = row.substring(0, (int) Math.min(row.length(), shown));
                // Centred on the whole row, so typing grows it left to right instead of from the middle.
                int left = (fb.width() - font.widthOf(row)) / 2;
                font.draw(fb, part, left, y, colour);
                shown -= row.length();
                y += font.lineHeight();
            }
        }
    }
}
