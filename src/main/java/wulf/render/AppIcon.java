package wulf.render;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import wulf.data.SpriteData;

/**
 * The game's icon (AGENTS.md §28.3): the whole amulet, its four quarters drawn from
 * their sprite JSON and put together, nearest-neighbour to any size. The window uses
 * it, and {@code wulf.tools.IconForge} turns it into the packaged apps' icon files at
 * build time — never into a file in the repository (§2.4).
 */
public final class AppIcon {

    /** The quarters' frames, in reading order: top-left, top-right, bottom-left, bottom-right. */
    private static final List<String> QUARTERS = List.of("nw", "ne", "sw", "se");

    /** The sizes handed to the window manager, which picks the nearest. */
    public static final List<Integer> WINDOW_SIZES = List.of(16, 32, 64, 128, 256);

    private AppIcon() {
    }

    /** The amulet {@code size} pixels square, on a transparent ground. */
    public static BufferedImage draw(SpriteData amulet, int[] argb, int size) {
        Map<String, byte[]> frames = amulet.decode(amulet.name());
        int qw = amulet.size().w();
        int qh = amulet.size().h();
        int w = qw * 2;
        int h = qh * 2;
        int side = Math.max(w, h);
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                // Centre the amulet on a square ground, then sample it.
                int sx = x * side / size - (side - w) / 2;
                int sy = y * side / size - (side - h) / 2;
                if (sx < 0 || sy < 0 || sx >= w || sy >= h) {
                    continue;
                }
                byte[] q = frames.get(QUARTERS.get((sy / qh) * 2 + sx / qw));
                byte v = q[(sy % qh) * qw + sx % qw];
                if (v >= 0) {
                    img.setRGB(x, y, argb[v]);
                }
            }
        }
        return img;
    }

    public static List<BufferedImage> windowImages(SpriteData amulet, int[] argb) {
        List<BufferedImage> images = new ArrayList<>();
        for (int s : WINDOW_SIZES) {
            images.add(draw(amulet, argb, s));
        }
        return images;
    }
}
