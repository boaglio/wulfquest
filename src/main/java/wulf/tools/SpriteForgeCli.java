package wulf.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import wulf.Content;
import wulf.data.DataException;
import wulf.data.SpriteData;
import wulf.world.SceneryCatalog;

/**
 * The art review loop (AGENTS.md §10.3): previews sprites in the terminal
 * with 24-bit ANSI colour, so art can be judged without launching the game.
 *
 * <p>There is deliberately no PNG exporter: it would tempt binaries back
 * into the repository (§2.4).
 *
 * <pre>
 *   validate          load and cross-check every sprite and scenery object
 *   preview NAME|ID   one sprite, by sprite name or 4-digit scenery id
 *   sheet [COLUMNS]   every scenery object on one contact sheet
 *   mask NAME|ID      a scenery object's art above its collision cells
 * </pre>
 */
public final class SpriteForgeCli {

    /** Control Sequence Introducer: ESC then '['. Built from the code point so no raw control char sits in source. */
    private static final String CSI = (char) 27 + "[";
    private static final String RESET = CSI + "0m";

    private SpriteForgeCli() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(64);
        }
        Content content;
        try {
            content = Content.load(Path.of("data"));
        } catch (DataException e) {
            System.err.println("DATA ERROR\n  file    : " + e.file() + "\n  pointer : " + e.pointer()
                    + "\n  problem : " + e.detail());
            System.exit(2);
            return;
        }
        int[] argb = content.palette().toArgb();
        switch (args[0]) {
            case "validate" -> validate(content);
            case "preview" -> print(art(content, argb, spriteName(content, need(args))));
            case "sheet" -> sheet(content, argb, args.length > 1 ? Integer.parseInt(args[1]) : 150);
            case "mask" -> mask(content, argb, need(args));
            default -> {
                usage();
                System.exit(64);
            }
        }
    }

    private static void usage() {
        System.err.println("usage: SpriteForgeCli validate | preview NAME|ID | sheet [COLUMNS] | mask NAME|ID");
    }

    private static String need(String[] args) {
        if (args.length < 2) {
            usage();
            System.exit(64);
        }
        return args[1];
    }

    private static String spriteName(Content c, String nameOrId) {
        String upper = nameOrId.toUpperCase(Locale.ROOT);
        return c.scenery().has(upper) ? c.scenery().piece(upper).sprite() : nameOrId;
    }

    private static void validate(Content c) {
        System.out.println("sprites          : " + c.sprites().all().size() + " loaded through the index");
        System.out.println("scenery objects  : " + c.scenery().ids().size());
        System.out.println("map references   : every graphic resolves; every placement fits 32x24");
        System.out.println("room masks       : " + c.map().templateCount() + " templates baked");
        System.out.println();
        System.out.println("id    cells  solid/total  collision");
        for (String id : c.scenery().ids()) {
            SceneryCatalog.Piece p = c.scenery().piece(id);
            System.out.printf("%s  %2dx%-2d  %4d/%-5d  %s%n", id, p.w(), p.h(), p.solidCells(), p.w() * p.h(),
                    p.derived() ? "derived from art" : "authored override");
        }
    }

    /** Half-block rendering: one terminal cell shows two pixels, top as foreground, bottom as background. */
    static List<String> art(Content c, int[] argb, String spriteName) {
        SpriteData data = c.sprites().get(spriteName);
        byte[] px = data.decode("data/art/sprites/" + spriteName + ".sprite.json").get(data.frames().get(0).id());
        int w = data.size().w();
        int h = data.size().h();
        List<String> lines = new ArrayList<>();
        for (int y = 0; y < h; y += 2) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < w; x++) {
                int top = px[y * w + x];
                int bottom = y + 1 < h ? px[(y + 1) * w + x] : -1;
                sb.append(cell(argb, top, bottom));
            }
            lines.add(sb.append(RESET).toString());
        }
        return lines;
    }

    private static String cell(int[] argb, int top, int bottom) {
        if (top < 0 && bottom < 0) {
            return RESET + " ";
        }
        if (top >= 0 && bottom >= 0) {
            return RESET + fg(argb[top]) + bg(argb[bottom]) + "▀";
        }
        return RESET + fg(argb[top >= 0 ? top : bottom]) + (top >= 0 ? "▀" : "▄");
    }

    private static String fg(int argb) {
        return CSI + "38;2;" + ((argb >> 16) & 0xFF) + ";" + ((argb >> 8) & 0xFF) + ";" + (argb & 0xFF) + "m";
    }

    private static String bg(int argb) {
        return CSI + "48;2;" + ((argb >> 16) & 0xFF) + ";" + ((argb >> 8) & 0xFF) + ";" + (argb & 0xFF) + "m";
    }

    private static void print(List<String> lines) {
        for (String line : lines) {
            System.out.println(line);
        }
    }

    private static void sheet(Content c, int[] argb, int columns) {
        List<String> ids = new ArrayList<>(c.scenery().ids());
        int i = 0;
        while (i < ids.size()) {
            List<String> row = new ArrayList<>();
            int used = 0;
            while (i < ids.size()) {
                int w = c.scenery().piece(ids.get(i)).w() * SceneryCatalog.CELL;
                if (!row.isEmpty() && used + w + 2 > columns) {
                    break;
                }
                row.add(ids.get(i++));
                used += w + 2;
            }
            printRow(c, argb, row);
        }
    }

    private static void printRow(Content c, int[] argb, List<String> ids) {
        List<List<String>> arts = new ArrayList<>();
        int height = 0;
        for (String id : ids) {
            List<String> a = art(c, argb, c.scenery().piece(id).sprite());
            arts.add(a);
            height = Math.max(height, a.size());
        }
        for (int line = 0; line < height; line++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < ids.size(); k++) {
                int w = c.scenery().piece(ids.get(k)).w() * SceneryCatalog.CELL;
                List<String> a = arts.get(k);
                int offset = height - a.size();   // bottom-align: everything stands on the ground
                sb.append(line >= offset ? a.get(line - offset) : " ".repeat(w)).append("  ");
            }
            System.out.println(sb);
        }
        StringBuilder labels = new StringBuilder();
        for (String id : ids) {
            int w = c.scenery().piece(id).w() * SceneryCatalog.CELL;
            labels.append(String.format("%-" + w + "s  ", id.length() > w ? id.substring(0, w) : id));
        }
        System.out.println(labels);
        System.out.println();
    }

    private static void mask(Content c, int[] argb, String nameOrId) {
        String id = nameOrId.toUpperCase(Locale.ROOT);
        if (!c.scenery().has(id)) {
            System.err.println("'" + nameOrId + "' is not a scenery object id");
            System.exit(64);
        }
        SceneryCatalog.Piece p = c.scenery().piece(id);
        print(art(c, argb, p.sprite()));
        System.out.println();
        System.out.printf("%s — %dx%d cells, %d solid (%s)%n", id, p.w(), p.h(), p.solidCells(),
                p.derived() ? "derived from art" : "authored override");
        for (int cy = 0; cy < p.h(); cy++) {
            StringBuilder sb = new StringBuilder("  ");
            for (int cx = 0; cx < p.w(); cx++) {
                sb.append(p.solidAt(cx, cy) ? "##" : "..");
            }
            System.out.println(sb);
        }
    }
}
