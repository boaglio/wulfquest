package wulf.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import wulf.data.DisplayConfig;
import wulf.data.QuickSaveData;
import wulf.data.Settings;
import wulf.data.ShellConfig;
import wulf.render.FontSet;
import wulf.render.Framebuffer;
import wulf.render.Sprite;

/**
 * The menus (AGENTS.md §17.4, §17.9, §21.4, {@code [NEW]}): the pause menu, the save and load
 * slots and the options. Over a game they are a box on the jungle, which stands still and
 * dimmed under them; from the title, pages of their own. The line chosen is bright, with a
 * mark before it.
 */
final class MenuScreen {

    /** Layout, not game numbers: the box's margin round its lines, and the marker's gap before a line. */
    private static final int PAD_PX = 8;
    private static final int MARKER_GAP_PX = 5;

    private final Chrome chrome;
    private final DisplayConfig.Playfield field;
    private final ShellConfig config;
    private final Sprite marker;

    MenuScreen(Chrome chrome, DisplayConfig display, ShellConfig config, Sprite marker) {
        this.marker = marker;
        this.chrome = chrome;
        this.field = display.playfield();
        this.config = config;
    }

    /** One line of a menu: in the big font, or the small one under the line before it. */
    private record Row(String text, boolean big, boolean chosen) {
    }

    void paint(Framebuffer fb, Shell shell, Settings settings) {
        List<Row> rows = new ArrayList<>();
        String heading;
        String footer;
        switch (shell.state()) {
            case PAUSED -> {
                heading = config.pause().heading();
                footer = config.pause().footer();
                List<ShellConfig.Entry> items = config.pause().items();
                for (int i = 0; i < items.size(); i++) {
                    rows.add(new Row(items.get(i).label(), true, i == shell.cursor()));
                }
            }
            case OPTIONS -> {
                ShellConfig.Options o = config.options();
                List<ShellConfig.Entry> items = shell.optionLines();
                heading = o.heading();
                footer = items.get(shell.cursor()).id().equals("volume") ? o.volumeFooter() : o.footer();
                for (int i = 0; i < items.size(); i++) {
                    ShellConfig.Entry e = items.get(i);
                    String text = switch (e.id()) {
                        case "back", "keys", "load", "hiScores", "stats" -> e.label();
                        case "difficulty" -> e.label() + "  "
                                + (shell.easy() ? config.difficulty().easy() : config.difficulty().hard());
                        case "volume" -> e.label() + "  " + settings.audio().volumePercent() + "%";
                        case "eightBit" -> e.label() + "  " + (Shell.eightBitOn(settings, o.eightBit()) ? o.on() : o.off());
                        default -> e.label() + "  " + (Shell.optionOn(settings, e.id()) ? o.on() : o.off());
                    };
                    rows.add(new Row(text, true, i == shell.cursor()));
                }
            }
            default -> {
                ShellConfig.Saves s = config.saves();
                heading = switch (shell.state()) {
                    case SAVE_GAME -> s.saveHeading();
                    case NEW_GAME -> s.newGameHeading();   // §21.4: a new game's slot
                    default -> s.loadHeading();
                };
                footer = s.footer();
                for (int slot = 1; slot <= s.slots(); slot++) {
                    int n = slot;
                    Optional<QuickSaveData> in = shell.saveSlots() == null ? Optional.empty() : shell.saveSlots().peek(n);
                    boolean chosen = n - 1 == shell.cursor();
                    rows.add(new Row(in.map(d -> slotLine(s, n, d)).orElse(s.empty().replace("{n}", Integer.toString(n))),
                            true, chosen));
                    rows.add(new Row(in.map(d -> detailLine(s, d)).orElse(""), false, chosen));
                }
            }
        }
        if (shell.menuInGame()) {
            paintBox(fb, heading, rows, shell.menuNote(), footer);
        } else {
            paintPage(fb, heading, rows, shell.menuNote(), footer);
        }
    }

    static String slotLine(ShellConfig.Saves s, int n, QuickSaveData d) {
        int job = d.newGamePlus();
        String jobName = job < s.jobNames().size() ? s.jobNames().get(job) : Integer.toString(job);
        return s.slot().replace("{n}", Integer.toString(n))
                .replace("{mode}", d.easy() ? s.modeNames().easy() : s.modeNames().hard())
                .replace("{job}", jobName)
                .replace("{quarters}", Integer.toString(d.quarters()))
                .replace("{score}", Long.toString(d.score()))
                .replaceAll(" {3,}", "  ");   // a job with no name leaves no hole
    }

    static String detailLine(ShellConfig.Saves s, QuickSaveData d) {
        return s.detail().replace("{date}", d.date() == null ? "" : d.date())
                .replace("{room}", d.room() == null ? "" : d.room()).trim();
    }

    /** Over the game: the jungle dimmed, and the menu in a box in the middle of it. */
    private void paintBox(Framebuffer fb, String heading, List<Row> rows, String note, String footer) {
        fb.dim(field.x(), field.y(), field.w(), field.h());
        int big = chrome.big().lineHeight();
        int small = chrome.small().lineHeight();
        int h = 2 * big;
        for (Row r : rows) {
            h += r.big() ? big + 2 : small + 2;
        }
        h += big + small + 2 * PAD_PX;
        int top = field.y() + Math.max(0, (field.h() - h) / 2);
        fb.fillRect(field.x() + PAD_PX, top, field.w() - 2 * PAD_PX, h, chrome.ground());
        fb.drawRect(field.x() + PAD_PX, top, field.w() - 2 * PAD_PX, h, chrome.dim());
        int y = top + PAD_PX;
        chrome.big().drawCentred(fb, heading, field.x(), field.w(), y, chrome.heading());
        y += 2 * big;
        y = paintRows(fb, rows, field.x(), field.w(), y);
        if (note != null) {
            chrome.small().drawCentred(fb, note, field.x(), field.w(), y + 2, chrome.heading());
        }
        chrome.small().drawCentred(fb, footer, field.x(), field.w(), top + h - PAD_PX - small, chrome.dim());
    }

    /** From the title: a page of its own. */
    private void paintPage(Framebuffer fb, String heading, List<Row> rows, String note, String footer) {
        int y = chrome.page(fb, heading) + chrome.big().lineHeight();
        y = paintRows(fb, rows, 0, fb.width(), y);
        if (note != null) {
            chrome.small().drawCentred(fb, note, 0, fb.width(), y + chrome.big().lineHeight(), chrome.heading());
        }
        chrome.footer(fb, footer);
    }

    /**
     * One line of a menu, centred, and before it the marker (§17.2) when it is the line chosen;
     * the line stays where it was, so choosing it does not shift it.
     */
    static void drawLine(Framebuffer fb, FontSet font, Sprite marker, String frame, String text, int x, int w,
                         int y, int colour) {
        font.drawCentred(fb, text, x, w, y, colour);
        if (marker != null) {
            int left = x + (w - font.widthOf(text)) / 2 - MARKER_GAP_PX - marker.w();
            int bottom = y + (font.glyphH() + marker.h()) / 2;   // their middles level
            marker.blit(fb, frame, left + marker.originX(), bottom - marker.h() + marker.originY());
        }
    }

    private int paintRows(Framebuffer fb, List<Row> rows, int x, int w, int y) {
        for (Row r : rows) {
            if (r.big()) {
                drawLine(fb, chrome.big(), r.chosen() ? marker : null, config.title().marker().frame(), r.text(),
                        x, w, y, r.chosen() ? chrome.bright() : chrome.body());
                y += chrome.big().lineHeight() + 2;
            } else {
                chrome.small().drawCentred(fb, r.text(), x, w, y, chrome.dim());
                y += chrome.small().lineHeight() + 2;
            }
        }
        return y;
    }
}
