package wulf.ui;

import java.util.ArrayList;
import java.util.List;
import wulf.data.DataException;
import wulf.data.Palette;
import wulf.render.Framebuffer;
import wulf.render.FontSet;

/**
 * The {@code DATA_ERROR} state (AGENTS.md §17.1).
 *
 * <p>A typo in a JSON file must produce a readable screen naming the file, the
 * JSON pointer and the expected shape — never a stack trace. In a data-driven
 * game this is the highest-value debugging feature there is, which is why it
 * exists in M1 rather than later.
 */
public final class DataErrorScreen {

    private final FontSet font;
    private final int ground;
    private final int heading;
    private final int body;
    private final int accent;

    public DataErrorScreen(FontSet font, Palette palette) {
        this.font = font;
        this.ground = palette.indexOf("blue");
        this.heading = palette.indexOf("brightYellow");
        this.body = palette.indexOf("brightWhite");
        this.accent = palette.indexOf("brightCyan");
    }

    public void paint(Framebuffer fb, DataException error) {
        fb.clear(ground);
        int w = fb.width();
        int y = 24;

        font.drawCentred(fb, "DATA ERROR", 0, w, y, heading);
        y += font.lineHeight() * 2;

        font.draw(fb, "FILE", 8, y, accent);
        y += font.lineHeight();
        for (String line : wrap(shortenPath(error.file()), (w - 16) / font.advance())) {
            font.draw(fb, line, 8, y, body);
            y += font.lineHeight();
        }

        y += 4;
        font.draw(fb, "POINTER", 8, y, accent);
        y += font.lineHeight();
        for (String line : wrap(error.pointer(), (w - 16) / font.advance())) {
            font.draw(fb, line, 8, y, body);
            y += font.lineHeight();
        }

        y += 4;
        font.draw(fb, "PROBLEM", 8, y, accent);
        y += font.lineHeight();
        for (String line : wrap(error.detail(), (w - 16) / font.advance())) {
            font.draw(fb, line, 8, y, body);
            y += font.lineHeight();
        }

        font.drawCentred(fb, "FIX THE FILE AND RESTART", 0, w, fb.height() - 20, heading);
        font.drawCentred(fb, "PRESS ESCAPE TO QUIT", 0, w, fb.height() - 12, body);
    }

    /** Keeps the tail of a long path — the part that identifies the file. */
    static String shortenPath(String path) {
        int cut = path.indexOf("/data/");
        return cut >= 0 ? path.substring(cut + 1) : path;
    }

    /** Hard-wraps on word boundaries, then on characters for very long tokens. */
    static List<String> wrap(String text, int cols) {
        List<String> out = new ArrayList<>();
        if (cols < 1) {
            return List.of(text);
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            while (word.length() > cols) {
                if (line.length() > 0) {
                    out.add(line.toString());
                    line.setLength(0);
                }
                out.add(word.substring(0, cols));
                word = word.substring(cols);
            }
            if (line.length() + (line.length() == 0 ? 0 : 1) + word.length() > cols) {
                out.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }
}
