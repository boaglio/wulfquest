package wulf.render;

import java.util.Map;
import wulf.data.SpriteData;

/** A decoded sprite: frames of palette indices, blitted with -1 as "skip" (AGENTS.md §5.3, §10.1). */
public final class Sprite {

    private final String name;
    private final int w;
    private final int h;
    private final int originX;
    private final int originY;
    private final Map<String, byte[]> frames;
    private final Map<String, Map<String, SpriteData.Point>> anchors;
    private final String firstFrame;

    public Sprite(SpriteData data, String source) {
        this.name = data.name();
        this.w = data.size().w();
        this.h = data.size().h();
        this.originX = data.origin().x();
        this.originY = data.origin().y();
        this.frames = Map.copyOf(data.decode(source));
        this.anchors = data.anchors();
        this.firstFrame = data.frames().get(0).id();
    }

    public String name() {
        return name;
    }

    public int w() {
        return w;
    }

    public int h() {
        return h;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    /** A named anchor on a frame, in sprite pixels. Missing anchors are a data error caught at load. */
    public SpriteData.Point anchor(String frameId, String anchorName) {
        Map<String, SpriteData.Point> onFrame = anchors.get(frameId);
        SpriteData.Point p = onFrame == null ? null : onFrame.get(anchorName);
        if (p == null) {
            throw new IllegalStateException("sprite '" + name + "' frame '" + frameId + "' has no '" + anchorName
                    + "' anchor");
        }
        return p;
    }

    /** Draws a frame so that the sprite's origin lands on (x, y). */
    public void blit(Framebuffer fb, String frameId, int x, int y) {
        byte[] px = frames.get(frameId);
        if (px == null) {
            throw new IllegalStateException("sprite '" + name + "' has no frame '" + frameId + "'");
        }
        int left = x - originX;
        int top = y - originY;
        for (int yy = 0; yy < h; yy++) {
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                byte v = px[row + xx];
                if (v >= 0) {
                    fb.set(left + xx, top + yy, v);
                }
            }
        }
    }

    /** Draws a frame's silhouette in one colour: a hit flash. */
    public void blitTinted(Framebuffer fb, String frameId, int x, int y, int paletteIndex) {
        byte[] px = frames.get(frameId);
        if (px == null) {
            throw new IllegalStateException("sprite '" + name + "' has no frame '" + frameId + "'");
        }
        int left = x - originX;
        int top = y - originY;
        for (int yy = 0; yy < h; yy++) {
            int row = yy * w;
            for (int xx = 0; xx < w; xx++) {
                if (px[row + xx] >= 0) {
                    fb.set(left + xx, top + yy, paletteIndex);
                }
            }
        }
    }

    public void blit(Framebuffer fb, int x, int y) {
        blit(fb, firstFrame, x, y);
    }
}
