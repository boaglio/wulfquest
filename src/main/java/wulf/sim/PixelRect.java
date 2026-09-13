package wulf.sim;

/** An axis-aligned rectangle in world pixels. */
public record PixelRect(int x, int y, int w, int h) {

    public static final PixelRect NONE = new PixelRect(0, 0, 0, 0);

    public boolean isEmpty() {
        return w <= 0 || h <= 0;
    }

    public boolean overlaps(PixelRect o) {
        return !isEmpty() && !o.isEmpty()
                && x < o.x + o.w && o.x < x + w
                && y < o.y + o.h && o.y < y + h;
    }
}
