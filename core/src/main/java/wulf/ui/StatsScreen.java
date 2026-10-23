package wulf.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import wulf.data.ShellConfig;
import wulf.data.Stats;
import wulf.render.Framebuffer;

/**
 * The lifetime ledger (AGENTS.md §21.5). Local counters, shown because they are
 * the player's own; they go nowhere else, and there is no network code in this
 * project to send them with.
 */
public final class StatsScreen {

    private final Chrome chrome;
    private final ShellConfig.Stats config;

    public StatsScreen(Chrome chrome, ShellConfig.Stats config) {
        this.chrome = chrome;
        this.config = config;
    }

    public void paint(Framebuffer fb, Stats stats, int tickHz) {
        int y = chrome.page(fb, config.heading());
        if (stats.blank()) {
            chrome.small().drawCentred(fb, config.emptyLine(), 0, fb.width(), y, chrome.body());
            chrome.footer(fb, "PRESS FIRE");
            return;
        }
        int left = fb.width() / 2 - 92;
        int right = fb.width() / 2 + 92;
        for (String[] row : rows(stats, tickHz)) {
            chrome.small().draw(fb, row[0], left, y, chrome.body());
            chrome.small().draw(fb, row[1], right - chrome.small().widthOf(row[1]), y, chrome.bright());
            y += chrome.small().lineHeight() * 2;
        }
        chrome.footer(fb, "PRESS FIRE");
    }

    /** The ledger as label/value pairs, so the painter has no opinion about what a stat is. */
    static List<String[]> rows(Stats s, int tickHz) {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"GAMES PLAYED", Long.toString(s.gamesPlayed())});
        out.add(new String[]{"ESCAPES", Long.toString(s.escapes())});
        out.add(new String[]{"DEATHS", Long.toString(s.deaths())});
        out.add(new String[]{"ROOMS ENTERED", Long.toString(s.roomsVisited())});
        out.add(new String[]{"AMULET QUARTERS", Long.toString(s.amuletPieces())});
        out.add(new String[]{"THE WULF, MET", Long.toString(s.wulfEncounters())});
        out.add(new String[]{"THE WULF, LOST", Long.toString(s.wulfEvaded())});
        out.add(new String[]{"BEST SCORE", Long.toString(s.bestScore())});
        out.add(new String[]{"BEST ESCAPE", s.bestEscapeTicks() == 0 ? "-" : clock(s.bestEscapeTicks(), tickHz)});
        out.add(new String[]{"MOST KILLED", top(s.killsBySpecies())});
        out.add(new String[]{"KILLED BY MOST", top(s.deathsByCause())});
        out.add(new String[]{"FAVOURITE FLOWER", top(s.orchidsByColour())});
        return out;
    }

    /** Ties break by name, so the same ledger always reads the same way. */
    static String top(Map<String, Long> counts) {
        String best = null;
        long most = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            if (e.getValue() > most || (e.getValue() == most && best != null && e.getKey().compareTo(best) < 0)) {
                best = e.getKey();
                most = e.getValue();
            }
        }
        return best == null ? "-" : best.toUpperCase(java.util.Locale.ROOT) + " " + most;
    }

    static String clock(long ticks, int tickHz) {
        long seconds = ticks / Math.max(1, tickHz);
        return seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
    }
}
